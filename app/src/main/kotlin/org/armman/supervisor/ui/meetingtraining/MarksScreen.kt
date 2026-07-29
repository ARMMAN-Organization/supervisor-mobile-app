package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens

/** Pre/Post Marks screen: pick a Training Topic, enter each Sakhi's marks, Save or Complete and
 * close (which locks this topic's marks permanently). */
@Composable
fun MarksScreen(
  onBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MarksViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? MarksUiState.Success)?.saved == true) onSaved()
  }

  val title = when ((uiState as? MarksUiState.Success)?.marksType) {
    MarksType.POST -> stringResource(R.string.marks_title_post)
    else -> stringResource(R.string.marks_title_pre)
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = title, onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is MarksUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is MarksUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is MarksUiState.Success -> SuccessContent(state = state, viewModel = viewModel)
      }
    }
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
private fun SuccessContent(state: MarksUiState.Success, viewModel: MarksViewModel) {
  var showCompleteDialog by remember { mutableStateOf(false) }
  val topicOptions = remember(state.topics) { state.topics.map { LocationOption(it.id, it.name) } }

  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    Text(
      text = stringResource(R.string.marks_topic_label),
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(bottom = Dimens.TinySpacing),
    )
    SingleSelectDropdown(
      options = topicOptions,
      selected = topicOptions.find { it.id == state.selectedTopicId },
      optionLabel = LocationOption::name,
      placeholder = stringResource(R.string.marks_topic_placeholder),
      onSelected = { viewModel.onTopicSelected(it.id) },
    )
    if (state.selectedTopicId != null) {
      LazyColumn(modifier = Modifier.weight(1f).padding(top = Dimens.ItemSpacing)) {
        items(state.roster, key = { it.sakhiId }) { entry ->
          MarksRow(entry = entry, readOnly = state.completed, onValueChange = { viewModel.onMarksChanged(entry.sakhiId, it) })
        }
      }
      if (state.invalidValueError) {
        Text(
          text = stringResource(R.string.marks_error_invalid_value),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = Dimens.SmallSpacing),
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing)) {
        OutlinedButton(
          onClick = { showCompleteDialog = true },
          enabled = !state.completed && !state.isSaving && !state.isCompleting && !state.isLoadingRoster,
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.marks_complete_and_close))
        }
        PrimaryButton(
          text = stringResource(R.string.marks_save),
          onClick = viewModel::onSave,
          enabled = !state.completed && !state.invalidValueError && !state.isLoadingRoster,
          loading = state.isSaving,
          containerColor = DashboardHeaderGreen,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  if (showCompleteDialog) {
    val messageRes = if (state.marksType == MarksType.POST) {
      R.string.marks_complete_dialog_message_post
    } else {
      R.string.marks_complete_dialog_message_pre
    }
    AlertDialog(
      onDismissRequest = { showCompleteDialog = false },
      title = { Text(stringResource(R.string.marks_complete_dialog_title)) },
      text = { Text(stringResource(messageRes)) },
      confirmButton = {
        TextButton(onClick = { showCompleteDialog = false; viewModel.onCompleteAndClose() }) {
          Text(stringResource(R.string.meeting_detail_complete_confirm))
        }
      },
      dismissButton = { TextButton(onClick = { showCompleteDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
  }
}

@Composable
private fun MarksRow(entry: MarksEntry, readOnly: Boolean, onValueChange: (String) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SmallSpacing),
  ) {
    Text(text = entry.sakhiName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    AppTextField(
      value = entry.marks?.toString() ?: "",
      onValueChange = onValueChange,
      label = "",
      placeholder = "",
      enabled = !readOnly,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.width(Dimens.QuantityFieldWidth),
    )
  }
}
