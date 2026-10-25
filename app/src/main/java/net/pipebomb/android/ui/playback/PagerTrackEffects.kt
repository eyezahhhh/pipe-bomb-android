package net.pipebomb.android.ui.playback

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Keeps a queue-backed pager and the playback current index in sync without feedback loops.
 *
 * - External index changes are applied only when the user is not scrolling.
 * - Page selection is emitted once per settled page, and never while a programmatic
 *   scroll or a user scroll is still in progress.
 */
@Composable
fun TrackPagerEffects(
    pagerState: PagerState,
    currentIndex: Int,
    itemCount: Int,
    onEnsureTracks: (Iterable<Int>) -> Unit,
    onSelectIndex: (Int) -> Unit,
) {
    val latestCurrentIndex by rememberUpdatedState(currentIndex)
    val latestOnSelectIndex by rememberUpdatedState(onSelectIndex)
    val latestOnEnsureTracks by rememberUpdatedState(onEnsureTracks)
    var lastSelectedPage by remember { mutableIntStateOf(currentIndex) }
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, itemCount, currentIndex) {
        if (itemCount == 0) return@LaunchedEffect
        val target = currentIndex.coerceIn(0, itemCount - 1)

        // Never fight a user gesture. If the index changes externally mid-scroll,
        // wait for the pager to settle before applying it.
        if (pagerState.isScrollInProgress) {
            snapshotFlow { pagerState.isScrollInProgress }.first { !it }
        }

        if (pagerState.currentPage != target) {
            programmaticScroll = true
            pagerState.animateScrollToPage(target)
            programmaticScroll = false
        }
        lastSelectedPage = target
    }

    LaunchedEffect(pagerState, itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (scrolling, page) ->
                if (scrolling) return@collect

                latestOnEnsureTracks((page - 2)..(page + 2))

                val target = latestCurrentIndex
                if (!programmaticScroll &&
                    page != target &&
                    page != lastSelectedPage &&
                    page in 0 until itemCount
                ) {
                    lastSelectedPage = page
                    latestOnSelectIndex(page)
                }
            }
    }
}
