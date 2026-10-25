package net.pipebomb.android.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.pipebomb.android.playback.PlaybackReporter
import net.pipebomb.android.playback.download.DownloadCoordinator

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppStartupEntryPoint {
    fun playbackReporter(): PlaybackReporter
    fun downloadCoordinator(): DownloadCoordinator
}
