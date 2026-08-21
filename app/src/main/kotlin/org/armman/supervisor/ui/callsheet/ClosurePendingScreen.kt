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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.StatusSuccess

/** "Closure Form Pending" drill-down screen; each item's "Add Reason" opens [AddReasonScreen]
 * with [ReasonContext.CLOSURE_PENDING]. */
@Composable
fun ClosurePendingScreen(
  onBack: () -> Unit,
  onAddReason: (itemId: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ClosurePendingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val success = uiState as? ClosurePendingUiState.Success
  val error = uiState as? ClosurePendingUiState.Error

  DrillDownScaffold(
    title = stringResource(R.string.call_sheet_stat_closure_form_pending),
    isLoading = uiState is ClosurePendingUiState.Loading,
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
      DrillDownFieldRow(stringResource(R.string.drilldown_field_registration_type), stringResource(item.registrationType.labelRes()))
      DrillDownFieldRow(stringResource(R.string.drilldown_field_registration_date), item.registrationDate)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_date_of_birth), item.dateOfBirth)
      DrillDownFieldRow(stringResource(R.string.drilldown_field_risk), item.risk)
      DrillDownBannerRow(
        label = stringResource(R.string.drilldown_field_overdue_days),
        value = item.overdueDays.toString(),
        backgroundColor = StatusSuccess,
      )
      Box(modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing), contentAlignment = Alignment.CenterEnd) {
        PrimaryButton(
          text = stringResource(R.string.drilldown_add_reason),
          onClick = { onAddReason(item.beneficiaryId) },
          containerColor = DashboardHeaderGreen,
          modifier = Modifier.fillMaxWidth(0.4f),
        )
      }
    }
  }
}
