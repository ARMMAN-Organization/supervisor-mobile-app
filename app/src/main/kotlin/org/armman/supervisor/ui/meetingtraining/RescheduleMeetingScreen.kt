package org.armman.supervisor.ui.meetingtraining

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import org.armman.supervisor.ui.components.AppDateField
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reschedule Meeting screen: original dates (read-only) + new start/end date pickers. */
@Composable
fun RescheduleMeetingScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: RescheduleMeetingViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? RescheduleMeetingUiState.Success)?.submitted == true) onSubmitted()
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.reschedule_meeting_title), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is RescheduleMeetingUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is RescheduleMeetingUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is RescheduleMeetingUiState.Success -> FormContent(state = state, viewModel = viewModel)
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
private fun FormContent(state: RescheduleMeetingUiState.Success, viewModel: RescheduleMeetingViewModel) {
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }

  Column(
    modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
  ) {
    Surface(color = White, shape = RoundedCornerShape(Dimens.CardRadius), modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(Dimens.TilePadding)) {
        Text(
          text = stringResource(R.string.meeting_training_date_range, state.originalStartDate, state.originalEndDate),
          style = MaterialTheme.typography.bodyLarge,
          color = NeutralG200,
        )
      }
    }
    Text(text = stringResource(R.string.reschedule_meeting_new_date_label), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing), modifier = Modifier.fillMaxWidth()) {
      AppDateField(
        label = stringResource(R.string.meeting_detail_start_date),
        placeholder = state.originalStartDate,
        value = state.newStartDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onNewStartDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
        minDate = LocalDate.now(),
      )
      AppDateField(
        label = stringResource(R.string.meeting_detail_end_date),
        placeholder = state.originalEndDate,
        value = state.newEndDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onNewEndDateSelected(it.format(formatter)) },
        modifier = Modifier.weight(1f),
        minDate = LocalDate.now(),
      )
    }
    if (state.invalidRange) {
      Text(
        text = stringResource(R.string.reschedule_meeting_error_invalid_range),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
      )
    }
    PrimaryButton(
      text = stringResource(R.string.reschedule_meeting_submit),
      onClick = viewModel::onSubmit,
      loading = state.isSubmitting,
      containerColor = DashboardHeaderGreen,
    )
  }
}
