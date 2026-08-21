package org.armman.supervisor.ui.callsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.theme.RiskHighSurface

/** "Visit Due" / "Visit 3 Days to expire" / "Missed Visit" drill-down screen — same row shape,
 * discriminated at the nav level by [DueVisitKind] (see [DueVisitViewModel]). */
@Composable
fun DueVisitScreen(
  titleRes: Int,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DueVisitViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val success = uiState as? DueVisitUiState.Success
  val error = uiState as? DueVisitUiState.Error

  DrillDownScaffold(
    title = stringResource(titleRes),
    isLoading = uiState is DueVisitUiState.Loading,
    errorMessage = error?.let { it.exceptionMessage ?: stringResource(it.fallbackMessageRes) },
    onRetry = viewModel::onRetry,
    items = success?.items,
    onBack = onBack,
    modifier = modifier,
  ) { item ->
    DrillDownCard(backgroundColor = RiskHighSurface) {
      DrillDownFieldRow(stringResource(R.string.drilldown_field_beneficiary_name), item.beneficiaryName)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_village_name), item.villageName)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_unique_id), item.uniqueId)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_registration_type), stringResource(item.registrationType.labelRes()))
      DrillDownFieldRow(stringResource(R.string.drilldown_field_visit), item.visit)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_scheduled_date), item.scheduledDate)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_balanced_days), item.balancedDays.toString())
      DrillDownFieldRow(stringResource(R.string.drilldown_field_risk), item.risk)
    }
  }
}
