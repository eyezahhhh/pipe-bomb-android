package net.pipebomb.android.core.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

private val loopbackHosts = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1")

internal fun isLoopbackHost(host: String): Boolean =
    host.lowercase() in loopbackHosts || host.startsWith("127.")

fun rewriteServerUrl(raw: String, server: HttpUrl?): String {
    if (server == null || raw.isBlank()) return raw
    val url = raw.toHttpUrlOrNull() ?: return raw
    if (!isLoopbackHost(url.host)) return raw
    if (url.host == server.host && url.port == server.port && url.scheme == server.scheme) return raw
    return url.newBuilder()
        .scheme(server.scheme)
        .host(server.host)
        .port(server.port)
        .build()
        .toString()
}

class ServerUrlRewriteInterceptor(
    private val serverUrlProvider: ServerUrlProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val server = serverUrlProvider.current()
        if (server == null || !isLoopbackHost(request.url.host)) {
            return chain.proceed(request)
        }
        if (request.url.host == server.host &&
            request.url.port == server.port &&
            request.url.scheme == server.scheme
        ) {
            return chain.proceed(request)
        }
        val rewritten = request.url.newBuilder()
            .scheme(server.scheme)
            .host(server.host)
            .port(server.port)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}
