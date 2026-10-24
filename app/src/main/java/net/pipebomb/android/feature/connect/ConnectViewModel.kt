package net.pipebomb.android.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.DataStoreServerUrlProvider
import net.pipebomb.android.core.net.NoServerConfiguredException
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.core.net.userMessage
import javax.inject.Inject

data class ConnectUiState(
    val isConnecting: Boolean = false,
    val error: String? = null,
    val needsSetup: Boolean = false,
    val setupUrl: String? = null,
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val serverUrlProvider: DataStoreServerUrlProvider,
    private val apiClientFactory: ApiClientFactory,
    private val cookieJar: PersistentCookieJar,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun connect(rawUrl: String) {
        if (_state.value.isConnecting) return
        viewModelScope.launch {
            _state.update { it.copy(isConnecting = true, error = null, needsSetup = false) }

            val previous = serverUrlProvider.current()
            val parsed = serverUrlProvider.setServerUrl(rawUrl)
            val url = parsed.getOrNull()
            if (url == null) {
                _state.update {
                    it.copy(
                        isConnecting = false,
                        error = parsed.exceptionOrNull()?.message ?: "Enter a valid server URL",
                    )
                }
                return@launch
            }

            if (previous != null && previous != url) {
                cookieJar.clear()
            }

            val setup = try {
                apiCall { apiClientFactory.requireApi().getSetupStatus() }
            } catch (_: NoServerConfiguredException) {
                ApiResult.Failure(net.pipebomb.android.core.net.ApiError.Network("No server configured"))
            }

            when (setup) {
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(isConnecting = false, error = setup.userMessage())
                    }
                }

                is ApiResult.Success -> {
                    apiCall { apiClientFactory.requireApi().getSelf() }

                    if (setup.value.needsSetup) {
                        _state.update {
                            it.copy(
                                isConnecting = false,
                                needsSetup = true,
                                setupUrl = url.toString(),
                            )
                        }
                    } else {
                        serverUrlProvider.markReady()
                        _state.update { ConnectUiState() }
                    }
                }
            }
        }
    }

    fun retry() {
        val url = _state.value.setupUrl ?: serverUrlProvider.current()?.toString() ?: return
        connect(url)
    }
}
