package net.pipebomb.android.core.net

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class NoServerConfiguredException : IllegalStateException("No server configured")

class ApiClientFactory(
    private val serverUrlProvider: ServerUrlProvider,
    private val cookieJar: PersistentCookieJar,
    private val json: Json,
    private val debugLogging: Boolean,
) {
    @Volatile
    private var cached: Pair<HttpUrl, PipeBombApi>? = null

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(ServerUrlRewriteInterceptor(serverUrlProvider))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (debugLogging) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .build()
    }

    fun requireApi(): PipeBombApi {
        val baseUrl = serverUrlProvider.current() ?: throw NoServerConfiguredException()
        cached?.let { (cachedUrl, api) ->
            if (cachedUrl == baseUrl) return api
        }
        return synchronized(this) {
            val existing = cached
            if (existing != null && existing.first == baseUrl) {
                existing.second
            } else {
                val api = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(PipeBombApi::class.java)
                cached = baseUrl to api
                api
            }
        }
    }
}
