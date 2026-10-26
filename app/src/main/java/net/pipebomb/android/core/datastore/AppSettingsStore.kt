package net.pipebomb.android.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class ServerConfig(
    val serverUrl: String?,
    val isReady: Boolean,
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    val serverConfig: Flow<ServerConfig> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            ServerConfig(
                serverUrl = prefs[Keys.ServerUrl],
                isReady = prefs[Keys.ServerReady] ?: false,
            )
        }

    val downloadReserveBytes: Flow<Long> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[Keys.DownloadReserveBytes] ?: DEFAULT_DOWNLOAD_RESERVE_BYTES }

    val allowMobileDataDownloads: Flow<Boolean> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[Keys.AllowMobileDataDownloads] ?: false }

    suspend fun setServerUrl(url: String?) {
        dataStore.edit { prefs ->
            if (url == null) {
                prefs.remove(Keys.ServerUrl)
            } else {
                prefs[Keys.ServerUrl] = url
            }
            prefs[Keys.ServerReady] = false
        }
    }

    suspend fun setServerReady(ready: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ServerReady] = ready
        }
    }

    suspend fun clearServer() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ServerUrl)
            prefs.remove(Keys.ServerReady)
        }
    }

    suspend fun setDownloadReserveBytes(bytes: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.DownloadReserveBytes] = bytes.coerceAtLeast(0L)
        }
    }

    suspend fun setAllowMobileDataDownloads(allow: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AllowMobileDataDownloads] = allow
        }
    }

    private object Keys {
        val ServerUrl = stringPreferencesKey("server_url")
        val ServerReady = booleanPreferencesKey("server_ready")
        val DownloadReserveBytes = longPreferencesKey("download_reserve_bytes")
        val AllowMobileDataDownloads = booleanPreferencesKey("allow_mobile_data_downloads")
    }

    companion object {
        const val DEFAULT_DOWNLOAD_RESERVE_BYTES = 1024L * 1024L * 1024L
    }
}
