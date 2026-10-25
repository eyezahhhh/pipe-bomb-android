package net.pipebomb.android.data.download

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [DownloadEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
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
    ).build()

    @Provides
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao = database.downloadDao()
}
