package com.codetrio.spatialflow.ui.library.recap

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel

/**
 * RecapShareSheet delegates cleanly to RecapStoryDialog so that the complete
 * interactive recap story and share flows are presented without duplication.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapShareSheet(
    recap: PlayerSharedViewModel.ListeningRecap,
    timeRangeLabel: String,
    viewModel: PlayerSharedViewModel,
    onDismissRequest: () -> Unit
) {
    RecapStoryDialog(
        recap = recap,
        timeRangeLabel = timeRangeLabel,
        viewModel = viewModel,
        onDismissRequest = onDismissRequest
    )
}
