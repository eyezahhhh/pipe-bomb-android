package net.pipebomb.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Identity(
    val pluginId: String,
    val identityId: String,
    val value: String,
    val ordinal: Int = 0,
)

@Serializable
data class Track(
    val uuid: String = "",
    val trackId: String,
    val pluginId: String,
    val libraryId: String,
    val title: String,
    val dateAdded: String? = null,
    val artists: List<TrackArtist>? = null,
    val albums: List<Album>? = null,
    val attributes: AttributeMap? = null,
    val identities: List<Identity>? = null,
    val bookmarked: Boolean? = null,
) {
    val key: TrackKey get() = TrackKey(pluginId, libraryId, trackId)
}

@Serializable
data class TrackArtist(
    val artistUuid: String? = null,
    val artist: Artist,
    val joinPhrase: String? = null,
)

@Serializable
data class AlbumArtist(
    val artistUuid: String? = null,
    val artist: Artist,
    val joinPhrase: String? = null,
)

@Serializable
data class Album(
    val uuid: String? = null,
    val attributes: AttributeMap? = null,
    val identities: List<Identity>? = null,
    val tracks: List<Track>? = null,
    val artists: List<AlbumArtist>? = null,
    val bookmarked: Boolean? = null,
) {
    val identityKey: String?
        get() = identities?.firstOrNull()?.let { "${it.pluginId}~${it.identityId}~${it.value}" }
}

@Serializable
data class Artist(
    val uuid: String? = null,
    val attributes: AttributeMap? = null,
    val identities: List<Identity>? = null,
    val tracks: List<Track>? = null,
    val albums: List<Album>? = null,
    val bookmarked: Boolean? = null,
) {
    val identityKey: String?
        get() = identities?.firstOrNull()?.let { "${it.pluginId}~${it.identityId}~${it.value}" }
}

@Serializable
data class PluginLibrary(
    val pluginId: String,
    val id: String,
    val name: String,
)

@Serializable
enum class PlaylistVisibility {
    @SerialName("public")
    Public,

    @SerialName("unlisted")
    Unlisted,

    @SerialName("private")
    Private,
}

@Serializable
data class PlaylistOwner(
    val type: String = "user",
    val userUuid: String? = null,
    val user: User? = null,
    val botId: String? = null,
    val botName: String? = null,
)

@Serializable
data class PlaylistMember(
    val userUuid: String,
    val user: User? = null,
    val role: String? = null,
    val dateAdded: String? = null,
)

@Serializable
data class UpsertPlaylistMemberDto(
    val role: String,
)

@Serializable
data class PlaylistTrack(
    val track: Track,
    val dateAdded: String? = null,
    val addedBy: PlaylistOwner? = null,
)

@Serializable
data class PlaylistTracksQuery(
    val offset: Int,
    val amount: Int,
)

@Serializable
data class Playlist(
    val uuid: String,
    val owner: PlaylistOwner = PlaylistOwner(),
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val visibility: PlaylistVisibility = PlaylistVisibility.Private,
    val attributes: AttributeMap? = null,
    val tracks: List<PlaylistTrack>? = null,
    val trackCount: Int? = null,
    val members: List<PlaylistMember>? = null,
)

@Serializable
data class PlaybackHistoryEntry(
    val trackUuid: String = "",
    val pluginId: String? = null,
    val clientName: String = "",
    val datePlayed: String? = null,
    val dateRecorded: String? = null,
    val track: Track? = null,
)

@Serializable
data class PlaybackHistoryPage(
    val entries: List<PlaybackHistoryEntry> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SavedTracks(
    val tracks: List<Track> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SavedAlbums(
    val albums: List<Album> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SavedArtists(
    val artists: List<Artist> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
)

@Serializable
data class SearchDto(
    val withTracks: Boolean = true,
    val withAlbums: Boolean = true,
    val withArtists: Boolean = true,
    val attributes: List<JsonElement>? = null,
    val query: String? = null,
    val sort: SearchSortDto? = null,
)

@Serializable
data class SearchSortDto(
    val key: String,
    val direction: String,
)

@Serializable
data class SearchSource(
    val pluginId: String,
    val sourceId: String,
    val name: String,
)

@Serializable
data class EphemeralSource(
    val id: String,
    val pluginId: String,
    val name: String,
)

@Serializable
data class EphemeralTrack(
    val uuid: String? = null,
    val trackId: String,
    val pluginId: String,
    val libraryId: String,
    val title: String,
    val attributes: AttributeMap? = null,
    val artists: List<TrackArtist>? = null,
    val bookmarked: Boolean? = null,
) {
    val key: TrackKey get() = TrackKey(pluginId, libraryId, trackId)
}

@Serializable
data class EphemeralSearchResults(
    val tracks: List<EphemeralTrack> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
)

@Serializable
data class ExternalUrl(
    val url: String,
    val iconUrl: String,
    val name: String,
)

@Serializable
data class TrackIdDto(
    val pluginId: String,
    val libraryId: String,
    val trackId: String,
)

@Serializable
data class TrackIdsDto(
    val tracks: List<TrackIdDto>,
)

@Serializable
data class AddPlaylistTracksDto(
    val tracks: List<TrackIdDto>? = null,
    val albums: List<NewPlaylistAlbumDto>? = null,
)

@Serializable
data class NewPlaylistAlbumDto(
    val uuid: String? = null,
    val pluginId: String? = null,
    val identityId: String? = null,
    val identity: String? = null,
)

@Serializable
data class UpdatePlaylistTracksDto(
    val add: AddPlaylistTracksDto? = null,
    val remove: List<TrackIdDto>? = null,
)

@Serializable
data class SearchSourceSummary(
    val pluginId: String,
    val sourceId: String,
    val name: String,
    val active: Boolean = false,
)

@Serializable
data class CreationSession(
    val uuid: String,
    val dateStarted: String? = null,
    val percent: Double? = null,
)

@Serializable
data class EphemeralSearchDto(
    val pluginId: String,
    val sourceId: String,
    val query: String,
)

@Serializable
data class AudioSession(
    val id: String,
    val baseUrl: String,
    val type: String,
)

@Serializable
data class ReportPlaybackDto(
    val pluginId: String,
    val libraryId: String,
    val trackId: String,
    val clientName: String,
)
