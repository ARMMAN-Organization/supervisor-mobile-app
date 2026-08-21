package org.armman.supervisor.ui.callsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
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

/** Shared "Add Reason" form (dropdown + remark + submit) for the Followup Pending, Closure
 * Pending and Last Sync Date Call Sheet flows — which reason list is shown is driven entirely by
 * [AddReasonViewModel]'s [ReasonContext], read from nav args. */
@Composable
fun AddReasonScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddReasonViewModel = hiltViewModel(),
) {
  val formState by viewModel.formState.collectAsStateWithLifecycle()

  if (formState.submitted) {
    onSubmitted()
    return
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.add_reason_title), onBack = onBack) },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
      ReasonCard(formState = formState, onReasonSelected = viewModel::onReasonSelected, onRemarkChanged = viewModel::onRemarkChanged)
      formState.validationErrorRes?.let { errorRes ->
        Text(text = stringResource(errorRes), color = MaterialTheme.colorScheme.error)
      }
      formState.submitErrorMessage?.let {
        Text(
          text = it.ifBlank { stringResource(R.string.add_reason_error_submit) },
          color = MaterialTheme.colorScheme.error,
        )
      }
      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        if (formState.isSubmitting) {
          CircularProgressIndicator()
        } else {
          PrimaryButton(
            text = stringResource(R.string.add_reason_submit),
            onClick = viewModel::onSubmit,
            containerColor = DashboardHeaderGreen,
            modifier = Modifier.fillMaxWidth(0.4f),
          )
        }
      }
    }
  }
}

@Composable
private fun ReasonCard(
  formState: AddReasonFormState,
  onReasonSelected: (ReasonChoice) -> Unit,
  onRemarkChanged: (String) -> Unit,
) {
  val choices = formState.context.choices()
  val labels = choices.associateWith { stringResource(it.labelRes) }
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Text(text = stringResource(R.string.add_reason_reason_label), style = MaterialTheme.typography.titleMedium, color = NeutralG400)
      SingleSelectDropdown(
        options = choices,
        selected = formState.selectedReason,
        optionLabel = { checkNotNull(labels[it]) },
        placeholder = stringResource(R.string.add_reason_select_reason),
        onSelected = onReasonSelected,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
      Text(
        text = stringResource(R.string.add_reason_remark_label),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralG400,
        modifier = Modifier.padding(top = Dimens.ItemSpacing),
      )
      OutlinedTextField(
        value = formState.remark,
        onValueChange = onRemarkChanged,
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.SmallSpacing),
      )
    }
  }
}
