package net.pipebomb.android.core.net

import kotlinx.serialization.json.Json

val PipeBombJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = false
}
