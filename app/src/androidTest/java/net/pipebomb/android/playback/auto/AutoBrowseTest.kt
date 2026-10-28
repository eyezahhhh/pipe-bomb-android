package net.pipebomb.android.playback.auto

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.runBlocking
import net.pipebomb.android.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class AutoBrowseTest {
    @Test
    fun exposesSaveShuffleAndRepeatCommands() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val deadline = System.currentTimeMillis() + 5_000
            var actions = emptyList<String>()
            while (System.currentTimeMillis() < deadline) {
                val ref = AtomicReference<List<String>>(emptyList())
                instrumentation.runOnMainSync {
                    ref.set(browser.mediaButtonPreferences.mapNotNull { it.sessionCommand?.customAction })
                }
                actions = ref.get()
                if (actions.containsAll(EXPECTED_ACTIONS)) break
                Thread.sleep(50)
            }
            assertTrue("Missing custom actions: $actions", actions.containsAll(EXPECTED_ACTIONS))
            val saveRef = AtomicReference<CommandButton?>(null)
            instrumentation.runOnMainSync {
                saveRef.set(
                    browser.mediaButtonPreferences.firstOrNull {
                        it.sessionCommand?.customAction ==
                            net.pipebomb.android.playback.PlaybackSessionActions.COMMAND_SAVE
                    },
                )
            }
            val saveButton = saveRef.get()
            assertTrue("Save button is missing", saveButton != null)
            assertTrue(
                "Save must not occupy the previous/next slots",
                (0 until saveButton!!.slots.length()).none { index ->
                    val slot = saveButton.slots.get(index)
                    slot == CommandButton.SLOT_BACK || slot == CommandButton.SLOT_FORWARD
                },
            )
            for (command in EXPECTED_ACTIONS) {
                val resultFuture = AtomicReference<ListenableFuture<SessionResult>>()
                instrumentation.runOnMainSync {
                    resultFuture.set(
                        browser.sendCustomCommand(
                            SessionCommand(command, Bundle.EMPTY),
                            Bundle.EMPTY,
                        ),
                    )
                }
                val result = resultFuture.get().get(5, TimeUnit.SECONDS)
                assertEquals(
                    "Command $command was rejected",
                    SessionResult.RESULT_SUCCESS,
                    result.resultCode,
                )
            }
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }

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
            if (root.value == null) {
                fail("root resultCode=${root.resultCode}")
            }
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
                childrenFuture.set(browser.getChildren(AutoIds.RECENT, 0, 10, null))
            }
            val children = childrenFuture.get().get(15, TimeUnit.SECONDS).value.orEmpty()
            val trackItems = children.filter { it.mediaId.startsWith("pb:track:") }
            assumeTrue(
                "Requires authenticated recent history with at least two tracks",
                trackItems.size > 1,
            )
            val item = trackItems.firstOrNull()
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

    @Test
    fun playlistSelectionBuildsFullQueueAndEnablesNext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val browser = MediaBrowser.Builder(context, token).buildAsync().get(15, TimeUnit.SECONDS)
        try {
            val playlistsFuture = AtomicReference<ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>>()
            instrumentation.runOnMainSync {
                playlistsFuture.set(browser.getChildren(AutoIds.PLAYLISTS, 0, 20, null))
            }
            val playlists = playlistsFuture.get().get(15, TimeUnit.SECONDS).value.orEmpty()
            var selected: MediaItem? = null
            var selectedAction: MediaItem? = null
            for (playlist in playlists) {
                val tracksFuture = AtomicReference<ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>>()
                instrumentation.runOnMainSync {
                    tracksFuture.set(browser.getChildren(playlist.mediaId, 0, 5, null))
                }
                val tracks = tracksFuture.get().get(15, TimeUnit.SECONDS).value.orEmpty()
                val playlistNode = AutoIds.parse(playlist.mediaId) as? AutoNode.Playlist
                if (playlistNode != null && tracks.count { it.mediaId.startsWith("pb:track:") } > 1) {
                    val context = AutoContext.Playlist(playlistNode.uuid)
                    assertEquals(AutoIds.action(context, shuffle = false), tracks[0].mediaId)
                    assertEquals(AutoIds.action(context, shuffle = true), tracks[1].mediaId)
                    selected = playlist
                    selectedAction = tracks[0]
                    break
                }
            }
            assumeNotNull("Requires an authenticated playlist with at least two tracks", selected)
            assumeNotNull("Playlist play action is missing", selectedAction)
            assertTrue("Playlist should open instead of playing", selected!!.mediaMetadata.isBrowsable == true)
            assertEquals(false, selected!!.mediaMetadata.isPlayable)

            instrumentation.runOnMainSync {
                browser.setMediaItem(selectedAction!!)
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
            assertTrue("Expected the full playlist queue, got $count item(s)", count > 1)
            val syntheticRef = AtomicReference(false)
            instrumentation.runOnMainSync {
                syntheticRef.set(
                    (0 until browser.mediaItemCount).all { index ->
                        browser.getMediaItemAt(index).localConfiguration?.uri?.scheme == "pipebomb"
                    },
                )
            }
            assertTrue("Expected lazy synthetic queue items", syntheticRef.get())
            val nextRef = AtomicReference(false)
            instrumentation.runOnMainSync {
                nextRef.set(browser.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            }
            assertTrue("Expected Next to be available", nextRef.get())
        } finally {
            instrumentation.runOnMainSync { browser.release() }
        }
    }

    private companion object {
        val EXPECTED_ACTIONS = setOf(
            net.pipebomb.android.playback.PlaybackSessionActions.COMMAND_SAVE,
            net.pipebomb.android.playback.PlaybackSessionActions.COMMAND_SHUFFLE,
            net.pipebomb.android.playback.PlaybackSessionActions.COMMAND_REPEAT,
        )
    }
}
