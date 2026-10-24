package net.pipebomb.android.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleArtistAndHandlesTap() {
        var clicked = false
        composeRule.setContent {
            PipeBombTheme {
                TrackRow(
                    track = Track(
                        trackId = "t1",
                        pluginId = "plugin",
                        libraryId = "library",
                        title = "Test Song",
                    ),
                    isCurrent = false,
                    isPlaying = false,
                    onClick = { clicked = true },
                    onPlay = {},
                    onMore = {},
                )
            }
        }

        composeRule.onNodeWithText("Test Song").assertIsDisplayed()
        composeRule.onNodeWithText("Unknown Artist").assertIsDisplayed()
        composeRule.onNodeWithText("Test Song").performClick()
        assertTrue(clicked)
    }
}
