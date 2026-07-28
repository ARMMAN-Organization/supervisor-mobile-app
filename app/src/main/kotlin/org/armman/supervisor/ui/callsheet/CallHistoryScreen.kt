package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Call History (timeline) screen for one Sakhi, with a FAB to log a new call attempt. */
@Composable
fun CallHistoryScreen(
  onBack: () -> Unit,
  onCallClick: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallHistoryViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Re-fetch whenever this screen resumes (e.g. returning from Call Outcome after a submit).
  LifecycleResumeEffect(Unit) {
    viewModel.onResumed()
    onPauseOrDispose { }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      val title = (uiState as? CallHistoryUiState.Success)?.sakhi?.name.orEmpty()
      BrandTopAppBar(title = title, onBack = onBack)
    },
    floatingActionButton = {
      FloatingActionButton(onClick = onCallClick, containerColor = DashboardHeaderGreen) {
        Icon(imageVector = Icons.Filled.Call, contentDescription = stringResource(R.string.cd_call_sakhi), tint = White)
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is CallHistoryUiState.Loading -> LoadingContent()
        is CallHistoryUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is CallHistoryUiState.Success -> SuccessContent(entries = state.entries)
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
private fun SuccessContent(entries: List<CallLogEntry>) {
  if (entries.isEmpty()) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text = stringResource(R.string.call_history_empty), style = MaterialTheme.typography.bodyLarge)
    }
    return
  }
  LazyColumn(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    itemsIndexed(entries) { index, entry -> CallTimelineEntryRow(entry, isLast = index == entries.lastIndex) }
  }
}

@Composable
private fun CallTimelineEntryRow(entry: CallLogEntry, isLast: Boolean) {
  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    Box(modifier = Modifier.width(Dimens.ItemSpacing).fillMaxHeight()) {
      if (!isLast) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .width(Dimens.HairlineWidth)
            .fillMaxHeight()
            .background(DashboardHeaderGreen),
        )
      }
      Box(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .size(Dimens.SmallSpacing + Dimens.TinySpacing)
          .background(color = DashboardHeaderGreen, shape = CircleShape),
      )
    }
    Surface(
      color = White,
      shape = RoundedCornerShape(Dimens.CardRadius),
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Dimens.SmallSpacing, bottom = Dimens.ItemSpacing)
        .softShadow(Dimens.CardRadius),
    ) {
      Column(modifier = Modifier.padding(Dimens.TilePadding)) {
        Text(
          text = entry.timestampEpochMillis.toDisplayDate(),
          style = MaterialTheme.typography.bodyMedium,
          color = DashboardHeaderGreen,
        )
        Text(
          text = stringResource(entry.outcomeLabelRes()),
          style = MaterialTheme.typography.titleMedium,
          color = NeutralG400,
        )
        entry.responder?.let { responder ->
          Text(
            text = stringResource(R.string.call_history_responder_label, stringResource(responder.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            color = NeutralG400,
          )
        }
        entry.notes?.let { notes ->
          Text(text = notes, style = MaterialTheme.typography.bodyMedium, color = NeutralG400)
        }
        entry.followUpAction?.let { action ->
          Text(text = action, style = MaterialTheme.typography.bodyMedium, color = NeutralG400)
        }
      }
    }
  }
}

private fun CallLogEntry.outcomeLabelRes(): Int = when (connected) {
  CallConnected.YES -> checkNotNull(successOutcome).labelRes()
  CallConnected.NO -> checkNotNull(failureReason).labelRes()
}

private fun Long.toDisplayDate(): String =
  SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(this))
