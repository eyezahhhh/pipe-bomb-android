package net.pipebomb.android.core.model

data class TrackKey(
    val pluginId: String,
    val libraryId: String,
    val trackId: String,
) {
    override fun toString(): String = "$pluginId:$libraryId:$trackId"

    companion object {
        fun parse(value: String): TrackKey? {
            val parts = value.split(':', limit = 3)
            if (parts.size != 3 || parts.any { it.isBlank() }) return null
            return TrackKey(parts[0], parts[1], parts[2])
        }
    }
}
