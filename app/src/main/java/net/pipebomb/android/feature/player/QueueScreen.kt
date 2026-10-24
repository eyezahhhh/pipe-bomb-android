package net.pipebomb.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant

@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Up Next",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
            )
            PipeBombButton(
                text = "Clear",
                onClick = viewModel::clear,
                variant = PipeBombButtonVariant.Ghost,
                enabled = state.queue.isNotEmpty(),
            )
        }
        if (state.queue.isEmpty()) {
            Text(
                text = "The queue is empty",
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.fgTertiary,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
                itemsIndexed(state.queue, key = { index, key -> "$index:$key" }) { index, key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PipeBombTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = key.trackId,
                            style = PipeBombTheme.typography.bodySecondary,
                            color = if (index == state.currentIndex) {
                                PipeBombTheme.colors.accentPrimary
                            } else {
                                PipeBombTheme.colors.fgSecondary
                            },
                        )
                        PipeBombButton(
                            text = "Remove",
                            onClick = { viewModel.remove(index) },
                            variant = PipeBombButtonVariant.Ghost,
                        )
                    }
                }
            }
        }
    }
}
