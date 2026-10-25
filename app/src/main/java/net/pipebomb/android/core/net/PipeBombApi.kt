package net.pipebomb.android.core.net

import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.AudioSession
import net.pipebomb.android.core.model.EphemeralSearchDto
import net.pipebomb.android.core.model.EphemeralSearchResults
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.LoginDto
import net.pipebomb.android.core.model.PlaybackHistoryPage
import net.pipebomb.android.core.model.Playlist
import net.pipebomb.android.core.model.PlaylistMember
import net.pipebomb.android.core.model.PlaylistTrack
import net.pipebomb.android.core.model.ReportPlaybackDto
import net.pipebomb.android.core.model.PluginLibrary
import net.pipebomb.android.core.model.SavedAlbums
import net.pipebomb.android.core.model.SavedArtists
import net.pipebomb.android.core.model.SavedTracks
import net.pipebomb.android.core.model.SearchDto
import net.pipebomb.android.core.model.SearchResults
import net.pipebomb.android.core.model.SearchSourceSummary
import net.pipebomb.android.core.model.SetupStatus
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackIdsDto
import net.pipebomb.android.core.model.User
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface PipeBombApi {
    @GET("setup")
    suspend fun getSetupStatus(): SetupStatus

    @POST("users/login")
    suspend fun login(@Body body: LoginDto): User

    @POST("users/signup")
    suspend fun signup(@Body body: LoginDto): User

    @GET("users/me")
    suspend fun getSelf(): User

    @POST("users/logout")
    suspend fun logout(): Response<Unit>

    @GET("tracks/saved")
    suspend fun getSavedTracks(
        @Query("pageSize") pageSize: Int,
        @Query("page") page: Int,
    ): SavedTracks

    @GET("albums/saved")
    suspend fun getSavedAlbums(
        @Query("pageSize") pageSize: Int,
        @Query("page") page: Int,
    ): SavedAlbums

    @GET("artists/saved")
    suspend fun getSavedArtists(
        @Query("pageSize") pageSize: Int,
        @Query("page") page: Int,
    ): SavedArtists

    @POST("tracks")
    suspend fun getTracks(@Body body: TrackIdsDto): List<Track>

    @GET("tracks/{pluginId}/{libraryId}/{trackId}")
    suspend fun getTrack(
        @Path("pluginId") pluginId: String,
        @Path("libraryId") libraryId: String,
        @Path("trackId") trackId: String,
    ): Track

    @GET("tracks/{pluginId}/{libraryId}/{trackId}/audio")
    suspend fun createAudioSession(
        @Path("pluginId") pluginId: String,
        @Path("libraryId") libraryId: String,
        @Path("trackId") trackId: String,
    ): AudioSession

    @GET("albums/{albumUuid}")
    suspend fun getAlbum(@Path("albumUuid") albumUuid: String): Album

    @GET("albums/{pluginId}/{identifierId}/{identity}")
    suspend fun getAlbumByIdentity(
        @Path("pluginId") pluginId: String,
        @Path("identifierId") identifierId: String,
        @Path("identity") identity: String,
    ): Album

    @GET("artists/{artistUuid}")
    suspend fun getArtist(@Path("artistUuid") artistUuid: String): Artist

    @GET("artists/{pluginId}/{identifierId}/{identity}")
    suspend fun getArtistByIdentity(
        @Path("pluginId") pluginId: String,
        @Path("identifierId") identifierId: String,
        @Path("identity") identity: String,
    ): Artist

    @GET("playlists")
    suspend fun getOwnPlaylists(): List<Playlist>

    @GET("playlists/public")
    suspend fun getPublicPlaylists(): List<Playlist>

    @GET("playlists/{uuid}")
    suspend fun getPlaylist(
        @Path("uuid") uuid: String,
        @Query("tracks") tracks: Int = 200,
    ): Playlist

    @POST("playlists/{uuid}")
    suspend fun getPlaylistTracks(
        @Path("uuid") uuid: String,
        @Body body: net.pipebomb.android.core.model.PlaylistTracksQuery,
    ): List<PlaylistTrack>

    @PATCH("playlists/{uuid}/tracks")
    suspend fun updatePlaylistTracks(
        @Path("uuid") uuid: String,
        @Body body: net.pipebomb.android.core.model.UpdatePlaylistTracksDto,
    ): Playlist

    @DELETE("playlists/{uuid}")
    suspend fun deletePlaylist(@Path("uuid") uuid: String): Response<Unit>

    @GET("playlists/{uuid}/members")
    suspend fun getPlaylistMembers(@Path("uuid") uuid: String): List<PlaylistMember>

    @PUT("playlists/{uuid}/members/{userUuid}")
    suspend fun upsertPlaylistMember(
        @Path("uuid") uuid: String,
        @Path("userUuid") userUuid: String,
        @Body body: net.pipebomb.android.core.model.UpsertPlaylistMemberDto,
    ): PlaylistMember

    @DELETE("playlists/{uuid}/members/{userUuid}")
    suspend fun removePlaylistMember(
        @Path("uuid") uuid: String,
        @Path("userUuid") userUuid: String,
    ): Response<Unit>

    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): List<User>

    @GET("playlists/{uuid}/all")
    suspend fun getAllPlaylistTracks(@Path("uuid") uuid: String): List<PlaylistTrack>

    @GET("playback-history/me")
    suspend fun getPlaybackHistory(
        @Query("pageSize") pageSize: Int,
        @Query("page") page: Int,
    ): PlaybackHistoryPage

    @POST("playback-history/me")
    suspend fun reportPlayback(@Body body: ReportPlaybackDto): Response<Unit>

    @GET("libraries")
    suspend fun getLibraries(): List<PluginLibrary>

    @POST("search")
    suspend fun search(@Body body: SearchDto): SearchResults

    @GET("search/sources")
    suspend fun getSearchSources(): List<SearchSourceSummary>

    @GET("ephemeral")
    suspend fun getEphemeralSources(): List<EphemeralSource>

    @POST("ephemeral/search")
    suspend fun searchEphemeral(@Body body: EphemeralSearchDto): EphemeralSearchResults

    @PUT("tracks/{pluginId}/{libraryId}/{trackId}/save")
    suspend fun saveTrack(
        @Path("pluginId") pluginId: String,
        @Path("libraryId") libraryId: String,
        @Path("trackId") trackId: String,
    ): Response<Unit>

    @DELETE("tracks/{pluginId}/{libraryId}/{trackId}/save")
    suspend fun unsaveTrack(
        @Path("pluginId") pluginId: String,
        @Path("libraryId") libraryId: String,
        @Path("trackId") trackId: String,
    ): Response<Unit>

    @PUT("albums/{albumUuid}/save")
    suspend fun saveAlbum(@Path("albumUuid") albumUuid: String): Response<Unit>

    @DELETE("albums/{albumUuid}/save")
    suspend fun unsaveAlbum(@Path("albumUuid") albumUuid: String): Response<Unit>

    @PUT("artists/{artistUuid}/save")
    suspend fun saveArtist(@Path("artistUuid") artistUuid: String): Response<Unit>

    @DELETE("artists/{artistUuid}/save")
    suspend fun unsaveArtist(@Path("artistUuid") artistUuid: String): Response<Unit>
}
