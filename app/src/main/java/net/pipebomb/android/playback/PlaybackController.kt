
package net.pipebomb.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.model.TrackIdDto
import net.pipebomb.android.core.datastore.PlaybackQueueStore
import net.pipebomb.android.core.datastore.PlaybackSnapshot
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.data.CatalogRepository
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
    private val catalogRepository: CatalogRepository,
    private val queueStore: PlaybackQueueStore,
) {
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private var originalQueue: List<TrackKey> = emptyList()
    private var retriedFor: String? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cache.dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
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
                _state.value = PlaybackUiState(
                    queue = snapshot.queue.map { TrackKey(it.pluginId, it.libraryId, it.trackId) },
                    currentIndex = snapshot.currentIndex.coerceAtLeast(0),
                    positionMs = snapshot.positionMs.coerceAtLeast(0),
                    shuffle = snapshot.shuffle,
                    repeat = runCatching { RepeatMode.valueOf(snapshot.repeat) }
                        .getOrDefault(RepeatMode.Off),
                )
            }
            restored = true
        }
        scope.launch {
            _state.debounce(1_500).collect { state ->
                if (!restored) return@collect
                if (state.queue.isEmpty()) {
                    queueStore.clear()
                } else {
                    queueStore.save(
                        PlaybackSnapshot(
                            queue = state.queue.map {
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
            while (true) {
                delay(500)
                val current = _state.value
                if (current.isPlaying || current.isBuffering) {
                    withContext(Dispatchers.Main) {
                        _state.update {
                            it.copy(
                                positionMs = player.currentPosition.coerceAtLeast(0),
                                durationMs = player.duration.coerceAtLeast(0),
                            )
                        }
                    }
                }
            }
        }
    }

    fun play(keys: List<TrackKey>, startIndex: Int = 0) {
        if (keys.isEmpty()) return
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

    fun addToQueue(keys: List<TrackKey>) {
        if (keys.isEmpty()) return
        _state.update { it.copy(queue = it.queue + keys) }
        if (_state.value.queue.size == keys.size) {
            playCurrent()
        }
    }

    fun playNext(key: TrackKey) {
        _state.update { current ->
            val queue = current.queue.toMutableList()
            queue.add((current.currentIndex + 1).coerceAtMost(queue.size), key)
            current.copy(queue = queue)
        }
    }

    fun toggle() {
        if (player.mediaItemCount == 0 && _state.value.queue.isNotEmpty()) {
            playCurrent(_state.value.positionMs)
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
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
    }

    fun toggleShuffle() {
        _state.update { current ->
            if (current.shuffle) {
                val currentKey = current.currentKey
                val restored = if (originalQueue.isNotEmpty()) originalQueue else current.queue
                current.copy(
                    shuffle = false,
                    queue = restored,
                    currentIndex = restored.indexOf(currentKey).takeIf { it >= 0 }
                        ?: current.currentIndex,
                )
            } else {
                originalQueue = current.queue
                val before = current.queue.take(current.currentIndex + 1)
                val after = current.queue.drop(current.currentIndex + 1).shuffled()
                current.copy(shuffle = true, queue = before + after)
            }
        }
    }

    fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        _currentTrack.value = null
        originalQueue = emptyList()
        _state.value = PlaybackUiState()
    }

    private fun playCurrent(startPositionMs: Long = 0) {
        val key = _state.value.currentKey ?: return
        scope.launch {
            _state.update { it.copy(isBuffering = true, error = null) }
            val resolved = when (val result = resolver.resolve(key)) {
                is ApiResult.Failure -> {
                    _state.update { it.copy(isBuffering = false, error = "Unable to play track") }
                    return@launch
                }

                is ApiResult.Success -> result.value
            }
            val fetchedTrack = (catalogRepository.tracks(listOf(key)) as? ApiResult.Success)?.value?.firstOrNull()
            val track = fetchedTrack ?: Track(
                trackId = key.trackId,
                pluginId = key.pluginId,
                libraryId = key.libraryId,
                title = key.trackId,
            )
            _currentTrack.value = track
            val item = buildMediaItem(key, resolved, track)
            withContext(Dispatchers.Main) {
                context.startService(Intent(context, PlaybackService::class.java))
                player.setMediaItem(item)
                player.prepare()
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

    private fun buildMediaItem(
        key: TrackKey,
        media: ResolvedMedia,
        track: Track?,
    ): MediaItem {
        val metadata = MediaMetadata.Builder().apply {
            track?.let { t ->
                setTitle(AttributeReader.string(t.attributes, "title") ?: t.title)
                val artists = t.artists
                    ?.mapNotNull { artist ->
                        AttributeReader.string(artist.artist.attributes, "name")
                    }
                    ?.takeIf { it.isNotEmpty() }
                    ?: AttributeReader.strings(t.attributes, "artist", multiple = true)
                artists?.joinToString(", ")?.let { setArtist(it) }
                AttributeReader.resource(t.attributes, "front")?.url?.let {
                    setArtworkUri(android.net.Uri.parse(it))
                }
            }
        }.build()
        return MediaItem.Builder()
            .setUri(media.url)
            .setMediaId(key.toString())
            .setCustomCacheKey(key.toString())
            .setMediaMetadata(metadata)
            .apply {
                if (media.type == ResolvedMedia.MediaType.Hls) {
                    setMimeType(MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()
    }

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

    private fun retryOrSkip(error: PlaybackException) {
        val key = _state.value.currentKey ?: return
        if (retriedFor != key.toString()) {
            retriedFor = key.toString()
            resolver.invalidate(key)
            playCurrent()
            return
        }
        _state.update { it.copy(isBuffering = false, error = "Playback failed") }
        next()
    }
}
