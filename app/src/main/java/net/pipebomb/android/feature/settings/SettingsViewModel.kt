package net.pipebomb.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.BuildConfig
import net.pipebomb.android.core.net.DataStoreServerUrlProvider
import net.pipebomb.android.data.AuthRepository
import net.pipebomb.android.data.SessionState
import net.pipebomb.android.playback.PlaybackCache
import net.pipebomb.android.playback.PlaybackController
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val cacheBytes: Long = 0,
    val appVersion: String = BuildConfig.VERSION_NAME,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serverUrlProvider: DataStoreServerUrlProvider,
    private val authRepository: AuthRepository,
    private val playbackCache: PlaybackCache,
    private val playbackController: PlaybackController,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = SettingsUiState(
            serverUrl = serverUrlProvider.current()?.toString().orEmpty(),
            username = (authRepository.session.value as? SessionState.Authenticated)
                ?.user?.username.orEmpty(),
            cacheBytes = playbackCache.sizeBytes(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }

    fun clearCache() {
        viewModelScope.launch {
            playbackCache.clear()
            refresh()
        }
    }

    fun logout() {
        viewModelScope.launch {
            playbackController.clearQueue()
            authRepository.logout()
        }
    }

    fun changeServer() {
        viewModelScope.launch {
            playbackController.clearQueue()
            playbackCache.clear()
            authRepository.clearSession()
            serverUrlProvider.clear()
        }
    }
}
