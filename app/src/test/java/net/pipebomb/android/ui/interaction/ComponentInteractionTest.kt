package net.pipebomb.android.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
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

    @Test
    fun miniPlayerTapOpensPlayer() {
        var opened = 0
        composeRule.setContent {
            PipeBombTheme {
                MiniPlayer(
                    track = TestFixtures.track(),
                    isPlaying = true,
                    isBuffering = false,
                    onOpen = { opened++ },
                    onToggle = {},
                    onNext = {},
                    onPrevious = {},
                )
            }
        }

        composeRule.onNodeWithTag("mini-player").performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun miniPlayerSwipesChangeTracks() {
        var next = 0
        var previous = 0
        composeRule.setContent {
            PipeBombTheme {
                MiniPlayer(
                    track = TestFixtures.track(),
                    isPlaying = true,
                    isBuffering = false,
                    onOpen = {},
                    onToggle = {},
                    onNext = { next++ },
                    onPrevious = { previous++ },
                )
            }
        }

        composeRule.onNodeWithTag("mini-player").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, next) }

        composeRule.onNodeWithTag("mini-player").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, previous) }
    }

    @Test
    fun miniPlayerExposesBufferingState() {
        composeRule.setContent {
            PipeBombTheme {
                MiniPlayer(
                    track = TestFixtures.track(),
                    isPlaying = true,
                    isBuffering = true,
                    onOpen = {},
                    onToggle = {},
                    onNext = {},
                    onPrevious = {},
                )
            }
        }

        composeRule.onNode(hasStateDescription("Loading")).assertIsDisplayed()
    }

    
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

    @Test
    fun playerArtworkSwipesChangeTracks() {
        var next = 0
        var previous = 0
        composeRule.setContent {
            PipeBombTheme {
                Box(Modifier) {
                    NowPlayingSheet(
                        track = TestFixtures.track(),
                        state = TestFixtures.playback(),
                        onToggle = {},
                        onNext = { next++ },
                        onPrevious = {},
                        onSwipePrevious = { previous++ },
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

        composeRule.onNodeWithTag("player-artwork").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, next) }

        composeRule.onNodeWithTag("player-artwork").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, previous) }
    }
}
