package net.pipebomb.android.core.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrlParser {
    private val localHost = Regex(
        "^(localhost|127\\.\\d+\\.\\d+\\.\\d+|10\\.\\d+\\.\\d+\\.\\d+|192\\.168\\.\\d+\\.\\d+|" +
            "172\\.(1[6-9]|2\\d|3[01])\\.\\d+\\.\\d+|\\[::1\\]|[^./:]+)(:\\d+)?(/.*)?$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): Result<HttpUrl> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Server URL is required"))
        }

        val withScheme = if ("://" in trimmed) {
            trimmed
        } else {
            val scheme = if (localHost.matches(trimmed)) "http" else "https"
            "$scheme://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("Enter a valid server URL"))

        if (parsed.host.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter a valid server URL"))
        }

        val builder = parsed.newBuilder()
            .query(null)
            .fragment(null)
        if (!parsed.encodedPath.endsWith('/')) {
            builder.addPathSegment("")
        }
        return Result.success(builder.build())
    }
}

interface ServerUrlProvider {
    fun current(): HttpUrl?
    val urls: StateFlow<HttpUrl?>
}

class InMemoryServerUrlProvider(initial: String? = null) : ServerUrlProvider {
    private val state = MutableStateFlow(initial?.let { ServerUrlParser.parse(it).getOrNull() })

    fun set(value: String?) {
        state.value = value?.let { ServerUrlParser.parse(it).getOrNull() }
    }

    override fun current(): HttpUrl? = state.value

    override val urls: StateFlow<HttpUrl?> = state.asStateFlow()
}
