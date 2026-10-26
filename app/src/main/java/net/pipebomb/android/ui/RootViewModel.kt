package net.pipebomb.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.pipebomb.android.core.datastore.AppSettingsStore
import net.pipebomb.android.core.net.ServerUrlProvider
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data class Connect(val prefill: String?) : RootState
    data object Login : RootState
    data object Ready : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settings: AppSettingsStore,
    serverUrlProvider: ServerUrlProvider,
    private val authRepository: AuthRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            settings.serverConfig
                .distinctUntilChanged()
                .collect { config ->
                    if (!config.isReady) {
                        authRepository.clearSession()
                    } else if (authRepository.session.value is SessionState.Unknown) {
                        authRepository.refresh()
                    }
                }
        }
    }

    val state: StateFlow<RootState> = combine(
        settings.serverConfig,
        serverUrlProvider.urls,
        authRepository.session,
    ) { config, url, session ->
        when {
            config.serverUrl == null -> RootState.Connect(null)
            !config.isReady -> RootState.Connect(config.serverUrl)
            url == null -> RootState.Loading
            else -> when (session) {
                SessionState.Unknown -> RootState.Loading
                SessionState.Unauthenticated -> RootState.Login
                is SessionState.Authenticated -> RootState.Ready
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RootState.Loading,
    )
}
