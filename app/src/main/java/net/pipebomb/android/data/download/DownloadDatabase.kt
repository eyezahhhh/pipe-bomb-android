package net.pipebomb.android.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        DownloadEntity::class,
        DownloadCollectionEntity::class,
        DownloadCollectionTrackEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE downloads ADD COLUMN manual INTEGER NOT NULL DEFAULT 1")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS download_collections (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                title TEXT NOT NULL,
                artworkUrl TEXT,
                ownerName TEXT,
                accountUuid TEXT,
                accountName TEXT,
                remoteRevision TEXT,
                trackCount INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                lastSyncedAt INTEGER,
                lastError TEXT
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS download_collection_tracks (
                collectionId TEXT NOT NULL,
                trackKey TEXT NOT NULL,
                position INTEGER NOT NULL,
                addedAt INTEGER NOT NULL,
                PRIMARY KEY(collectionId, trackKey)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_download_collection_tracks_trackKey " +
                "ON download_collection_tracks(trackKey)",
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_download_collection_tracks_collectionId " +
                "ON download_collection_tracks(collectionId)",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DownloadDatabaseModule {
    @Provides
    @Singleton
    fun provideDownloadDatabase(
        @ApplicationContext context: Context,
    ): DownloadDatabase = Room.databaseBuilder(
        context,
        DownloadDatabase::class.java,
        "downloads.db",
    )
        .addMigrations(MIGRATION_1_2)
        .build()

    @Provides
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao = database.downloadDao()
}
