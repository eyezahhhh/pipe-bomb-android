package net.pipebomb.android.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.feature.player.NowPlayingSheet
import net.pipebomb.android.test.TestFixtures
import net.pipebomb.android.ui.catalog.DesignCatalog
import net.pipebomb.android.ui.components.TrackRow
import net.pipebomb.android.ui.playback.MiniPlayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class ComponentScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun componentCanvas(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            PipeBombTheme {
                Box(
                    modifier = Modifier
                        .size(width = 412.dp, height = 104.dp)
                        .background(PipeBombTheme.colors.bgBase)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }

    @Test
    fun trackRow() {
        componentCanvas {
            TrackRow(
                track = TestFixtures.track(),
                isCurrent = false,
                isPlaying = false,
                onClick = {},
                onPlay = {},
                onMore = {},
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/track-row.png")
    }

    @Test
    fun trackRowCurrent() {
        componentCanvas {
            TrackRow(
                track = TestFixtures.track(),
                isCurrent = true,
                isPlaying = true,
                onClick = {},
                onPlay = {},
                onMore = {},
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/track-row-current.png")
    }

    @Test
    fun miniPlayer() {
        val tracks = TestFixtures.tracks()
        componentCanvas {
            MiniPlayer(
                queue = tracks.map { it.key },
                currentIndex = 1,
                tracksByKey = tracks.associateBy { it.key },
                isPlaying = true,
                isBuffering = false,
                onOpen = {},
                onToggle = {},
                onSelectIndex = {},
                onEnsureTracks = {},
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/mini-player.png")
    }

    @Test
    fun miniPlayerBuffering() {
        val tracks = TestFixtures.tracks()
        componentCanvas {
            MiniPlayer(
                queue = tracks.map { it.key },
                currentIndex = 1,
                tracksByKey = tracks.associateBy { it.key },
                isPlaying = true,
                isBuffering = true,
                onOpen = {},
                onToggle = {},
                onSelectIndex = {},
                onEnsureTracks = {},
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/mini-player-buffering.png")
    }

    @Test
    fun designTokens() {
        composeRule.setContent {
            PipeBombTheme { DesignCatalog() }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/design-tokens.png")
    }

    @Test
    fun nowPlaying() {
        val tracks = TestFixtures.tracks()
        composeRule.setContent {
            PipeBombTheme {
                NowPlayingSheet(
                    track = tracks[1],
                    state = TestFixtures.playback(),
                    queue = tracks.map { it.key },
                    currentIndex = 1,
                    tracksByKey = tracks.associateBy { it.key },
                    onEnsureTracks = {},
                    onSelectIndex = {},
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
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/now-playing.png")
    }

    @Test
    fun nowPlayingBuffering() {
        val tracks = TestFixtures.tracks()
        composeRule.setContent {
            PipeBombTheme {
                NowPlayingSheet(
                    track = tracks[1],
                    state = TestFixtures.playback(isBuffering = true),
                    queue = tracks.map { it.key },
                    currentIndex = 1,
                    tracksByKey = tracks.associateBy { it.key },
                    onEnsureTracks = {},
                    onSelectIndex = {},
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
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/now-playing-buffering.png")
    }
}
