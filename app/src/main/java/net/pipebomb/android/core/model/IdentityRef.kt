package net.pipebomb.android.core.model

data class IdentityRef(
    val pluginId: String,
    val identifierId: String,
    val value: String,
) {
    val key: String get() = "$pluginId~$identifierId~$value"
}

fun parseIdentityRef(value: String): IdentityRef? {
    val parts = value.split('~')
    if (parts.size != 3 || parts.any { it.isBlank() }) return null
    return IdentityRef(parts[0], parts[1], parts[2])
}
