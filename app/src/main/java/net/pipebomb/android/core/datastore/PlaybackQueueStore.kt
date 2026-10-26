package net.pipebomb.android.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import net.pipebomb.android.core.model.TrackIdDto
import net.pipebomb.android.core.net.PipeBombJson
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback")

@Serializable
data class PlaybackSnapshot(
    val queue: List<TrackIdDto> = emptyList(),
    val originalQueue: List<TrackIdDto> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "Off",
)

@Singleton
class PlaybackQueueStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val dataStore = context.playbackDataStore

    val snapshot: Flow<PlaybackSnapshot?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            prefs[Keys.Snapshot]?.let { raw ->
                runCatching {
                    PipeBombJson.decodeFromString<PlaybackSnapshot>(raw)
                }.getOrNull()
            }
        }

    suspend fun save(snapshot: PlaybackSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.Snapshot] = PipeBombJson.encodeToString(snapshot)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(Keys.Snapshot) }
    }

    private object Keys {
        val Snapshot = stringPreferencesKey("snapshot")
    }
}
