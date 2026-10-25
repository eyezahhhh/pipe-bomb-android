package net.pipebomb.android.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import net.pipebomb.android.BuildConfig
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ConnectivityMonitor
import net.pipebomb.android.core.net.ConnectivityStatus
import net.pipebomb.android.core.net.CookieStorage
import net.pipebomb.android.core.net.DataStoreServerUrlProvider
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.core.net.ServerUrlProvider
import net.pipebomb.android.core.net.SharedPreferencesCookieStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = PipeBombJson

    @Provides
    @Singleton
    fun provideCookieStorage(@ApplicationContext context: Context): CookieStorage =
        SharedPreferencesCookieStorage(context)

    @Provides
    @Singleton
    fun provideCookieJar(storage: CookieStorage): PersistentCookieJar =
        PersistentCookieJar(storage)

    @Provides
    @Singleton
    fun provideApiClientFactory(
        serverUrlProvider: ServerUrlProvider,
        cookieJar: PersistentCookieJar,
        json: Json,
    ): ApiClientFactory = ApiClientFactory(
        serverUrlProvider = serverUrlProvider,
        cookieJar = cookieJar,
        json = json,
        debugLogging = BuildConfig.DEBUG,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingsModule {
    @Binds
    @Singleton
    abstract fun bindServerUrlProvider(
        impl: DataStoreServerUrlProvider,
    ): ServerUrlProvider

    @Binds
    @Singleton
    abstract fun bindConnectivityStatus(
        impl: ConnectivityMonitor,
    ): ConnectivityStatus
}
