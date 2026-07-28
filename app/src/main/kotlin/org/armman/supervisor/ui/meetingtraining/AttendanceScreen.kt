package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardKpiGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400

/** Attendance screen: Sakhi roster checklist, "Mark All Present", running count. */
@Composable
fun AttendanceScreen(
  onBack: () -> Unit,
  onSaved: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AttendanceViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? AttendanceUiState.Success)?.saved == true) onSaved()
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.attendance_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is AttendanceUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is AttendanceUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is AttendanceUiState.Success -> SuccessContent(state = state, viewModel = viewModel)
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
private fun SuccessContent(state: AttendanceUiState.Success, viewModel: AttendanceViewModel) {
  Column(modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = stringResource(R.string.meeting_training_attended_count, state.presentCount, state.roster.size),
        style = MaterialTheme.typography.titleMedium,
      )
      TextButton(onClick = viewModel::onMarkAllPresent, enabled = !state.readOnly) {
        Text(stringResource(R.string.attendance_mark_all_present))
      }
    }
    LazyColumn(modifier = Modifier.padding(top = Dimens.ItemSpacing)) {
      items(state.roster, key = { it.sakhiId }) { entry ->
        AttendanceRow(entry = entry, readOnly = state.readOnly, onToggle = { viewModel.onToggle(entry.sakhiId) })
      }
    }
    PrimaryButton(
      text = stringResource(R.string.attendance_save),
      onClick = viewModel::onSave,
      loading = state.isSaving,
      enabled = !state.readOnly,
      modifier = Modifier.padding(top = Dimens.ItemSpacing),
    )
  }
}

@Composable
private fun AttendanceRow(entry: AttendanceEntry, readOnly: Boolean, onToggle: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.SmallSpacing).clickable(enabled = !readOnly, onClick = onToggle),
  ) {
    Text(text = entry.sakhiName, style = MaterialTheme.typography.bodyLarge, color = NeutralG400, modifier = Modifier.weight(1f))
    Checkbox(
      checked = entry.present,
      onCheckedChange = { onToggle() },
      enabled = !readOnly,
      colors = CheckboxDefaults.colors(checkedColor = DashboardKpiGreen),
    )
  }
}
