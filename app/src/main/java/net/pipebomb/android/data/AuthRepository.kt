package net.pipebomb.android.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.pipebomb.android.core.model.LoginDto
import net.pipebomb.android.core.model.User
import net.pipebomb.android.core.net.ApiClientFactory
import net.pipebomb.android.core.net.ApiError
import net.pipebomb.android.core.net.ApiResult
import net.pipebomb.android.core.net.PersistentCookieJar
import net.pipebomb.android.core.net.PipeBombJson
import net.pipebomb.android.core.net.apiCall
import net.pipebomb.android.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Unknown : SessionState
    data object Unauthenticated : SessionState
    data class Authenticated(val user: User) : SessionState
}

@Singleton
class AuthRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val apiClientFactory: ApiClientFactory,
    private val cookieJar: PersistentCookieJar,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var cachedUser: User? = prefs.getString(KEY_USER, null)?.let { raw ->
        runCatching { PipeBombJson.decodeFromString<User>(raw) }.getOrNull()
    }

    private val _session = MutableStateFlow<SessionState>(
        cachedUser?.let(SessionState::Authenticated) ?: SessionState.Unknown,
    )
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val _lastError = MutableStateFlow<ApiError?>(null)
    val lastError: StateFlow<ApiError?> = _lastError.asStateFlow()

    init {
        if (cachedUser != null) {
            scope.launch { refresh() }
        }
    }

    suspend fun refresh(): ApiResult<User> {
        val result = apiCall { apiClientFactory.requireApi().getSelf() }
        when (result) {
            is ApiResult.Success -> {
                _lastError.value = null
                cacheUser(result.value)
                _session.value = SessionState.Authenticated(result.value)
            }

            is ApiResult.Failure -> {
                _lastError.value = result.error
                val user = cachedUser
                if (result.error is ApiError.Network && user != null) {
                    _session.value = SessionState.Authenticated(user)
                } else {
                    clearCachedUser()
                    _session.value = SessionState.Unauthenticated
                }
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
        clearCachedUser()
        _session.value = SessionState.Unauthenticated
        _lastError.value = null
    }

    private suspend fun authenticate(block: suspend () -> User): ApiResult<User> {
        val result = apiCall(block)
        when (result) {
            is ApiResult.Success -> {
                _lastError.value = null
                cacheUser(result.value)
                _session.value = SessionState.Authenticated(result.value)
            }

            is ApiResult.Failure -> {
                _lastError.value = result.error
            }
        }
        return result
    }

    private fun cacheUser(user: User) {
        cachedUser = user
        prefs.edit()
            .putString(KEY_USER, PipeBombJson.encodeToString(User.serializer(), user))
            .apply()
    }

    private fun clearCachedUser() {
        cachedUser = null
        prefs.edit().remove(KEY_USER).apply()
    }

    private companion object {
        const val PREFS_NAME = "auth_bootstrap"
        const val KEY_USER = "last_user"
    }
}
