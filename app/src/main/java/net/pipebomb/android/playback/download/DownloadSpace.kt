package net.pipebomb.android.playback.download

object DownloadSpace {
    fun availableForDownloads(freeBytes: Long, reserveBytes: Long): Long =
        (freeBytes - reserveBytes).coerceAtLeast(0L)

    fun canStartDownload(freeBytes: Long, reserveBytes: Long): Boolean =
        freeBytes > reserveBytes
}
