package net.pipebomb.android.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.feature.player.NowPlayingSheet
import net.pipebomb.android.test.TestFixtures
import net.pipebomb.android.ui.components.TrackRow
import net.pipebomb.android.ui.playback.MiniPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ComponentInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tracks = TestFixtures.tracks()
    private val queue = tracks.map { it.key }
    private val tracksByKey = tracks.associateBy { it.key }

    @Test
    fun miniPlayerTapOpensPlayer() {
        var opened = 0
        setMiniPlayer(onOpen = { opened++ })
        composeRule.onNodeWithTag("mini-player-pager").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun miniPlayerSwipeSelectsNextTrack() {
        var selected = -1
        var selectedCount = 0
        setMiniPlayer(onSelectIndex = {
            selected = it
            selectedCount++
        })

        composeRule.onNodeWithTag("mini-player-pager").performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(2, selected)
            assertEquals(1, selectedCount)
        }
    }

    @Test
    fun miniPlayerSwipeSelectsPreviousTrack() {
        var selected = -1
        var selectedCount = 0
        setMiniPlayer(onSelectIndex = {
            selected = it
            selectedCount++
        })

        composeRule.onNodeWithTag("mini-player-pager").performTouchInput { swipeRight() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(0, selected)
            assertEquals(1, selectedCount)
        }
    }

    @Test
    fun miniPlayerExposesBufferingState() {
        setMiniPlayer(isBuffering = true)
        composeRule.onNode(hasStateDescription("Loading")).assertIsDisplayed()
    }

    @Test
    fun trackRowTapPlays() {
        var clicked = 0
        composeRule.setContent {
            PipeBombTheme {
                TrackRow(
                    track = TestFixtures.track(),
                    isCurrent = false,
                    isPlaying = false,
                    onClick = { clicked++ },
                    onPlay = {},
                    onMore = {},
                )
            }
        }

        composeRule.onNodeWithText("Midnight Drive").performClick()
        composeRule.runOnIdle { assertEquals(1, clicked) }
    }

    @Test
    fun trackRowLongPressOpensContextActions() {
        var more = 0
        composeRule.setContent {
            PipeBombTheme {
                TrackRow(
                    track = TestFixtures.track(),
                    isCurrent = false,
                    isPlaying = false,
                    onClick = {},
                    onPlay = {},
                    onMore = { more++ },
                )
            }
        }

        composeRule.onNodeWithText("Midnight Drive").performTouchInput { longClick() }
        composeRule.runOnIdle { assertTrue(more > 0) }
    }


    fun playerPagerPartialSwipeDoesNotSelect() {
        var selected = -1
        var selectedCount = 0
        composeRule.setContent {
            PipeBombTheme {
                Box(Modifier) {
                    NowPlayingSheet(
                        track = tracks[1],
                        state = TestFixtures.playback(),
                        queue = queue,
                        currentIndex = 1,
                        tracksByKey = tracksByKey,
                        onEnsureTracks = {},
                        onSelectIndex = {
                            selected = it
                            selectedCount++
                        },
                        onToggle = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                        onShuffle = {},
                        onRepeat = {},
                        onOpenQueue = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("player-pager").performTouchInput {
            down(center)
            moveBy(Offset(-80f, 0f))
            up()
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(-1, selected)
            assertEquals(0, selectedCount)
        }
    }

    @Test
    fun playerPagerSwipeSelectsAdjacentTracks() {
        var selected = -1
        var currentIndex by mutableIntStateOf(1)
        composeRule.setContent {
            PipeBombTheme {
                Box(Modifier) {
                    NowPlayingSheet(
                        track = tracks[1],
                        state = TestFixtures.playback(),
                        queue = queue,
                        currentIndex = currentIndex,
                        tracksByKey = tracksByKey,
                        onEnsureTracks = {},
                        onSelectIndex = {
                            selected = it
                            currentIndex = it
                        },
                        onToggle = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = {},
                        onShuffle = {},
                        onRepeat = {},
                        onOpenQueue = {},
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("player-pager").performTouchInput { swipeLeft() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(2, selected) }

        composeRule.onNodeWithTag("player-pager").performTouchInput { swipeRight() }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, selected) }
    }

    private fun setMiniPlayer(
        isBuffering: Boolean = false,
        onOpen: () -> Unit = {},
        onSelectIndex: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            PipeBombTheme {
                MiniPlayer(
                    queue = queue,
                    currentIndex = 1,
                    tracksByKey = tracksByKey,
                    isPlaying = true,
                    isBuffering = isBuffering,
                    onOpen = onOpen,
                    onToggle = {},
                    onSelectIndex = onSelectIndex,
                    onEnsureTracks = {},
                )
            }
        }
    }
}
