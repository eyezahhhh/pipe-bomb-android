package net.pipebomb.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val uuid: String,
    val username: String,
    val playlists: List<PlaylistSummary>? = null,
    val privileges: List<Privilege>? = null,
)

@Serializable
data class Privilege(
    val pluginId: String? = null,
    val key: String,
    val granted: Boolean,
    val includedIn: List<String> = emptyList(),
    val grantedByInclusion: Boolean = false,
)

@Serializable
data class PlaylistSummary(
    val uuid: String,
)

@Serializable
data class LoginDto(
    val username: String,
    val password: String,
)

@Serializable
data class SetupStatus(
    val needsSetup: Boolean,
)

@Serializable
data class PipeBombErrorBody(
    val error: String = "",
    val message: String = "",
    val statusCode: Int = 0,
)
