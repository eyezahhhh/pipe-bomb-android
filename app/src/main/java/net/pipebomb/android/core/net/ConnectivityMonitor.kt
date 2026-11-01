package net.pipebomb.android.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityMonitor @Inject constructor(
    @param:ApplicationContext context: Context,
) : ConnectivityStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(currentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    private val _isMetered = MutableStateFlow(currentlyMetered())
    override val isMetered: StateFlow<Boolean> = _isMetered.asStateFlow()

    @Volatile
    private var lastSignature: String? = null

    init {
        lastSignature = signatureOf(currentlyOnline(), currentlyMetered(), activeCapabilities())
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    refresh()
                }

                override fun onLost(network: Network) {
                    refresh()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    refresh()
                }
            },
        )
    }

    private fun refresh() {
        val capabilities = activeCapabilities()
        val online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET,
        ) == true && capabilities?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
        ) != true
        val signature = signatureOf(online, metered, capabilities)
        if (signature != lastSignature) {
            lastSignature = signature
            val validated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED,
            ) == true
            AppLog.info(
                category = LogCategory.NETWORK,
                event = "net.connectivity",
                fields = mapOf(
                    "online" to online,
                    "metered" to metered,
                    "transports" to transportsOf(capabilities).joinToString("+"),
                    "validated" to validated,
                    "downstreamKbps" to (capabilities?.linkDownstreamBandwidthKbps ?: 0),
                    "upstreamKbps" to (capabilities?.linkUpstreamBandwidthKbps ?: 0),
                ),
            )
        }
        _isOnline.value = online
        _isMetered.value = metered
    }

    private fun signatureOf(
        online: Boolean,
        metered: Boolean,
        capabilities: NetworkCapabilities?,
    ): String = buildString {
        append(online)
        append('|')
        append(metered)
        append('|')
        append(transportsOf(capabilities).joinToString("+"))
        append('|')
        append(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        append('|')
        append(capabilities?.linkDownstreamBandwidthKbps ?: 0)
        append('|')
        append(capabilities?.linkUpstreamBandwidthKbps ?: 0)
    }

    private fun transportsOf(capabilities: NetworkCapabilities?): List<String> {
        if (capabilities == null) return emptyList()
        return buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("bluetooth")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
            if (isEmpty()) add("other")
        }
    }

    private fun currentlyOnline(): Boolean =
        activeCapabilities()?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun currentlyMetered(): Boolean {
        val capabilities = activeCapabilities() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun activeCapabilities(): NetworkCapabilities? {
        val network = manager.activeNetwork ?: return null
        return manager.getNetworkCapabilities(network)
    }
}
