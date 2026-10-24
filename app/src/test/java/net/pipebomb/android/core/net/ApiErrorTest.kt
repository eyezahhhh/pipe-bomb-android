package net.pipebomb.android.core.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorTest {
    private fun httpError(status: Int, body: String = ""): HttpException {
        val response = Response.error<Any>(
            status,
            body.toResponseBody("application/json".toMediaType()),
        )
        return HttpException(response)
    }

    @Test
    fun mapsUnauthorizedAndUsesServerMessage() {
        val error = httpError(401, """{"error":"Unauthorized","message":"Bad login","statusCode":401}""")
            .toApiError()
        assertTrue(error is ApiError.Unauthorized)
        assertEquals("Bad login", error.message)
    }

    @Test
    fun mapsForbidden() {
        assertTrue(httpError(403).toApiError() is ApiError.Forbidden)
    }

    @Test
    fun mapsNotFound() {
        assertTrue(httpError(404).toApiError() is ApiError.NotFound)
    }

    @Test
    fun mapsConflictWithMessage() {
        val error = httpError(409, """{"message":"Username taken"}""").toApiError()
        assertTrue(error is ApiError.Conflict)
        assertEquals("Username taken", error.message)
    }

    @Test
    fun mapsServerErrors() {
        val error = httpError(503).toApiError()
        assertTrue(error is ApiError.Server)
        assertEquals(503, (error as ApiError.Server).statusCode)
    }

    @Test
    fun mapsOtherClientErrors() {
        val error = httpError(422).toApiError()
        assertTrue(error is ApiError.Client)
        assertEquals(422, (error as ApiError.Client).statusCode)
    }
}
