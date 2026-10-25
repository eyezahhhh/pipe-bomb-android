package net.pipebomb.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.di.AppStartupEntryPoint
import javax.inject.Inject

@HiltAndroidApp
class PipeBombApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.processName == packageName) {
            val startup = EntryPointAccessors.fromApplication(this, AppStartupEntryPoint::class.java)
            startup.playbackReporter()
            startup.downloadCoordinator().start()
            startup.collectionSyncScheduler().apply {
                schedulePeriodic()
                syncNow()
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { apiClientFactory.client }))
            }
            .build()
}
