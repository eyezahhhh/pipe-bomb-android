package net.pipebomb.android.core.logging

import java.security.MessageDigest

object LogRedaction {
    const val MAX_BODY_BYTES = 1024

    private val urlRegex = Regex(
        """(?i)\b(https?)://([^/\s?#]+)([^\s?#]*)(\?[^\s#]*)?(#\S*)?""",
    )
    private val audioPathRegex = Regex(
        """(?i)(https?://[^/\s?#]+)/audio/[^\s?#]*""",
    )
    private val bearerRegex = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jsonSecretRegex = Regex(
        """(?i)("(?:[^"]*?(?:token|secret|password|passwd|cookie|authorization|auth|session)[^"]*?)"\s*:\s*)"(?:[^"\\]|\\.)*"""",
    )
    private val secretPairRegex = Regex(
        """(?i)\b(token|secret|password|passwd|cookie|authorization|auth_token|session(?:id)?)\b(\s*[=:]\s*)([^\s,;&"']+)""",
    )

    fun url(raw: String): String {
        val withoutAudioSession = audioPathRegex.replace(raw) { match ->
            "${match.groupValues[1]}/audio/<redacted>"
        }
        return urlRegex.replace(withoutAudioSession) { match ->
            val scheme = match.groupValues[1]
            val authority = match.groupValues[2].substringAfterLast('@')
            val path = match.groupValues[3]
            "$scheme://$authority$path"
        }
    }

    fun requestUrl(scheme: String, host: String, port: Int, encodedPath: String): String {
        val defaultPort = (scheme == "http" && port == 80) ||
            (scheme == "https" && port == 443)
        return if (defaultPort) {
            "$scheme://$host$encodedPath"
        } else {
            "$scheme://$host:$port$encodedPath"
        }
    }

    fun message(input: String): String {
        var output = url(input)
        output = bearerRegex.replace(output, "Bearer ***")
        output = jsonSecretRegex.replace(output) { match ->
            "${match.groupValues[1]}\"***\""
        }
        output = secretPairRegex.replace(output) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}***"
        }
        return output
    }

    fun body(input: String, limit: Int = MAX_BODY_BYTES): String {
        val redacted = message(input)
        return if (redacted.length > limit) {
            redacted.take(limit) + "…<truncated>"
        } else {
            redacted
        }
    }

    fun sessionId(id: String): String = sha256(id).take(8)

    fun throwable(error: Throwable, limit: Int = 4096): String =
        body(error.stackTraceToString(), limit)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
