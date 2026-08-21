package org.armman.supervisor.ui.callsheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R

/** "High Risk ANC"/"High Risk PNC" drill-down screen — read-only, no Add Reason. */
@Composable
fun HighRiskListScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: HighRiskListViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val success = uiState as? HighRiskListUiState.Success
  val error = uiState as? HighRiskListUiState.Error
  val titleRes = when (viewModel.riskType) {
    HighRiskType.ANC -> R.string.call_sheet_stat_high_risk_anc
    HighRiskType.PNC -> R.string.call_sheet_stat_high_risk_pnc
  }

  DrillDownScaffold(
    title = stringResource(titleRes),
    isLoading = uiState is HighRiskListUiState.Loading,
    errorMessage = error?.let { it.exceptionMessage ?: stringResource(it.fallbackMessageRes) },
    onRetry = viewModel::onRetry,
    items = success?.items,
    onBack = onBack,
    modifier = modifier,
  ) { item ->
    DrillDownCard {
      DrillDownFieldRow(stringResource(R.string.drilldown_field_beneficiary_name), item.beneficiaryName)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_village_name), item.villageName)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_unique_id), item.uniqueId)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_risk_name), item.riskName)
    }
  }
}
