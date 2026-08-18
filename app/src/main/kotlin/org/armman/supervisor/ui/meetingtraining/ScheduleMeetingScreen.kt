package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.components.AppDateField
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** New Meeting form: Project, date range, remarks, submit. */
@Composable
fun ScheduleMeetingScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ScheduleMeetingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? ScheduleMeetingUiState.Success)?.submitted == true) onSubmitted()
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.schedule_meeting_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is ScheduleMeetingUiState.Loading -> LoadingContent()
        is ScheduleMeetingUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is ScheduleMeetingUiState.Success -> FormContent(state = state, viewModel = viewModel)
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
private fun FormContent(state: ScheduleMeetingUiState.Success, viewModel: ScheduleMeetingViewModel) {
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }

  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
  ) {
    SingleSelectDropdown(
      options = state.projects,
      selected = state.projects.find { it.id == state.selectedProjectId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.schedule_meeting_project_placeholder),
      onSelected = { viewModel.onProjectSelected(it.id) },
    )
    Text(text = stringResource(R.string.schedule_meeting_date_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
      AppDateField(
        label = stringResource(R.string.meeting_detail_start_date),
        placeholder = stringResource(R.string.schedule_meeting_start_placeholder),
        value = state.startDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onStartDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
        minDate = LocalDate.now(),
      )
      AppDateField(
        label = stringResource(R.string.meeting_detail_end_date),
        placeholder = stringResource(R.string.schedule_meeting_end_placeholder),
        value = state.endDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onEndDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
        minDate = LocalDate.now(),
      )
    }
    AppTextField(
      value = state.remarks,
      onValueChange = viewModel::onRemarksChanged,
      label = stringResource(R.string.schedule_meeting_remarks_label),
      placeholder = stringResource(R.string.add_item_remarks_placeholder),
    )
    state.formError?.let { error ->
      Text(
        text = stringResource(error.messageRes()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
      )
    }
    PrimaryButton(
      text = stringResource(R.string.schedule_meeting_submit),
      onClick = viewModel::onSubmit,
      loading = state.isSubmitting,
      containerColor = DashboardHeaderGreen,
    )
  }
}

private fun ScheduleMeetingFormError.messageRes(): Int = when (this) {
  ScheduleMeetingFormError.PROJECT_REQUIRED -> R.string.schedule_meeting_error_project_required
  ScheduleMeetingFormError.START_DATE_REQUIRED -> R.string.schedule_meeting_error_start_date_required
  ScheduleMeetingFormError.INVALID_DATE_RANGE -> R.string.schedule_meeting_error_invalid_range
}
