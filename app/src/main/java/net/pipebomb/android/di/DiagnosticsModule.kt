package net.pipebomb.android.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.pipebomb.android.core.logging.DiagnosticsFiles
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticsModule {
    @Provides
    @Singleton
    fun provideDiagnosticsFiles(@ApplicationContext context: Context): DiagnosticsFiles =
        DiagnosticsFiles.forContext(context)
}
