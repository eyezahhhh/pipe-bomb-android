package net.pipebomb.android.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageLocationLabelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.getSharedPreferences("storage_bootstrap", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
    }

    @Test
    fun unavailableNonInternalSelectionDoesNotClaimInternalStorage() {
        prefs.edit().putString("selected_location", "external:missing").commit()
        val repository = repository()

        assertEquals("Selected storage (unavailable)", repository.selectedLocationLabel())
    }

    @Test
    fun unavailableSelectionUsesStoredLabel() {
        prefs.edit()
            .putString("selected_location", "external:missing")
            .putString("selected_label", "My SD card")
            .commit()
        val repository = repository()

        assertEquals("My SD card", repository.selectedLocationLabel())
    }

    @Test
    fun internalSelectionStillReportsInternalStorage() {
        prefs.edit().putString("selected_location", StorageLocationRepository.INTERNAL_ID).commit()
        val repository = repository()

        assertEquals("Internal storage", repository.selectedLocationLabel())
    }

    private fun repository() = StorageLocationRepository(
        context = context,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )
}
