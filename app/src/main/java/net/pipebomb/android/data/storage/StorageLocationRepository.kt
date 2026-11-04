package net.pipebomb.android.data.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.pipebomb.android.core.logging.AppLog
import net.pipebomb.android.core.logging.LogCategory
import net.pipebomb.android.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()
    @Volatile
    private var lastFallback: Boolean? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppLog.debug(
                category = LogCategory.STORAGE,
                event = "storage.volume",
                fields = mapOf("action" to intent?.action),
            )
            _revision.update { it + 1 }
        }
    }

    init {
        val pendingDelete = applyPendingSwitch()
        scope.launch(Dispatchers.IO) {
            pendingDelete?.let(::deleteCacheDirectory)
            if (!resolve().isFallback) {
                File(context.filesDir, FALLBACK_MEDIA_DIR).deleteRecursively()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun availableLocations(): List<StorageLocation> {
        val locations = mutableListOf(
            internalLocation(),
        )
        val seen = mutableSetOf(locations.first().path.absolutePath)
        for (dir in context.getExternalFilesDirs(null)) {
            if (dir == null || !seen.add(dir.absolutePath)) continue
            val volume = context.getSystemService(StorageManager::class.java)
                .getStorageVolume(dir)
                ?: continue
            if (volume.state != Environment.MEDIA_MOUNTED || !dir.canWrite()) continue
            val isPrimary = volume.isPrimary
            val id = if (isPrimary) {
                EXTERNAL_PRIMARY_ID
            } else {
                "external:${volume.uuid ?: dir.absolutePath}"
            }
            locations += StorageLocation(
                id = id,
                label = if (isPrimary) "Device storage" else volume.getDescription(context),
                path = dir,
                isRemovable = volume.isRemovable,
                isPrimary = isPrimary,
                availableBytes = dir.availableBytes(),
                totalBytes = dir.totalBytes(),
            )
        }
        return locations
    }

    fun selectedLocationId(): String =
        prefs.getString(KEY_SELECTED_LOCATION, INTERNAL_ID) ?: INTERNAL_ID

    fun selectedLocationLabel(): String {
        val selectedId = selectedLocationId()
        return prefs.getString(KEY_SELECTED_LABEL, null)
            ?: availableLocations().firstOrNull { it.id == selectedId }?.label
            ?: if (selectedId == INTERNAL_ID) {
                "Internal storage"
            } else {
                "Selected storage (unavailable)"
            }
    }

    fun resolve(): ResolvedStorage {
        val resolved = resolveStorage()
        if (resolved.isFallback != lastFallback) {
            lastFallback = resolved.isFallback
            if (resolved.isFallback) {
                AppLog.warn(
                    category = LogCategory.STORAGE,
                    event = "storage.fallback",
                    fields = mapOf("selectedId" to resolved.selectedId),
                )
            } else {
                AppLog.info(
                    category = LogCategory.STORAGE,
                    event = "storage.available",
                    fields = mapOf("selectedId" to resolved.selectedId),
                )
            }
        }
        return resolved
    }

    private fun resolveStorage(): ResolvedStorage {
        val selectedId = selectedLocationId()
        if (selectedId == INTERNAL_ID) {
            val location = internalLocation()
            return ResolvedStorage(
                selectedId = selectedId,
                location = location,
                cacheDir = File(location.path, MEDIA_DIR),
                isFallback = false,
            )
        }
        val location = availableLocations().firstOrNull { it.id == selectedId }
        if (location == null) {
            return ResolvedStorage(
                selectedId = selectedId,
                location = null,
                cacheDir = File(context.filesDir, FALLBACK_MEDIA_DIR),
                isFallback = true,
            )
        }
        return ResolvedStorage(
            selectedId = selectedId,
            location = location,
            cacheDir = File(location.path, MEDIA_DIR),
            isFallback = false,
        )
    }

    fun requestSwitch(location: StorageLocation): Boolean {
        val current = resolve()
        if (location.id == current.selectedId && current.location != null) return false
        AppLog.info(
            category = LogCategory.STORAGE,
            event = "storage.switch.requested",
            fields = mapOf(
                "from" to current.selectedId,
                "to" to location.id,
            ),
        )
        prefs.edit()
            .putString(KEY_PENDING_LOCATION, location.id)
            .putString(KEY_PENDING_LABEL, location.label)
            .putString(KEY_PENDING_DELETE, current.cacheDir.absolutePath)
            .commit()
        return true
    }

    fun selectedAvailableBytes(): Long {
        val resolved = resolve()
        return resolved.location?.path?.availableBytes() ?: context.filesDir.availableBytes()
    }

    fun selectedTotalBytes(): Long {
        val resolved = resolve()
        return resolved.location?.path?.totalBytes() ?: context.filesDir.totalBytes()
    }

    private fun applyPendingSwitch(): File? {
        val target = prefs.getString(KEY_PENDING_LOCATION, null) ?: return null
        val label = prefs.getString(KEY_PENDING_LABEL, null)
        val deletePath = prefs.getString(KEY_PENDING_DELETE, null)
        prefs.edit()
            .putString(KEY_SELECTED_LOCATION, target)
            .putString(KEY_SELECTED_LABEL, label)
            .remove(KEY_PENDING_LOCATION)
            .remove(KEY_PENDING_LABEL)
            .remove(KEY_PENDING_DELETE)
            .apply()
        AppLog.info(
            category = LogCategory.STORAGE,
            event = "storage.switch.applied",
            fields = mapOf("selectedId" to target, "label" to label),
        )
        return deletePath?.let(::File)
    }

    private fun deleteCacheDirectory(dir: File) {
        if (!isAppOwnedPath(dir)) return
        runCatching { dir.deleteRecursively() }
            .onFailure { error ->
                AppLog.warn(
                    category = LogCategory.STORAGE,
                    event = "storage.delete.failed",
                    message = error.message,
                    fields = mapOf("exception" to error.javaClass.simpleName),
                )
            }
    }

    private fun isAppOwnedPath(dir: File): Boolean {
        val target = runCatching { dir.canonicalFile }.getOrNull() ?: return false
        val roots = buildList {
            add(context.filesDir)
            add(context.cacheDir)
            context.getExternalFilesDirs(null).filterNotNull().forEach(::add)
        }
        return roots.any { root ->
            val rootPath = runCatching { root.canonicalFile }.getOrNull() ?: return@any false
            target != rootPath && target.path.startsWith(rootPath.path + File.separator)
        }
    }

    private fun internalLocation(): StorageLocation = StorageLocation(
        id = INTERNAL_ID,
        label = "Internal storage",
        path = context.filesDir,
        isRemovable = false,
        isPrimary = true,
        availableBytes = context.filesDir.availableBytes(),
        totalBytes = context.filesDir.totalBytes(),
    )

    private fun File.availableBytes(): Long = runCatching { StatFs(absolutePath).availableBytes }.getOrDefault(0L)

    private fun File.totalBytes(): Long = runCatching { StatFs(absolutePath).totalBytes }.getOrDefault(0L)

    companion object {
        const val INTERNAL_ID = "internal"
        const val EXTERNAL_PRIMARY_ID = "external:primary"
        const val MEDIA_DIR = "media"
        const val FALLBACK_MEDIA_DIR = "media-fallback"

        private const val PREFS_NAME = "storage_bootstrap"
        private const val KEY_SELECTED_LOCATION = "selected_location"
        private const val KEY_SELECTED_LABEL = "selected_label"
        private const val KEY_PENDING_LOCATION = "pending_location"
        private const val KEY_PENDING_LABEL = "pending_label"
        private const val KEY_PENDING_DELETE = "pending_delete"
    }
}
