package org.armman.supervisor.ui.registrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import org.armman.supervisor.ui.components.StatTable
import org.armman.supervisor.ui.components.StatTableRow
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.DashboardKpiGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.NeutralG50
import org.armman.supervisor.ui.theme.RiskModerate
import org.armman.supervisor.ui.theme.SerifTitle
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** Registrations detail screen: location filter + one card per Sakhi showing her registration
 * badge count, mother/child targets, and a Village/Mother/Child table. */
@Composable
fun RegistrationsScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: RegistrationsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.registrations_title), onBack = onBack) },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp

      when (val state = uiState) {
        is RegistrationsUiState.Loading -> LoadingContent()
        is RegistrationsUiState.Error -> ErrorContent(onRetry = viewModel::onRetry)
        is RegistrationsUiState.Success -> SuccessContent(
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
  state: RegistrationsUiState.Success,
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
        Text(text = stringResource(R.string.registrations_empty), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      state.sakhis.forEach { sakhi -> SakhiRegistrationCard(sakhi) }
    }
  }
}

@Composable
private fun SakhiRegistrationCard(sakhi: SakhiRegistrationSummary) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = sakhi.sakhiName, style = SerifTitle, color = NeutralG400, modifier = Modifier.weight(1f))
        Icon(
          imageVector = Icons.Filled.Groups,
          contentDescription = stringResource(R.string.cd_registration_badge_icon),
          tint = DashboardKpiGreen,
          modifier = Modifier.size(Dimens.RegistrationBadgeIconSize),
        )
        Text(
          text = "${sakhi.badgeCount}",
          style = MaterialTheme.typography.titleMedium,
          color = DashboardKpiGreen,
          modifier = Modifier.padding(start = Dimens.ExtraSmallSpacing),
        )
      }
      TargetRow(
        label = stringResource(R.string.registrations_mother_target_label),
        value = sakhi.motherTarget,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
      TargetRow(
        label = stringResource(R.string.registrations_child_target_label),
        value = sakhi.childTarget,
        modifier = Modifier.padding(top = Dimens.ExtraSmallSpacing),
      )
      StatTable(
        columnHeaderLabel = stringResource(R.string.table_header_village),
        columnHeaderA = stringResource(R.string.table_header_mother),
        columnHeaderB = stringResource(R.string.table_header_child),
        rows = sakhi.villages.map { StatTableRow(it.villageName, it.motherCount, it.childCount) },
        headerBackgroundColor = DashboardHeaderGreen,
        headerTextColor = White,
        alternateRowColor = NeutralG50,
        textColor = NeutralG400,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
    }
  }
}

@Composable
private fun TargetRow(label: String, value: Int, modifier: Modifier = Modifier) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = NeutralG400, modifier = Modifier.weight(1f))
    Text(text = "$value", style = MaterialTheme.typography.bodyMedium, color = RiskModerate)
  }
}

private const val TABLET_FILTER_WIDTH_FRACTION = 0.5f
