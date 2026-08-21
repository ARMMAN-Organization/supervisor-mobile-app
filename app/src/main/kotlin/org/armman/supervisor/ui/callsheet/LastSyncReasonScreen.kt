package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens

/** "Last Sync Date Reason" drill-down screen — a single read-only reason record for the Sakhi
 * (not a list of beneficiaries), with an "Add Reason" action when none has been recorded yet. */
@Composable
fun LastSyncReasonScreen(
  onBack: () -> Unit,
  onAddReason: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LastSyncReasonViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.call_sheet_last_sync_reason_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Dimens.ScreenPadding)) {
      when (val state = uiState) {
        is LastSyncReasonUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        is LastSyncReasonUiState.Error -> Column(
          verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
          Text(text = state.exceptionMessage ?: stringResource(state.fallbackMessageRes), style = MaterialTheme.typography.bodyLarge)
          PrimaryButton(text = stringResource(R.string.retry), onClick = viewModel::onRetry)
        }
        is LastSyncReasonUiState.Success -> if (state.reason == null) {
          Column(verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)) {
            Text(text = stringResource(R.string.call_sheet_last_sync_reason_empty), style = MaterialTheme.typography.bodyLarge)
            PrimaryButton(
              text = stringResource(R.string.drilldown_add_reason),
              onClick = onAddReason,
              containerColor = DashboardHeaderGreen,
              modifier = Modifier.fillMaxWidth(0.4f),
            )
          }
        } else {
          DrillDownCard {
            DrillDownFieldRow(stringResource(R.string.call_sheet_last_sync_reason_date), state.reason.syncDate)
            DrillDownFieldRow(stringResource(R.string.call_sheet_last_sync_reason_label), state.reason.reason)
          }
        }
      }
    }
  }
}
