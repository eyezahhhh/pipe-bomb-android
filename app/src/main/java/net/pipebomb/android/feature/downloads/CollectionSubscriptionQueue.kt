package net.pipebomb.android.feature.downloads

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CollectionSubscriptionQueue(
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val activeIds = ConcurrentHashMap.newKeySet<String>()
    private val activeCount = MutableStateFlow(0)

    val isBusy: StateFlow<Boolean> = activeCount
        .map { it > 0 }
        .stateIn(scope, SharingStarted.Eagerly, false)

    fun enqueue(id: String, block: suspend () -> Unit): Boolean {
        if (!activeIds.add(id)) return false
        activeCount.update { it + 1 }
        scope.launch {
            try {
                mutex.withLock { block() }
            } finally {
                activeIds.remove(id)
                activeCount.update { it - 1 }
            }
        }
        return true
    }
}
