package net.pipebomb.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.DataStoreServerUrlProvider
import net.pipebomb.android.core.net.userMessage
import net.pipebomb.android.data.AuthRepository
import javax.inject.Inject

enum class AuthMode { Login, Signup }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverUrlProvider: DataStoreServerUrlProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.lastError.collect { error ->
                if (error != null && _state.value.error == null) {
                    _state.update { it.copy(error = error.userMessage()) }
                }
            }
        }
    }

    fun setUsername(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun setPassword(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun toggleMode() {
        _state.update {
            it.copy(
                mode = if (it.mode == AuthMode.Login) AuthMode.Signup else AuthMode.Login,
                error = null,
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Enter a username and password") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            val result = if (current.mode == AuthMode.Login) {
                authRepository.login(current.username, current.password)
            } else {
                authRepository.signup(current.username, current.password)
            }
            if (result is ApiResult.Failure) {
                _state.update {
                    it.copy(isSubmitting = false, error = result.userMessage())
                }
            } else {
                _state.update { it.copy(isSubmitting = false, password = "") }
            }
        }
    }

    fun changeServer() {
        viewModelScope.launch {
            authRepository.clearSession()
            serverUrlProvider.clear()
        }
    }
}
