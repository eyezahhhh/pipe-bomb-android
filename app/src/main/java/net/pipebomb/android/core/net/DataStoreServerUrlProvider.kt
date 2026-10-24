package net.pipebomb.android.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.di.ApplicationScope
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreServerUrlProvider @Inject constructor(
    private val store: AppSettingsStore,
    @ApplicationScope scope: CoroutineScope,
) : ServerUrlProvider {
    private val state = MutableStateFlow<HttpUrl?>(null)
    override val urls: StateFlow<HttpUrl?> = state.asStateFlow()

    init {
        scope.launch {
            store.serverConfig.collect { config ->
                state.value = config.serverUrl?.let { ServerUrlParser.parse(it).getOrNull() }
            }
        }
    }

    override fun current(): HttpUrl? = state.value

    suspend fun setServerUrl(input: String): Result<HttpUrl> {
        val parsed = ServerUrlParser.parse(input)
        parsed.getOrNull()?.let { url ->
            store.setServerUrl(url.toString())
            state.value = url
        }
        return parsed
    }

    suspend fun markReady() {
        store.setServerReady(true)
    }

    suspend fun clear() {
        store.clearServer()
        state.value = null
    }
}
