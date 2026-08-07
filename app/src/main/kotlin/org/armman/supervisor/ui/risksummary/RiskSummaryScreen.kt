package org.armman.supervisor.ui.risksummary

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
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.components.StatTableCard
import org.armman.supervisor.ui.components.StatTableRow
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.DashboardPillNeutral
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.White

/** Risk Summary detail screen: location filter + one Village/Mother/Child table per Sakhi.
 * Village names are tappable, navigating to the per-village beneficiary risk detail screen. */
@Composable
fun RiskSummaryScreen(
  onBack: () -> Unit,
  onVillageSelected: (villageId: String, villageName: String, sakhiName: String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: RiskSummaryViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.risk_summary_title), onBack = onBack) },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

      when (val state = uiState) {
        is RiskSummaryUiState.Loading -> LoadingContent()
        is RiskSummaryUiState.Error -> ErrorContent(state = state, onRetry = viewModel::onRetry)
        is RiskSummaryUiState.Success -> SuccessContent(
          state = state,
          isTablet = isTablet,
          onLocationSelected = viewModel::onLocationSelected,
          onVillageSelected = onVillageSelected,
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
private fun ErrorContent(state: RiskSummaryUiState.Error, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
      style = MaterialTheme.typography.bodyLarge,
    )
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(
  state: RiskSummaryUiState.Success,
  isTablet: Boolean,
  onLocationSelected: (String) -> Unit,
  onVillageSelected: (villageId: String, villageName: String, sakhiName: String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(Dimens.StatCardScreenPadding),
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
        Text(text = stringResource(R.string.risk_summary_empty), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      state.sakhis.forEach { sakhi ->
        StatTableCard(
          title = sakhi.sakhiName,
          columnHeaderLabel = stringResource(R.string.table_header_village),
          columnHeaderA = stringResource(R.string.table_header_mother),
          columnHeaderB = stringResource(R.string.table_header_child),
          rows = sakhi.villages.map { StatTableRow(it.villageName, it.motherCount, it.childCount) },
          headerBackgroundColor = DashboardHeaderGreen,
          headerTextColor = White,
          alternateRowColor = NeutralG50,
          textColor = NeutralG400,
          badgeText = stringResource(R.string.current_month_label),
          badgeBackgroundColor = DashboardPillNeutral,
          badgeTextColor = NeutralG400,
          onRowClick = { row -> onVillageSelected(row.label, row.label, sakhi.sakhiName) },
        )
      }
    }
  }
}

private const val TABLET_FILTER_WIDTH_FRACTION = 0.5f
