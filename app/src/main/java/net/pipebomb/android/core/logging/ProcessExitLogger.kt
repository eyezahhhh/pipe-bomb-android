package net.pipebomb.android.core.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.InputStream

class ProcessExitLogger(private val context: Context) {
    fun logPreviousExits() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_CHECK, now - FIRST_RUN_WINDOW_MS)
        val reasons = runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_REASONS)
        }.getOrDefault(emptyList())
        reasons.forEach { info ->
            if (info.timestamp > last) {
                logExit(info)
            }
        }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun logExit(info: ApplicationExitInfo) {
        val reason = reasonName(info.reason)
        AppLog.warn(
            category = LogCategory.APP,
            event = "app.process.exit",
            fields = mapOf(
                "reason" to reason,
                "status" to info.status,
                "importance" to info.importance,
                "timestamp" to info.timestamp,
                "pssKb" to info.pss,
                "rssKb" to info.rss,
                "description" to info.description,
            ),
        )
        if (reason in TRACE_REASONS) {
            val trace = runCatching {
                info.traceInputStream?.use { readLimited(it, MAX_TRACE_BYTES) }
            }.getOrNull()
            if (!trace.isNullOrBlank()) {
                AppLog.error(
                    category = LogCategory.APP,
                    event = "app.process.exit.trace",
                    fields = mapOf(
                        "reason" to reason,
                        "timestamp" to info.timestamp,
                        "trace" to trace,
                    ),
                )
            }
        }
    }

    private fun readLimited(stream: InputStream, limit: Int): String {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = stream.read(buffer, total, limit - total)
            if (read <= 0) break
            total += read
        }
        return String(buffer, 0, total, Charsets.UTF_8)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exit_self"
        ApplicationExitInfo.REASON_SIGNALED -> "signaled"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
        ApplicationExitInfo.REASON_ANR -> "anr"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_change"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
        ApplicationExitInfo.REASON_OTHER -> "other"
        ApplicationExitInfo.REASON_FREEZER -> "freezer"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package_state_change"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "package_updated"
        ApplicationExitInfo.REASON_UNKNOWN -> "unknown"
        else -> "reason_$reason"
    }

    private companion object {
        const val PREFS_NAME = "diagnostics_bootstrap"
        const val KEY_LAST_CHECK = "last_exit_check"
        const val MAX_EXIT_REASONS = 10
        const val MAX_TRACE_BYTES = 8 * 1024
        const val FIRST_RUN_WINDOW_MS = 24 * 60 * 60 * 1000L
        val TRACE_REASONS = setOf("crash", "crash_native", "anr")
    }
}
