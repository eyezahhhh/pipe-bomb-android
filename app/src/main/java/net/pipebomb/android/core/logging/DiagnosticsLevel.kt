package net.pipebomb.android.core.logging

enum class DiagnosticsLevel(val storageValue: String, val label: String) {
    OFF("off", "Off"),
    NORMAL("normal", "Normal"),
    VERBOSE("verbose", "Verbose"),
    ;

    fun allows(level: LogLevel): Boolean = when (this) {
        OFF -> level == LogLevel.FATAL
        NORMAL -> level.priority >= LogLevel.INFO.priority
        VERBOSE -> true
    }

    companion object {
        val Default = VERBOSE

        fun fromStorage(value: String?): DiagnosticsLevel =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}
