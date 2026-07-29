package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens

/** Meeting & Training list: Scheduled/Completed tabs, project + gathering-type filters, event cards. */
@Composable
fun MeetingTrainingScreen(
  onBack: () -> Unit,
  onNewMeeting: () -> Unit,
  onNewTraining: () -> Unit,
  onEventSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MeetingTrainingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose { /* no-op */ }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.meeting_training_title), onBack = onBack) },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      NewEventButtonsRow(onNewMeeting = onNewMeeting, onNewTraining = onNewTraining)
      when (val state = uiState) {
        is MeetingTrainingUiState.Loading -> LoadingContent()
        is MeetingTrainingUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is MeetingTrainingUiState.Success -> SuccessContent(
          state = state,
          onTabSelected = viewModel::onTabSelected,
          onProjectSelected = viewModel::onProjectSelected,
          onEventTypeToggled = viewModel::onEventTypeToggled,
          onEventSelected = onEventSelected,
        )
      }
    }
  }
}

@Composable
private fun NewEventButtonsRow(onNewMeeting: () -> Unit, onNewTraining: () -> Unit) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
  ) {
    OutlinedButton(onClick = onNewMeeting, modifier = Modifier.weight(1f)) {
      Text(stringResource(R.string.meeting_training_new_meeting))
    }
    PrimaryButton(
      text = stringResource(R.string.meeting_training_new_training),
      onClick = onNewTraining,
      containerColor = DashboardHeaderGreen,
      modifier = Modifier.weight(1f),
    )
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
  state: MeetingTrainingUiState.Success,
  onTabSelected: (MeetingTrainingTab) -> Unit,
  onProjectSelected: (String?) -> Unit,
  onEventTypeToggled: (EventType) -> Unit,
  onEventSelected: (String) -> Unit,
) {
  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = state.tab.ordinal) {
      Tab(
        selected = state.tab == MeetingTrainingTab.SCHEDULED,
        onClick = { onTabSelected(MeetingTrainingTab.SCHEDULED) },
        text = { Text(stringResource(R.string.meeting_training_tab_scheduled)) },
      )
      Tab(
        selected = state.tab == MeetingTrainingTab.COMPLETED,
        onClick = { onTabSelected(MeetingTrainingTab.COMPLETED) },
        text = { Text(stringResource(R.string.meeting_training_tab_completed)) },
      )
    }
    Column(modifier = Modifier.padding(Dimens.ScreenPadding)) {
      Text(
        text = stringResource(R.string.meeting_training_project_label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = Dimens.TinySpacing),
      )
      SingleSelectDropdown(
        options = state.projects,
        selected = state.projects.find { it.id == state.selectedProjectId },
        optionLabel = LocationOption::name,
        placeholder = stringResource(R.string.meeting_training_all_projects),
        onSelected = { onProjectSelected(it.id) },
      )
      Text(
        text = stringResource(R.string.meeting_training_gathering_type_label),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = Dimens.ItemSpacing, bottom = Dimens.SmallSpacing),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)) {
        EventTypeChip(
          label = stringResource(R.string.meeting_training_chip_meeting),
          selected = EventType.MEETING in state.selectedEventTypes,
          onClick = { onEventTypeToggled(EventType.MEETING) },
        )
        EventTypeChip(
          label = stringResource(R.string.meeting_training_chip_training),
          selected = EventType.TRAINING in state.selectedEventTypes,
          onClick = { onEventTypeToggled(EventType.TRAINING) },
        )
      }
    }
    if (state.events.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.meeting_training_empty_events), style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      LazyColumn(
        contentPadding = PaddingValues(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
      ) {
        items(state.events, key = { it.id }) { event ->
          MeetingEntryCard(event = event, onClick = { onEventSelected(event.id) })
        }
      }
    }
  }
}

@Composable
private fun EventTypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DashboardHeaderGreen.copy(alpha = 0.15f)),
  )
}
