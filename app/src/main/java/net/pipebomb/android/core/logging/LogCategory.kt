package net.pipebomb.android.core.logging

enum class LogCategory(val token: String) {
    APP("app"),
    NETWORK("net"),
    AUTH("auth"),
    PLAYBACK("playback"),
    MEDIA3("media3"),
    CACHE("cache"),
    DOWNLOAD("download"),
    STORAGE("storage"),
    AUTO("auto"),
    SETTINGS("settings"),
    DIAGNOSTICS("diagnostics"),
    CRASH("crash"),
}
