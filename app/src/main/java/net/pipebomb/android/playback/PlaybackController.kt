
package net.pipebomb.android.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.model.TrackIdDto
import net.pipebomb.android.core.datastore.PlaybackQueueStore
import net.pipebomb.android.core.datastore.PlaybackSnapshot
import net.pipebomb.android.data.TrackMetadataRepository
import net.pipebomb.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode { Off, All, One }

data class PlaybackUiState(
    val queue: List<TrackKey> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val error: String? = null,
) {
    val currentKey: TrackKey? get() = queue.getOrNull(currentIndex)
}

@Singleton
@OptIn(UnstableApi::class, FlowPreview::class)
class PlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val resolver: PlaybackResolver,
    private val cache: PlaybackCache,
    private val metadata: TrackMetadataRepository,
    private val queueStore: PlaybackQueueStore,
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private var originalQueue: List<TrackKey> = emptyList()
    private var retriedFor: String? = null
    private var bufferingSinceMs = 0L
    private var playRequestId = 0L
    private var externalQueueMode = false
    private var externalTracks: Map<TrackKey, Track> = emptyMap()
    private var positionTicker: Job? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                LazyTrackMediaSourceFactory(
                    resolver = resolver,
                    childFactory = DefaultMediaSourceFactory(cache.dataSourceFactory)
                        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5)),
                    scope = scope,
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply { setWakeMode(C.WAKE_MODE_NETWORK) }
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (!externalQueueMode) return
                        val index = currentMediaItemIndex
                        val key = _state.value.queue.getOrNull(index) ?: return
                        _state.update { it.copy(currentIndex = index) }
                        externalTracks[key]?.let { _currentTrack.value = it }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            retriedFor = null
                            bufferingSinceMs = 0
                        }
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                if (bufferingSinceMs == 0L) {
                                    bufferingSinceMs = SystemClock.elapsedRealtime()
                                }
                                _state.update { it.copy(isBuffering = true) }
                            }

                            Player.STATE_READY -> {
                                _state.update {
                                    it.copy(
                                        isBuffering = false,
                                        durationMs = duration.coerceAtLeast(0),
                                    )
                                }
                            }

                            Player.STATE_ENDED -> advanceOnEnd()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        retryOrSkip(error)
                    }
                })
            }
    }

    private var restored = false

    init {
        scope.launch {
            val snapshot = queueStore.snapshot.first()
            if (snapshot != null && snapshot.queue.isNotEmpty()) {
                val queue = snapshot.queue.map { TrackKey(it.pluginId, it.libraryId, it.trackId) }
                val index = snapshot.currentIndex.coerceIn(queue.indices)
                originalQueue = snapshot.originalQueue
                    .map { TrackKey(it.pluginId, it.libraryId, it.trackId) }
                    .takeIf { it.size == queue.size }
                    ?: queue
                _state.value = PlaybackUiState(
                    queue = queue,
                    currentIndex = index,
                    positionMs = snapshot.positionMs.coerceAtLeast(0),
                    shuffle = snapshot.shuffle,
                    repeat = runCatching { RepeatMode.valueOf(snapshot.repeat) }
                        .getOrDefault(RepeatMode.Off),
                )
                queue.getOrNull(index)?.let { key ->
                    _currentTrack.value = resolveTrack(key) ?: Track(
                        trackId = key.trackId,
                        pluginId = key.pluginId,
                        libraryId = key.libraryId,
                        title = key.trackId,
                    )
                }
            }
            restored = true
        }
        scope.launch {
            _state.debounce(5_000).collect { state ->
                if (!restored) return@collect
                if (state.queue.isEmpty()) {
                    queueStore.clear()
                } else {
                    queueStore.save(
                        PlaybackSnapshot(
                            queue = state.queue.map {
                                TrackIdDto(it.pluginId, it.libraryId, it.trackId)
                            },
                            originalQueue = originalQueue.map {
                                TrackIdDto(it.pluginId, it.libraryId, it.trackId)
                            },
                            currentIndex = state.currentIndex,
                            positionMs = state.positionMs,
                            shuffle = state.shuffle,
                            repeat = state.repeat.name,
                        ),
                    )
                }
            }
        }
        scope.launch {
            _state
                .map { it.isPlaying || it.isBuffering }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) startPositionTicker() else stopPositionTicker()
                }
        }
    }

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (true) {
                delay(500)
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0),
                            durationMs = player.duration.coerceAtLeast(0),
                        )
                    }
                    checkForStall()
                }
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    fun play(keys: List<TrackKey>, startIndex: Int = 0) {
        if (keys.isEmpty()) return
        retriedFor = null
        bufferingSinceMs = 0
        externalQueueMode = false
        externalTracks = emptyMap()
        originalQueue = keys
        _state.update {
            it.copy(
                queue = keys,
                currentIndex = startIndex.coerceIn(keys.indices),
                shuffle = false,
                error = null,
            )
        }
        playCurrent()
    }

    fun playNow(key: TrackKey) = play(listOf(key), 0)

    fun playIndex(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        if (externalQueueMode && player.mediaItemCount > 1) {
            player.seekTo(index, 0)
            return
        }
        retriedFor = null
        _state.update { it.copy(currentIndex = index, error = null) }
        playCurrent()
    }

    fun prepareExternalQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        retriedFor = null
        bufferingSinceMs = 0
        externalQueueMode = true
        externalTracks = tracks.associateBy { it.key }
        val keys = tracks.map { it.key }
        originalQueue = keys
        val index = startIndex.coerceIn(keys.indices)
        _state.update {
            it.copy(
                queue = keys,
                currentIndex = index,
                shuffle = false,
                positionMs = 0,
                durationMs = 0,
                error = null,
            )
        }
        _currentTrack.value = tracks.getOrNull(index)
        applyRepeatMode()
    }

    fun addToQueue(keys: List<TrackKey>) {
        if (keys.isEmpty()) return
        val wasEmpty = _state.value.queue.isEmpty()
        _state.update { it.copy(queue = it.queue + keys) }
        originalQueue = originalQueue + keys
        if (wasEmpty) {
            playCurrent()
        }
    }

    fun playNext(keys: List<TrackKey>) {
        if (keys.isEmpty()) return
        val currentIndex = _state.value.currentIndex
        _state.update { current ->
            val queue = current.queue.toMutableList()
            queue.addAll((current.currentIndex + 1).coerceAtMost(queue.size), keys)
            current.copy(queue = queue)
        }
        val original = originalQueue.toMutableList()
        original.addAll((currentIndex + 1).coerceAtMost(original.size), keys)
        originalQueue = original
    }

    fun playNext(key: TrackKey) = playNext(listOf(key))

    fun toggle() {
        if (player.mediaItemCount == 0 && _state.value.queue.isNotEmpty()) {
            playCurrent(_state.value.positionMs)
            return
        }
        if (player.isPlaying) {
            player.pause()
            return
        }
        resetRetry()
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.play()
    }

    fun next() {
        resetRetry()
        if (externalQueueMode && player.mediaItemCount > 1) {
            player.seekToNextMediaItem()
            return
        }
        val queue = _state.value.queue
        if (queue.isEmpty()) return
        val nextIndex = _state.value.currentIndex + 1
        if (nextIndex < queue.size) {
            _state.update { it.copy(currentIndex = nextIndex) }
            playCurrent()
        } else if (_state.value.repeat == RepeatMode.All) {
            _state.update { it.copy(currentIndex = 0) }
            playCurrent()
        }
    }

    fun previous() {
        resetRetry()
        if (externalQueueMode && player.mediaItemCount > 1) {
            player.seekToPreviousMediaItem()
            return
        }
        if (player.currentPosition > 3_000) {
            player.seekTo(0)
            return
        }
        val index = _state.value.currentIndex
        if (index > 0) {
            _state.update { it.copy(currentIndex = index - 1) }
            playCurrent()
        } else {
            player.seekTo(0)
        }
    }

    fun previousTrack() {
        resetRetry()
        val index = _state.value.currentIndex
        if (index > 0) {
            _state.update { it.copy(currentIndex = index - 1) }
            playCurrent()
        } else {
            player.seekTo(0)
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    fun cycleRepeat() {
        _state.update {
            it.copy(
                repeat = when (it.repeat) {
                    RepeatMode.Off -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.Off
                },
            )
        }
        applyRepeatMode()
    }

    private fun applyRepeatMode() {
        player.repeatMode = if (externalQueueMode) {
            when (_state.value.repeat) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            }
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val current = _state.value
        if (current.shuffle) {
            val currentKey = current.currentKey
            val restored = if (originalQueue.isNotEmpty()) originalQueue else current.queue
            _state.update {
                it.copy(
                    shuffle = false,
                    queue = restored,
                    currentIndex = restored.indexOf(currentKey).takeIf { idx -> idx >= 0 }
                        ?: it.currentIndex,
                )
            }
        } else {
            originalQueue = current.queue
            val before = current.queue.take(current.currentIndex + 1)
            val after = current.queue.drop(current.currentIndex + 1).shuffled()
            _state.update { it.copy(shuffle = true, queue = before + after) }
        }
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        val removedKey = current.queue[index]
        val newQueue = current.queue.toMutableList().apply { removeAt(index) }
        if (newQueue.isEmpty()) {
            clearQueue()
            return
        }
        if (index == current.currentIndex) {
            play(newQueue, index.coerceAtMost(newQueue.lastIndex))
        } else {
            removeFromOriginalQueue(removedKey)
            _state.update {
                it.copy(
                    queue = newQueue,
                    currentIndex = if (index < it.currentIndex) it.currentIndex - 1 else it.currentIndex,
                )
            }
        }
    }

    private fun removeFromOriginalQueue(key: TrackKey) {
        originalQueue = originalQueue.toMutableList().apply {
            val idx = indexOfFirst { it == key }
            if (idx >= 0) removeAt(idx)
        }
    }

    fun updateCurrentTrackSaved(saved: Boolean) {
        _currentTrack.value = _currentTrack.value?.copy(bookmarked = saved)
    }

    fun clearQueue() {
        externalQueueMode = false
        externalTracks = emptyMap()
        player.stop()
        player.clearMediaItems()
        _currentTrack.value = null
        originalQueue = emptyList()
        _state.value = PlaybackUiState()
    }

    private fun playCurrent(startPositionMs: Long = 0) {
        val key = _state.value.currentKey ?: return
        val requestId = ++playRequestId
        bufferingSinceMs = 0
        externalQueueMode = false
        externalTracks = emptyMap()
        scope.launch {
            _state.update { it.copy(isBuffering = true, error = null) }
            withContext(Dispatchers.Main) { player.pause() }
            val track = resolveTrack(key) ?: Track(
                trackId = key.trackId,
                pluginId = key.pluginId,
                libraryId = key.libraryId,
                title = key.trackId,
            )
            if (requestId != playRequestId) return@launch
            _currentTrack.value = track
            val item = TrackMediaItemFactory.create(track)
            withContext(Dispatchers.Main) {
                if (requestId != playRequestId) return@withContext
                context.startService(Intent(context, PlaybackService::class.java))
                player.setMediaItem(item)
                player.prepare()
                player.repeatMode = Player.REPEAT_MODE_OFF
                if (startPositionMs > 0) player.seekTo(startPositionMs)
                player.play()
                _state.update {
                    it.copy(
                        isBuffering = true,
                        positionMs = startPositionMs.coerceAtLeast(0),
                        durationMs = 0,
                    )
                }
            }
        }
    }

    private suspend fun resolveTrack(key: TrackKey): Track? = metadata.resolve(key)

    private fun advanceOnEnd() {
        val state = _state.value
        when (state.repeat) {
            RepeatMode.One -> {
                player.seekTo(0)
                player.play()
            }

            RepeatMode.All -> {
                val next = (state.currentIndex + 1) % state.queue.size.coerceAtLeast(1)
                _state.update { it.copy(currentIndex = next) }
                playCurrent()
            }

            RepeatMode.Off -> {
                if (state.currentIndex < state.queue.lastIndex) {
                    _state.update { it.copy(currentIndex = state.currentIndex + 1) }
                    playCurrent()
                } else {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    private fun resetRetry() {
        retriedFor = null
        bufferingSinceMs = 0
    }

    private fun checkForStall() {
        val key = _state.value.currentKey ?: return
        if (player.playbackState != Player.STATE_BUFFERING || !player.playWhenReady) {
            bufferingSinceMs = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (bufferingSinceMs == 0L) {
            bufferingSinceMs = now
            return
        }
        if (now - bufferingSinceMs < STALL_TIMEOUT_MS) return
        if (retriedFor != key.toString()) {
            retryCurrent(key, player.currentPosition.coerceAtLeast(0))
            return
        }
        bufferingSinceMs = 0
        _state.update { it.copy(isBuffering = false, error = "Playback stalled") }
        player.pause()
    }

    private fun retryOrSkip(error: PlaybackException) {
        val key = _state.value.currentKey ?: return
        val positionMs = player.currentPosition.coerceAtLeast(0)
        if (retriedFor != key.toString()) {
            retryCurrent(key, positionMs)
            return
        }
        _state.update { it.copy(isBuffering = false, error = "Playback failed") }
        player.pause()
    }

    private fun retryCurrent(key: TrackKey, positionMs: Long) {
        retriedFor = key.toString()
        bufferingSinceMs = 0
        resolver.invalidate(key)
        if (externalQueueMode && player.mediaItemCount > 0) {
            val index = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
            val track = externalTracks[key] ?: Track(
                trackId = key.trackId,
                pluginId = key.pluginId,
                libraryId = key.libraryId,
                title = key.trackId,
            )
            player.replaceMediaItem(index, TrackMediaItemFactory.create(track))
            player.prepare()
            player.seekTo(positionMs)
            player.play()
        } else {
            playCurrent(positionMs)
        }
    }

    private companion object {
        const val STALL_TIMEOUT_MS = 20_000L
    }
}
