package net.pipebomb.android.playback

data class StorageHealth(
    val selectedId: String,
    val label: String,
    val cacheDir: String?,
    val selectedDir: String?,
    val fallback: Boolean,
    val mismatch: Boolean,
    val available: Boolean,
    val freeBytes: Long,
    val totalBytes: Long,
    val error: String? = null,
) {
    val issue: String?
        get() = when {
            error != null -> error
            fallback -> "Selected storage is unavailable. Reconnect it and restart Pipe Bomb."
            mismatch -> "Storage changed since startup. Restart Pipe Bomb to use it."
            !available -> "Download storage is unavailable."
            else -> null
        }

    companion object {
        fun unknown(): StorageHealth = StorageHealth(
            selectedId = "",
            label = "Internal storage",
            cacheDir = null,
            selectedDir = null,
            fallback = false,
            mismatch = false,
            available = true,
            freeBytes = 0,
            totalBytes = 0,
        )
    }
}
