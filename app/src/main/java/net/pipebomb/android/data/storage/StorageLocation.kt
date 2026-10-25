package net.pipebomb.android.data.storage

import java.io.File

data class StorageLocation(
    val id: String,
    val label: String,
    val path: File,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val availableBytes: Long,
    val totalBytes: Long,
)

data class ResolvedStorage(
    val selectedId: String,
    val location: StorageLocation?,
    val cacheDir: File,
    val isFallback: Boolean,
) {
    val downloadsEnabled: Boolean get() = location != null
    val availableBytes: Long get() = location?.availableBytes ?: 0L
}
