package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.semantics.Role
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

/** New Training form: Project, training date range, pre/post-marks flag, remarks, submit. */
@Composable
fun ScheduleTrainingScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: ScheduleTrainingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? ScheduleTrainingUiState.Success)?.submitted == true) onSubmitted()
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.schedule_training_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is ScheduleTrainingUiState.Loading -> LoadingContent()
        is ScheduleTrainingUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is ScheduleTrainingUiState.Success -> FormContent(state = state, viewModel = viewModel)
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
private fun FormContent(state: ScheduleTrainingUiState.Success, viewModel: ScheduleTrainingViewModel) {
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }

  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
  ) {
    SingleSelectDropdown(
      options = state.projects,
      selected = state.projects.find { it.id == state.selectedProjectId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.schedule_training_project_placeholder),
      onSelected = { viewModel.onProjectSelected(it.id) },
    )
    Text(text = stringResource(R.string.schedule_training_date_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
      AppDateField(
        label = stringResource(R.string.meeting_detail_start_date),
        placeholder = stringResource(R.string.schedule_training_start_placeholder),
        value = state.startDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onStartDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
      )
      AppDateField(
        label = stringResource(R.string.meeting_detail_end_date),
        placeholder = stringResource(R.string.schedule_training_end_placeholder),
        value = state.endDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onEndDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().toggleable(
        value = state.prePostMarksApplicable,
        role = Role.Checkbox,
        onValueChange = viewModel::onPrePostMarksToggled,
      ),
    ) {
      Checkbox(
        checked = state.prePostMarksApplicable,
        onCheckedChange = null,
        colors = CheckboxDefaults.colors(checkedColor = DashboardHeaderGreen),
      )
      Text(
        text = stringResource(R.string.schedule_training_pre_post_marks_label),
        style = MaterialTheme.typography.bodyLarge,
      )
    }
    AppTextField(
      value = state.remarks,
      onValueChange = viewModel::onRemarksChanged,
      label = stringResource(R.string.schedule_training_remarks_label),
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
      text = stringResource(R.string.schedule_training_submit),
      onClick = viewModel::onSubmit,
      loading = state.isSubmitting,
      containerColor = DashboardHeaderGreen,
    )
  }
}

private fun ScheduleTrainingFormError.messageRes(): Int = when (this) {
  ScheduleTrainingFormError.PROJECT_REQUIRED -> R.string.schedule_meeting_error_project_required
  ScheduleTrainingFormError.START_DATE_REQUIRED -> R.string.schedule_training_error_start_date_required
  ScheduleTrainingFormError.INVALID_DATE_RANGE -> R.string.schedule_meeting_error_invalid_range
}
