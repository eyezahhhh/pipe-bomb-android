package net.pipebomb.android.core.net

import kotlinx.serialization.SerializationException
import net.pipebomb.android.core.model.PipeBombErrorBody
import retrofit2.HttpException
import java.io.IOException

sealed interface ApiError {
    val message: String

    data class Unauthorized(override val message: String = "Session expired") : ApiError
    data class Forbidden(override val message: String = "Permission denied") : ApiError
    data class NotFound(override val message: String = "Not found") : ApiError
    data class Conflict(override val message: String = "Conflict") : ApiError
    data class Client(val statusCode: Int, override val message: String) : ApiError
    data class Server(val statusCode: Int, override val message: String) : ApiError
    data class Network(override val message: String = "Network unavailable", val cause: Throwable? = null) : ApiError
    data class Parse(override val message: String = "Unexpected server response", val cause: Throwable? = null) : ApiError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Failure(e.toApiError())
    } catch (e: SerializationException) {
        ApiResult.Failure(ApiError.Parse(cause = e))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network(cause = e))
    }
}

fun HttpException.toApiError(): ApiError {
    val body = response()?.errorBody()?.string().orEmpty()
    val parsed = runCatching {
        PipeBombJson.decodeFromString<PipeBombErrorBody>(body)
    }.getOrNull()
    val message = parsed?.message?.takeIf { it.isNotBlank() }
        ?: parsed?.error?.takeIf { it.isNotBlank() }
        ?: "HTTP ${code()}"
    return when (code()) {
        401 -> ApiError.Unauthorized(message)
        403 -> ApiError.Forbidden(message)
        404 -> ApiError.NotFound(message)
        409 -> ApiError.Conflict(message)
        in 400..499 -> ApiError.Client(code(), message)
        else -> ApiError.Server(code(), message)
    }
}

fun ApiError.userMessage(): String = when (this) {
    is ApiError.Unauthorized -> "Your session expired. Sign in again."
    is ApiError.Forbidden -> message
    is ApiError.NotFound -> "Not found"
    is ApiError.Conflict -> message
    is ApiError.Client -> message
    is ApiError.Server -> "Server error. Try again."
    is ApiError.Network -> "Can't reach the server. Check your connection."
    is ApiError.Parse -> "The server returned an unexpected response."
}

fun ApiResult.Failure.userMessage(): String = error.userMessage()

fun ApiError.toThrowable(): Throwable = when (this) {
    is ApiError.Network -> cause ?: java.io.IOException(message)
    is ApiError.Parse -> cause ?: IllegalStateException(message)
    else -> IllegalStateException(message)
}
