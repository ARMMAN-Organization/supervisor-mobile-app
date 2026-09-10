package org.armman.supervisor.ui.dashboard

import android.content.Context
import android.media.RingtoneManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.navigation.Routes
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White

/** Supervisor landing screen: header/KPIs, quick actions, summary cards and the stale-Sakhi alert.
 * While this screen is visible, [DashboardViewModel] polls for new notifications (see
 * [DashboardViewModel.startPolling]) and this screen plays the device's default notification
 * sound for each one detected (see [DashboardViewModel.newNotificationEvents]) — there is no
 * separate popup/banner UI; the bell icon's badge and the Notifications screen's list are the
 * only visible surfaces for notification content. */
@Composable
fun DashboardScreen(
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: DashboardViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LifecycleResumeEffect(Unit) {
    viewModel.startPolling()
    onPauseOrDispose { viewModel.stopPolling() }
  }

  LaunchedEffect(Unit) {
    viewModel.newNotificationEvents.collect { playNotificationSound(context) }
  }

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

private fun playNotificationSound(context: Context) {
  runCatching {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    RingtoneManager.getRingtone(context, uri)?.play()
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
    Triple(stringResource(R.string.monitoring_summary_title), data.monitoringSummary, null),
  )

  cards.forEach { (title, rows, route) ->
    DashboardSummaryCard(
      title = title,
      rows = rows,
      onClick = route?.let { { onNavigate(it) } },
      modifier = Modifier.padding(horizontal = Dimens.StatCardScreenPadding, vertical = Dimens.SmallSpacing),
    )
  }
}
