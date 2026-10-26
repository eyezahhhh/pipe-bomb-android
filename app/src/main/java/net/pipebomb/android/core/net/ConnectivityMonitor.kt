package net.pipebomb.android.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    init {
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
        _isOnline.value = currentlyOnline()
        _isMetered.value = currentlyMetered()
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
