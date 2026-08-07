package org.armman.supervisor.ui.quickresponse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

/** Add Reason form: pick a [ReasonOption] for a Quick Response request and submit. */
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
    topBar = { BrandTopAppBar(title = stringResource(R.string.quick_response_add_reason_title), onBack = onBack) },
  ) { innerPadding ->
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      val isTablet = maxWidth >= Dimens.TabletMinWidthDp.dp
      Box(
        modifier = Modifier.fillMaxSize().padding(Dimens.ScreenPadding),
        contentAlignment = if (isTablet) Alignment.TopCenter else Alignment.TopStart,
      ) {
        Column(
          modifier = Modifier.widthIn(max = Dimens.ContentMaxWidthTablet).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
          ReasonCard(
            selectedReason = formState.selectedReason,
            onReasonSelected = viewModel::onReasonSelected,
          )
          formState.validationErrorRes?.let { errorRes ->
            Text(text = stringResource(errorRes), color = MaterialTheme.colorScheme.error)
          }
          formState.submitErrorMessage?.let {
            Text(
              text = it.ifBlank { stringResource(R.string.quick_response_error_submit) },
              color = MaterialTheme.colorScheme.error,
            )
          }
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (formState.isSubmitting) {
              CircularProgressIndicator()
            } else {
              PrimaryButton(
                text = stringResource(R.string.quick_response_submit),
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
}

@Composable
private fun ReasonCard(selectedReason: ReasonOption?, onReasonSelected: (ReasonOption) -> Unit) {
  val reasonLabels = ReasonOption.entries.associateWith { stringResource(it.labelRes()) }
  Surface(
    color = White,
    shape = RoundedCornerShape(Dimens.CardRadius),
    modifier = Modifier.fillMaxWidth().softShadow(Dimens.CardRadius),
  ) {
    Column(modifier = Modifier.padding(Dimens.TilePadding)) {
      Text(
        text = stringResource(R.string.quick_response_reason_label),
        style = MaterialTheme.typography.titleMedium,
        color = NeutralG400,
      )
      SingleSelectDropdown(
        options = ReasonOption.entries,
        selected = selectedReason,
        optionLabel = { checkNotNull(reasonLabels[it]) },
        placeholder = stringResource(R.string.quick_response_select_reason),
        onSelected = onReasonSelected,
        modifier = Modifier.padding(top = Dimens.SmallSpacing),
      )
    }
  }
}
