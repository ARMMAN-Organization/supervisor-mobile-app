package org.armman.supervisor.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.Dimens

/** Notifications list screen: in-app notifications for the Supervisor, backed by
 * `GET /notifications`. Tapping a notification marks it read (if unread) via
 * `PATCH /notifications/{id}`, and — for notification types with a known destination screen
 * (see [resolveNavigationTarget]) — navigates there via [onNavigate], whether or not it was
 * already read. Types with no destination screen yet just mark read, no navigation. */
@Composable
fun NotificationsScreen(
  onBack: () -> Unit,
  onNavigate: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: NotificationsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.notifications_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is NotificationsUiState.Loading -> LoadingContent()
        is NotificationsUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is NotificationsUiState.Success -> SuccessContent(
          state = state,
          onNotificationClick = { notification ->
            viewModel.onNotificationClick(notification.id)
            notification.resolveNavigationTarget()?.let { target -> onNavigate(target.toRoute()) }
          },
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
private fun ErrorContent(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = message, style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(text = stringResource(R.string.retry), onClick = onRetry)
  }
}

@Composable
private fun SuccessContent(
  state: NotificationsUiState.Success,
  onNotificationClick: (AppNotification) -> Unit,
) {
  if (state.notifications.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text = stringResource(R.string.notifications_empty), style = MaterialTheme.typography.bodyLarge)
    }
    return
  }
  BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
    LazyColumn(
      verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
      modifier = Modifier
        .fillMaxSize()
        .then(if (isTablet) Modifier.widthIn(max = Dimens.ContentMaxWidthTablet) else Modifier)
        .padding(Dimens.ScreenPadding),
    ) {
      items(state.notifications, key = { it.id }) { notification ->
        NotificationCard(
          notification = notification,
          onClick = { onNotificationClick(notification) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
