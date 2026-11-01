package net.pipebomb.android

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import androidx.annotation.OptIn
import androidx.hilt.work.HiltWorkerFactory
import androidx.media3.common.util.Log as Media3Log
import androidx.media3.common.util.UnstableApi
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Lazy
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.CrashLogHandler
import net.pipebomb.android.core.logging.DiagnosticsFiles
import net.pipebomb.android.core.logging.DiagnosticsLevel
import net.pipebomb.android.core.logging.FileAppLogger
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.core.logging.LogRedaction
import net.pipebomb.android.core.logging.Media3LogSink
import net.pipebomb.android.core.logging.ProcessExitLogger
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.data.storage.StorageLocationRepository
import net.pipebomb.android.di.AppStartupEntryPoint
import net.pipebomb.android.di.ApplicationScope
import javax.inject.Inject

@HiltAndroidApp
class PipeBombApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject
    lateinit var apiClientFactory: ApiClientFactory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: AppSettingsStore

    @Inject
    lateinit var storage: Lazy<StorageLocationRepository>

    @Inject
    lateinit var connectivity: ConnectivityStatus

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val isMainProcess = applicationInfo.processName == packageName
        AppLog.configure(
            logger = FileAppLogger(
                files = DiagnosticsFiles.forContext(this),
                pruneEnabled = isMainProcess,
            ),
            level = DiagnosticsLevel.Default,
        )
        Thread.setDefaultUncaughtExceptionHandler(
            CrashLogHandler(Thread.getDefaultUncaughtExceptionHandler()),
        )
        installMedia3Logging()
        logSessionStart(isMainProcess)
        applicationScope.launch {
            settings.diagnosticsLevel.collect(AppLog::setLevel)
        }
        applicationScope.launch(Dispatchers.IO) {
            ProcessExitLogger(this@PipeBombApplication).logPreviousExits()
        }
        if (isMainProcess) {
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            AppLog.warn(
                category = LogCategory.APP,
                event = "app.memory.trim",
                fields = mapOf("level" to level),
            )
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLog.warn(category = LogCategory.APP, event = "app.memory.low")
    }

    @OptIn(UnstableApi::class)
    private fun installMedia3Logging() {
        runCatching {
            Media3Log.setLogger(Media3LogSink())
            Media3Log.setLogLevel(Media3Log.LOG_LEVEL_ALL)
        }
    }

    private fun logSessionStart(isMainProcess: Boolean) {
        val storageState = runCatching { storage.get().resolve() }.getOrNull()
        AppLog.info(
            category = LogCategory.APP,
            event = "app.session.start",
            fields = mapOf(
                "versionName" to BuildConfig.VERSION_NAME,
                "versionCode" to BuildConfig.VERSION_CODE,
                "buildType" to BuildConfig.BUILD_TYPE,
                "sdkInt" to Build.VERSION.SDK_INT,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "abi" to Build.SUPPORTED_ABIS.firstOrNull(),
                "process" to applicationInfo.processName,
                "mainProcess" to isMainProcess,
                "loggingLevel" to AppLog.currentLevel().storageValue,
                "storageId" to storageState?.selectedId,
                "storageFallback" to storageState?.isFallback,
                "online" to connectivity.isOnline.value,
                "metered" to connectivity.isMetered.value,
            ),
        )
        applicationScope.launch {
            val server = settings.serverConfig.first().serverUrl
            AppLog.info(
                category = LogCategory.APP,
                event = "app.session.config",
                fields = mapOf(
                    "server" to server?.let(LogRedaction::url),
                    "loggingLevel" to settings.diagnosticsLevel.first().storageValue,
                ),
            )
        }
    }
}
