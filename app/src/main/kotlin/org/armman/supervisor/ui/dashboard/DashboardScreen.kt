package org.armman.supervisor.ui.dashboard

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.navigation.Routes
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White

/** Supervisor landing screen: header/KPIs, quick actions, summary cards and the stale-Sakhi alert. */
@Composable
fun DashboardScreen(
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
    val isLandscape = maxWidth > maxHeight

    when (val state = uiState) {
      is DashboardUiState.Loading -> LoadingContent()
      is DashboardUiState.Error -> ErrorContent(onRetry = viewModel::onRetry)
      is DashboardUiState.Success -> SuccessContent(
        state = state,
        isTablet = isTablet,
        isLandscape = isLandscape,
        onNavigate = onNavigate,
        onLocationSelected = viewModel::onLocationSelected,
      )
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
  state: DashboardUiState.Success,
  isTablet: Boolean,
  isLandscape: Boolean,
  onNavigate: (String) -> Unit,
  onLocationSelected: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    DashboardHeaderSection(
      data = state.data,
      locations = state.locations,
      selectedLocationId = state.selectedLocationId,
      onLocationSelected = onLocationSelected,
      onProfileClick = { onNavigate(Routes.PROFILE) },
      onNotificationsClick = { onNavigate(Routes.NOTIFICATIONS) },
      onSettingsClick = { onNavigate(Routes.SETTINGS) },
      isTablet = isTablet,
      isLandscape = isLandscape,
    )
    Surface(
      color = White,
      modifier = Modifier.fillMaxSize(),
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Dimens.ScreenPadding),
        ) {
          DashboardQuickActions(
            onNavigate = onNavigate,
            isTablet = isTablet,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
          )
          SummarySections(data = state.data, onNavigate = onNavigate)
          DashboardStaleSakhiCard(
            entries = state.data.staleSakhis,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
          )
        }
        if (state.isRefreshing) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun SummarySections(data: DashboardData, onNavigate: (String) -> Unit) {
  val cards = listOf(
    Triple(stringResource(R.string.visit_summary_title), data.visitSummary, Routes.VISIT_SUMMARY),
    Triple(stringResource(R.string.registration_summary_title), data.registrationSummary, Routes.REGISTRATIONS),
    Triple(stringResource(R.string.risk_summary_title), data.riskSummary, Routes.RISK_SUMMARY),
    Triple(stringResource(R.string.monitoring_summary_title), data.monitoringSummary, Routes.MONITORING_SUMMARY),
  )

  cards.forEach { (title, rows, route) ->
    DashboardSummaryCard(
      title = title,
      rows = rows,
      onClick = { onNavigate(route) },
      modifier = Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
    )
  }
}
