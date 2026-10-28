package net.pipebomb.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.pipebomb.android.MainActivity
import net.pipebomb.android.playback.auto.AutoLibrarySessionCallback
import javax.inject.Inject

private data class MediaButtonState(
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val hasTrack: Boolean,
    val saved: Boolean,
)

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    @Inject
    lateinit var controller: PlaybackController

    @Inject
    lateinit var autoLibraryCallback: AutoLibrarySessionCallback

    @Inject
    lateinit var actions: PlaybackSessionActions

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val layoutScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var layoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this))

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val session = MediaLibrarySession.Builder(
            this,
            controller.player,
            autoLibraryCallback,
        )
            .setSessionActivity(sessionActivity)
            .build()
            .also {
                mediaLibrarySession = it
                addSession(it)
            }

        layoutJob = layoutScope.launch {
            combine(
                controller.state.map { it.shuffle to it.repeat },
                controller.currentTrack,
                actions.saved,
            ) { playback, track, saved ->
                MediaButtonState(
                    shuffle = playback.first,
                    repeat = playback.second,
                    hasTrack = track != null,
                    saved = saved,
                )
            }
                .distinctUntilChanged()
                .collect { state ->
                    session.setMediaButtonPreferences(mediaButtonPreferences(state))
                }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!controller.player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        layoutJob?.cancel()
        layoutScope.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        super.onDestroy()
    }

    private fun mediaButtonPreferences(state: MediaButtonState): List<CommandButton> = listOf(
        CommandButton.Builder(
            if (state.shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
        )
            .setSessionCommand(SessionCommand(PlaybackSessionActions.COMMAND_SHUFFLE, Bundle.EMPTY))
            .setDisplayName(if (state.shuffle) "Shuffle on" else "Shuffle off")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .setEnabled(state.hasTrack)
            .build(),
        CommandButton.Builder(
            when (state.repeat) {
                RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF
                RepeatMode.All -> CommandButton.ICON_REPEAT_ALL
                RepeatMode.One -> CommandButton.ICON_REPEAT_ONE
            },
        )
            .setSessionCommand(SessionCommand(PlaybackSessionActions.COMMAND_REPEAT, Bundle.EMPTY))
            .setDisplayName(
                when (state.repeat) {
                    RepeatMode.Off -> "Repeat off"
                    RepeatMode.All -> "Repeat all"
                    RepeatMode.One -> "Repeat one"
                },
            )
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .setEnabled(state.hasTrack)
            .build(),
        CommandButton.Builder(
            if (state.saved) CommandButton.ICON_BOOKMARK_FILLED else CommandButton.ICON_BOOKMARK_UNFILLED,
        )
            .setSessionCommand(SessionCommand(PlaybackSessionActions.COMMAND_SAVE, Bundle.EMPTY))
            .setDisplayName(if (state.saved) "Saved" else "Save")
            .setSlots(
                CommandButton.SLOT_FORWARD_SECONDARY,
                CommandButton.SLOT_OVERFLOW,
            )
            .setEnabled(state.hasTrack)
            .build(),
    )
}
