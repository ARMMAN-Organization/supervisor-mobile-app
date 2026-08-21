package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.RiskHighSurface

/** "Followup Form Pending" drill-down screen; each item's "Add Reason" opens [AddReasonScreen]
 * with [ReasonContext.FOLLOWUP_PENDING], keyed by the real `call_logs.call_log_id`. */
@Composable
fun FollowupPendingScreen(
  onBack: () -> Unit,
  onAddReason: (callLogId: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: FollowupPendingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val success = uiState as? FollowupPendingUiState.Success
  val error = uiState as? FollowupPendingUiState.Error

  // Re-fetch whenever this screen resumes (e.g. returning from Add Reason after submitting one).
  LifecycleResumeEffect(Unit) {
    viewModel.onResumed()
    onPauseOrDispose { }
  }

  DrillDownScaffold(
    title = stringResource(R.string.call_sheet_stat_followup_pending),
    isLoading = uiState is FollowupPendingUiState.Loading,
    errorMessage = error?.let { it.exceptionMessage ?: stringResource(it.fallbackMessageRes) },
    onRetry = viewModel::onRetry,
    items = success?.items,
    onBack = onBack,
    modifier = modifier,
  ) { item ->
    DrillDownCard(backgroundColor = RiskHighSurface) {
      DrillDownFieldRow(stringResource(R.string.drilldown_field_call_date), item.callDate)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_notes), item.notes ?: "—")
      Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing), contentAlignment = Alignment.CenterEnd) {
        PrimaryButton(
          text = stringResource(R.string.drilldown_add_reason),
          onClick = { onAddReason(item.callLogId) },
          containerColor = DashboardHeaderGreen,
          modifier = Modifier.fillMaxWidth(0.4f),
        )
      }
    }
  }
}
