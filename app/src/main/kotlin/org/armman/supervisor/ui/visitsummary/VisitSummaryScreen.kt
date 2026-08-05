package org.armman.supervisor.ui.visitsummary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.MultiColumnStatRow
import org.armman.supervisor.ui.components.MultiColumnStatTableCard
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.DashboardPillNeutral
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.White

/** Visit Summary detail screen: location filter + one Village/Total/Due/Missed table per Sakhi. */
@Composable
fun VisitSummaryScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: VisitSummaryViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.visit_summary_title), onBack = onBack) },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

      when (val state = uiState) {
        is VisitSummaryUiState.Loading -> LoadingContent()
        is VisitSummaryUiState.Error -> ErrorContent(onRetry = viewModel::onRetry)
        is VisitSummaryUiState.Success -> SuccessContent(
          state = state,
          isTablet = isTablet,
          onLocationSelected = viewModel::onLocationSelected,
        )
      }
    }
  }
}

@Composable
private fun LoadingContent() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ErrorContent(onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = stringResource(R.string.dashboard_error_generic), style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(
  state: VisitSummaryUiState.Success,
  isTablet: Boolean,
  onLocationSelected: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
  ) {
    SingleSelectDropdown(
      options = state.locations,
      selected = state.locations.find { it.id == state.selectedLocationId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.location_selector_placeholder),
      onSelected = { onLocationSelected(it.id) },
      modifier = if (isTablet) Modifier.fillMaxWidth(TABLET_FILTER_WIDTH_FRACTION) else Modifier.fillMaxWidth(),
    )
    if (state.sakhis.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.visit_summary_empty), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      state.sakhis.forEach { sakhi ->
        MultiColumnStatTableCard(
          title = sakhi.sakhiName,
          columnHeaderLabel = stringResource(R.string.table_header_village),
          columnHeaders = listOf(
            stringResource(R.string.table_header_total),
            stringResource(R.string.table_header_due),
            stringResource(R.string.table_header_missed),
          ),
          rows = sakhi.villages.map { MultiColumnStatRow(it.villageName, listOf(it.total, it.due, it.missed)) },
          headerBackgroundColor = DashboardHeaderGreen,
          headerTextColor = White,
          alternateRowColor = NeutralG50,
          textColor = NeutralG400,
          badgeText = stringResource(R.string.current_month_label),
          badgeBackgroundColor = DashboardPillNeutral,
          badgeTextColor = NeutralG400,
        )
      }
    }
  }
}

private const val TABLET_FILTER_WIDTH_FRACTION = 0.5f
