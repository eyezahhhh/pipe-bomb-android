package net.pipebomb.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.pipebomb.android.core.net.ConnectivityStatus
import javax.inject.Inject

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    monitor: ConnectivityStatus,
) : ViewModel() {
    val isOnline: StateFlow<Boolean> = monitor.isOnline.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        monitor.isOnline.value,
    )
}
