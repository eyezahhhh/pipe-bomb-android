package net.pipebomb.android.core.logging

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

class NetworkPhaseListener : EventListener() {
    private var callStartNs = 0L
    private var dnsStartNs = 0L
    private var dnsNs = 0L
    private var connectStartNs = 0L
    private var connectNs = 0L
    private var connectCount = 0
    private var tlsStartNs = 0L
    private var tlsNs = 0L
    private var headersStartNs = 0L
    private var failure: IOException? = null
    private var host = ""
    private var path = ""
    private var emitted = false

    override fun callStart(call: Call) {
        callStartNs = System.nanoTime()
        host = call.request().url.host
        path = call.request().url.encodedPath
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartNs = System.nanoTime()
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<java.net.InetAddress>,
    ) {
        if (dnsStartNs > 0) dnsNs += System.nanoTime() - dnsStartNs
        dnsStartNs = 0L
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
    ) {
        connectStartNs = System.nanoTime()
        connectCount += 1
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        if (connectStartNs > 0) connectNs += System.nanoTime() - connectStartNs
        connectStartNs = 0L
    }

    override fun secureConnectStart(call: Call) {
        tlsStartNs = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        if (tlsStartNs > 0) tlsNs += System.nanoTime() - tlsStartNs
        tlsStartNs = 0L
    }

    override fun responseHeadersStart(call: Call) {
        headersStartNs = System.nanoTime()
    }

    override fun callEnd(call: Call) {
        emit()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        failure = ioe
        emit()
    }

    private fun emit() {
        if (emitted) return
        emitted = true
        val now = System.nanoTime()
        val totalMs = if (callStartNs > 0) (now - callStartNs) / 1_000_000 else 0L
        if (failure == null && totalMs < SLOW_THRESHOLD_MS) return
        val ttfbMs = if (headersStartNs > 0 && callStartNs > 0) {
            (headersStartNs - callStartNs) / 1_000_000
        } else {
            null
        }
        AppLog.warn(
            category = LogCategory.NETWORK,
            event = "net.phases",
            message = failure?.message,
            fields = mapOf(
                "host" to host,
                "path" to path,
                "failed" to (failure != null),
                "exception" to failure?.javaClass?.simpleName,
                "dnsMs" to dnsNs / 1_000_000,
                "connectMs" to connectNs / 1_000_000,
                "connectCount" to connectCount,
                "tlsMs" to tlsNs / 1_000_000,
                "ttfbMs" to ttfbMs,
                "totalMs" to totalMs,
            ),
        )
    }

    private companion object {
        const val SLOW_THRESHOLD_MS = 2_000L
    }
}
