package net.pipebomb.android.playback.download

import net.pipebomb.android.data.download.DownloadEntity
import net.pipebomb.android.data.download.DownloadState

internal data class ReconcilePlan(
    val indexHealthy: Boolean,
    val missingCompleted: List<String>,
    val failedIncomplete: List<String>,
    val orphans: List<String>,
)

/**
 * Decides what to do with Room download rows and Media3 index entries.
 *
 * An empty index is only trusted when there is nothing that should be indexed. Otherwise the
 * index is treated as unavailable and every mutation is skipped. Completed rows are never
 * deleted here: a missing file is surfaced as a retryable failure so a transient SD/index
 * problem cannot wipe the user's download metadata.
 */
internal fun planReconcile(
    rows: List<DownloadEntity>,
    indexedIds: Set<String>,
): ReconcilePlan {
    val hasVisibleRows = rows.any { row ->
        row.state != DownloadState.Pending.name &&
            row.state != DownloadState.Unsupported.name
    }
    val indexHealthy = indexedIds.isNotEmpty() || !hasVisibleRows
    if (!indexHealthy) {
        return ReconcilePlan(
            indexHealthy = false,
            missingCompleted = emptyList(),
            failedIncomplete = emptyList(),
            orphans = emptyList(),
        )
    }

    val missingCompleted = rows
        .filter { it.state == DownloadState.Completed.name && it.trackKey !in indexedIds }
        .map { it.trackKey }
    val failedIncomplete = rows
        .filter {
            it.trackKey !in indexedIds &&
                it.state in INCOMPLETE_STATES
        }
        .map { it.trackKey }
    val rowIds = rows.mapTo(mutableSetOf()) { it.trackKey }
    val orphans = if (rows.isEmpty()) {
        emptyList()
    } else {
        indexedIds.filterNot(rowIds::contains)
    }
    return ReconcilePlan(
        indexHealthy = true,
        missingCompleted = missingCompleted,
        failedIncomplete = failedIncomplete,
        orphans = orphans,
    )
}

private val INCOMPLETE_STATES = setOf(
    DownloadState.Queued.name,
    DownloadState.Downloading.name,
    DownloadState.Paused.name,
)
