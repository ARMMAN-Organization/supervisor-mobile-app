package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.armman.supervisor.ui.components.AppDateField
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Add Training Topics: project (read-only), date, multi-select topic catalog, Save. */
@Composable
fun AddTrainingTopicsScreen(
  onBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddTrainingTopicsViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? AddTrainingTopicsUiState.Success)?.saved == true) onSaved()
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.add_training_topics_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is AddTrainingTopicsUiState.Loading -> LoadingContent()
        is AddTrainingTopicsUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is AddTrainingTopicsUiState.Success -> FormContent(state = state, viewModel = viewModel)
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
private fun FormContent(state: AddTrainingTopicsUiState.Success, viewModel: AddTrainingTopicsViewModel) {
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }

  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    Text(text = state.projectName, style = MaterialTheme.typography.titleMedium)
    AppDateField(
      label = stringResource(R.string.add_training_topics_date_label),
      placeholder = stringResource(R.string.add_training_topics_date_placeholder),
      value = runCatching { LocalDate.parse(state.date, formatter) }.getOrNull(),
      onDateSelected = { viewModel.onDateSelected(it.format(formatter)) },
      modifier = Modifier.padding(top = Dimens.SmallSpacing),
    )
    Text(
      text = stringResource(R.string.add_training_topics_label),
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(top = Dimens.ItemSpacing, bottom = Dimens.SmallSpacing),
    )
    LazyColumn(modifier = Modifier.weight(1f)) {
      items(state.catalog, key = { it.id }) { topic ->
        TopicRow(
          topic = topic,
          checked = topic.id in state.selectedTopicIds,
          onToggle = { viewModel.onTopicToggled(topic.id) },
        )
      }
    }
    PrimaryButton(
      text = stringResource(R.string.action_save),
      onClick = viewModel::onSave,
      enabled = state.selectedTopicIds.isNotEmpty() && state.date.isNotBlank(),
      loading = state.isSaving,
      containerColor = DashboardHeaderGreen,
      modifier = Modifier.padding(top = Dimens.ItemSpacing),
    )
  }
}

@Composable
private fun TopicRow(topic: TrainingTopic, checked: Boolean, onToggle: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
  ) {
    Checkbox(
      checked = checked,
      onCheckedChange = null,
      colors = CheckboxDefaults.colors(checkedColor = DashboardHeaderGreen),
    )
    Text(text = topic.name, style = MaterialTheme.typography.bodyLarge)
  }
}
