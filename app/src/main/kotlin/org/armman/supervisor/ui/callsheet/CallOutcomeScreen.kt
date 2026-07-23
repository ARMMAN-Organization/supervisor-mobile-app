package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** Call Outcome form: Call Connected Yes/No, then the relevant outcome/reason/responder fields. */
@Composable
fun CallOutcomeScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: CallOutcomeViewModel = hiltViewModel(),
) {
  val formState by viewModel.formState.collectAsStateWithLifecycle()

  if (formState.submitted) {
    onSubmitted()
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.call_outcome_connected_label), onBack = onBack) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
      ConnectedCard(
        selected = formState.connected,
        onSelected = viewModel::onConnectedChanged,
      )
      if (formState.connected == CallConnected.YES) {
        SuccessOutcomeCard(
          formState = formState,
          onSuccessOutcomeChanged = viewModel::onSuccessOutcomeChanged,
          onResponderChanged = viewModel::onResponderChanged,
          onDurationChanged = viewModel::onDurationChanged,
          onNotesChanged = viewModel::onNotesChanged,
          onFollowUpActionChanged = viewModel::onFollowUpActionChanged,
        )
      } else if (formState.connected == CallConnected.NO) {
        FailureOutcomeCard(selected = formState.failureReason, onSelected = viewModel::onFailureReasonChanged)
      }
      formState.validationErrorRes?.let { errorRes ->
        Text(text = stringResource(errorRes), color = MaterialTheme.colorScheme.error)
      }
      formState.submitErrorMessage?.let {
        Text(
          text = it.ifBlank { stringResource(R.string.call_outcome_error_submit) },
          color = MaterialTheme.colorScheme.error,
        )
      }
      if (formState.connected != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
          if (formState.isSubmitting) {
            CircularProgressIndicator()
          } else {
            PrimaryButton(
              text = stringResource(R.string.call_outcome_submit),
              onClick = viewModel::onSubmit,
              containerColor = DashboardHeaderGreen,
              modifier = Modifier.fillMaxWidth(0.4f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ConnectedCard(selected: CallConnected?, onSelected: (CallConnected) -> Unit) {
  FormCard(titleRes = R.string.call_outcome_connected_label) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing), modifier = Modifier.selectableGroup()) {
      RadioOption(
        label = stringResource(R.string.call_outcome_connected_yes),
        selected = selected == CallConnected.YES,
        onClick = { onSelected(CallConnected.YES) },
      )
      RadioOption(
        label = stringResource(R.string.call_outcome_connected_no),
        selected = selected == CallConnected.NO,
        onClick = { onSelected(CallConnected.NO) },
      )
    }
  }
}

@Composable
private fun SuccessOutcomeCard(
  formState: CallOutcomeFormState,
  onSuccessOutcomeChanged: (SuccessOutcome) -> Unit,
  onResponderChanged: (CallResponder) -> Unit,
  onDurationChanged: (String) -> Unit,
  onNotesChanged: (String) -> Unit,
  onFollowUpActionChanged: (String) -> Unit,
) {
  val selectReasonPlaceholder = stringResource(R.string.call_outcome_select_reason)
  val successOutcomeLabels = SuccessOutcome.entries.associateWith { stringResource(it.labelRes()) }
  val responderLabels = CallResponder.entries.associateWith { stringResource(it.labelRes()) }
  FormCard(titleRes = R.string.call_outcome_success_label) {
    SingleSelectDropdown(
      options = SuccessOutcome.entries,
      selected = formState.successOutcome,
      optionLabel = { checkNotNull(successOutcomeLabels[it]) },
      placeholder = selectReasonPlaceholder,
      onSelected = onSuccessOutcomeChanged,
    )
    if (formState.showResponder) {
      Text(
        text = stringResource(R.string.call_outcome_responder_label),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralG400,
        modifier = Modifier.padding(top = Dimens.ItemSpacing),
      )
      SingleSelectDropdown(
        options = CallResponder.entries,
        selected = formState.responder,
        optionLabel = { checkNotNull(responderLabels[it]) },
        placeholder = selectReasonPlaceholder,
        onSelected = onResponderChanged,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
    }
    OutlinedTextField(
      value = formState.durationMinutesText,
      onValueChange = onDurationChanged,
      label = { Text(stringResource(R.string.call_outcome_duration_label)) },
      modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing),
    )
    OutlinedTextField(
      value = formState.notes,
      onValueChange = onNotesChanged,
      label = { Text(stringResource(R.string.call_outcome_notes_label)) },
      modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing),
    )
    OutlinedTextField(
      value = formState.followUpAction,
      onValueChange = onFollowUpActionChanged,
      label = { Text(stringResource(R.string.call_outcome_follow_up_label)) },
      modifier = Modifier.fillMaxWidth().padding(top = Dimens.ItemSpacing),
    )
  }
}

@Composable
private fun FailureOutcomeCard(selected: FailureReason?, onSelected: (FailureReason) -> Unit) {
  val failureReasonLabels = FailureReason.entries.associateWith { stringResource(it.labelRes()) }
  FormCard(titleRes = R.string.call_outcome_failure_label) {
    SingleSelectDropdown(
      options = FailureReason.entries,
      selected = selected,
      optionLabel = { checkNotNull(failureReasonLabels[it]) },
      placeholder = stringResource(R.string.call_outcome_select_reason),
      onSelected = onSelected,
    )
  }
}

@Composable
private fun FormCard(titleRes: Int, content: @Composable () -> Unit) {
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = NeutralG400)
      Box(modifier = Modifier.padding(top = Dimens.SmallSpacing)) {}
      content()
    }
  }
}

@Composable
private fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
  ) {
    Image(
      painter = painterResource(if (selected) R.drawable.ic_radio_selected else R.drawable.ic_radio_unselected),
      colorFilter = if (selected) ColorFilter.tint(DashboardHeaderGreen) else null,
      contentDescription = null,
      modifier = Modifier.size(Dimens.RadioIconSize),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = NeutralG400,
      modifier = Modifier.padding(start = Dimens.SmallSpacing),
    )
  }
}
