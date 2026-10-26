package net.pipebomb.android.core.net

import kotlinx.coroutines.flow.StateFlow

interface ConnectivityStatus {
    val isOnline: StateFlow<Boolean>
    val isMetered: StateFlow<Boolean>
}
