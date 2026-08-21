package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.Dimens

/** Call Sheet list screen: project/location selector + a Sakhi list with stats and a call action. */
@Composable
fun CallSheetScreen(
  onBack: () -> Unit,
  onSakhiSelected: (SakhiOption) -> Unit,
  onStatClick: (SakhiOption, CallSheetStatKind) -> Unit,
  onLastSyncDateClick: (SakhiOption) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallSheetViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Re-fetch whenever this screen resumes (e.g. returning from Call History after logging a call).
  LifecycleResumeEffect(Unit) {
    viewModel.onResumed()
    onPauseOrDispose { }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.call_sheet_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is CallSheetUiState.Loading -> LoadingContent()
        is CallSheetUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is CallSheetUiState.Success -> SuccessContent(
          state = state,
          onLocationSelected = viewModel::onLocationSelected,
          onSakhiSelected = onSakhiSelected,
          onStatClick = onStatClick,
          onLastSyncDateClick = onLastSyncDateClick,
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
  state: CallSheetUiState.Success,
  onLocationSelected: (String) -> Unit,
  onSakhiSelected: (SakhiOption) -> Unit,
  onStatClick: (SakhiOption, CallSheetStatKind) -> Unit,
  onLastSyncDateClick: (SakhiOption) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    SingleSelectDropdown(
      options = state.locations,
      selected = state.locations.find { it.id == state.selectedLocationId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.location_selector_placeholder),
      onSelected = { onLocationSelected(it.id) },
    )
    if (state.sakhiSummaries.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.call_sheet_empty_sakhis), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      val now = System.currentTimeMillis()
      LazyColumn(
        modifier = Modifier.padding(top = Dimens.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
      ) {
        items(state.sakhiSummaries) { summary ->
          val isRecentlyCalled = summary.lastCalledAtEpochMillis?.let { now - it < RECENTLY_CALLED_WINDOW_MILLIS } ?: false
          CallSheetSakhiCard(
            summary = summary,
            isRecentlyCalled = isRecentlyCalled,
            onCallClick = { onSakhiSelected(summary.sakhi) },
            onCardClick = { onSakhiSelected(summary.sakhi) },
            onStatClick = { kind -> onStatClick(summary.sakhi, kind) },
            onLastSyncDateClick = { onLastSyncDateClick(summary.sakhi) },
          )
        }
      }
    }
  }
}
