package net.pipebomb.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import net.pipebomb.android.playback.PlaybackReporter
import javax.inject.Inject

@HiltAndroidApp
class PipeBombApplication : Application() {
    @Inject
    lateinit var playbackReporter: PlaybackReporter
}
