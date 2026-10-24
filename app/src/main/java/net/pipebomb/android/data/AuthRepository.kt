package net.pipebomb.android.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.pipebomb.android.core.model.LoginDto
import net.pipebomb.android.core.model.User
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.apiCall
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Unknown : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val user: User) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val cookieJar: PersistentCookieJar,
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Unknown)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _lastError = MutableStateFlow<ApiError?>(null)
    val lastError: StateFlow<ApiError?> = _lastError.asStateFlow()

    suspend fun refresh(): ApiResult<User> {
        val result = apiCall { apiClientFactory.requireApi().getSelf() }
        when (result) {
            is ApiResult.Success -> {
                _lastError.value = null
                _session.value = SessionState.Authenticated(result.value)
            }

            is ApiResult.Failure -> {
                _lastError.value = result.error
                _session.value = SessionState.Unauthenticated
            }
        }
        return result
    }

    suspend fun login(username: String, password: String): ApiResult<User> {
        return authenticate {
            apiClientFactory.requireApi().login(LoginDto(username.trim(), password))
        }
    }

    suspend fun signup(username: String, password: String): ApiResult<User> {
        return authenticate {
            apiClientFactory.requireApi().signup(LoginDto(username.trim(), password))
        }
    }

    suspend fun logout() {
        runCatching { apiClientFactory.requireApi().logout() }
        clearSession()
    }

    fun clearSession() {
        cookieJar.clear()
        _session.value = SessionState.Unauthenticated
        _lastError.value = null
    }

    private suspend fun authenticate(block: suspend () -> User): ApiResult<User> {
        val result = apiCall(block)
        when (result) {
            is ApiResult.Success -> {
                _lastError.value = null
                _session.value = SessionState.Authenticated(result.value)
            }

            is ApiResult.Failure -> {
                _lastError.value = result.error
            }
        }
        return result
    }
}
