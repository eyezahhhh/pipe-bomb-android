package net.pipebomb.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.playback.PlaybackReporter
import javax.inject.Inject

@HiltAndroidApp
class PipeBombApplication : Application(), SingletonImageLoader.Factory {
    @Inject
    lateinit var playbackReporter: PlaybackReporter

    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(true)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { apiClientFactory.client }))
            }
            .build()
}
