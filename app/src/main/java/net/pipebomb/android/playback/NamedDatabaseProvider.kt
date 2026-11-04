package net.pipebomb.android.playback

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider

/**
 * A [DatabaseProvider] with a dedicated database file.
 *
 * Media3's [androidx.media3.database.StandaloneDatabaseProvider] hardcodes
 * `exoplayer_internal.db`. The streamed cache and the download cache must have independent
 * indices, so the streamed cache gets its own database.
 */
@OptIn(UnstableApi::class)
class NamedDatabaseProvider(
    context: Context,
    name: String,
) : SQLiteOpenHelper(context.applicationContext, name, null, VERSION), DatabaseProvider {
    override fun onCreate(db: SQLiteDatabase) = Unit

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private companion object {
        const val VERSION = 1
    }
}
