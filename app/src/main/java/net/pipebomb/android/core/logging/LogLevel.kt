package net.pipebomb.android.core.logging

enum class LogLevel(val priority: Int, val token: String) {
    DEBUG(0, "D"),
    INFO(1, "I"),
    WARN(2, "W"),
    ERROR(3, "E"),
    FATAL(4, "F"),
}
