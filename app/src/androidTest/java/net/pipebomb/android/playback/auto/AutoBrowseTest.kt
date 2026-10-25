package net.pipebomb.android.playback.auto

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {
    @Test
    fun browsesLibraryRootAndCategories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val rootFuture = AtomicReference<ListenableFuture<LibraryResult<MediaItem>>>()
            instrumentation.runOnMainSync {
                rootFuture.set(browser.getLibraryRoot(null))
            }
            val root = rootFuture.get().get(15, TimeUnit.SECONDS)
            assertNotNull(root.value)
            assertEquals(AutoIds.ROOT, root.value?.mediaId)

            val categoriesFuture = AtomicReference<ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>>()
            instrumentation.runOnMainSync {
                categoriesFuture.set(browser.getChildren(AutoIds.ROOT, 0, 20, null))
            }
            val categories = categoriesFuture.get().get(15, TimeUnit.SECONDS)
            val ids = categories.value?.map { it.mediaId }.orEmpty()
            assertTrue(ids.contains(AutoIds.RECENT))
            assertTrue(ids.contains(AutoIds.PLAYLISTS))
            assertTrue(ids.contains(AutoIds.SAVED))
            assertTrue(ids.contains(AutoIds.HISTORY))

            val savedFuture = AtomicReference<ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>>()
            instrumentation.runOnMainSync {
                savedFuture.set(browser.getChildren(AutoIds.SAVED, 0, 20, null))
            }
            val saved = savedFuture.get().get(15, TimeUnit.SECONDS)
            val savedIds = saved.value?.map { it.mediaId }.orEmpty()
            assertTrue(savedIds.contains(AutoIds.savedTracks()))
            assertTrue(savedIds.contains(AutoIds.savedAlbums()))
            assertTrue(savedIds.contains(AutoIds.savedArtists()))
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }

    
    @Test
    fun expandsPlayableContextIntoQueue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val childrenFuture = AtomicReference<ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>>()
            instrumentation.runOnMainSync {
                childrenFuture.set(browser.getChildren(AutoIds.HISTORY, 0, 10, null))
            }
            val children = childrenFuture.get().get(15, TimeUnit.SECONDS).value.orEmpty()
            val item = children.firstOrNull { it.mediaMetadata.isPlayable == true }
            assumeNotNull("Requires an authenticated catalog with history", item)

            instrumentation.runOnMainSync {
                browser.setMediaItem(item!!)
                browser.prepare()
                browser.play()
            }

            val deadline = System.currentTimeMillis() + 30_000
            var count = 0
            while (System.currentTimeMillis() < deadline) {
                val countRef = AtomicReference(0)
                instrumentation.runOnMainSync { countRef.set(browser.mediaItemCount) }
                count = countRef.get()
                if (count > 1) break
                Thread.sleep(100)
            }
            assertTrue("Expected a full queue, got $count item(s)", count > 1)
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }
}
