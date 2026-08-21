package org.armman.supervisor.ui.assignitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.armman.supervisor.ui.components.AppTextField
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.SingleSelectDropdown
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Add Item Transaction form: program, date, type, remarks and per-item quantities for a Sakhi. */
@Composable
fun AddItemTransactionScreen(
  onBack: () -> Unit,
  onSubmitted: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddItemTransactionViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState) {
    if ((uiState as? AddItemTransactionUiState.Success)?.submitted == true) onSubmitted()
  }

  val isEditing = (uiState as? AddItemTransactionUiState.Success)?.isEditing == true
  val titleRes = if (isEditing) R.string.edit_item_transaction_title else R.string.add_item_transaction_title

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(titleRes), onBack = onBack) },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is AddItemTransactionUiState.Loading -> LoadingContent()
        is AddItemTransactionUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is AddItemTransactionUiState.Success -> FormContent(state = state, viewModel = viewModel)
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
private fun FormContent(state: AddItemTransactionUiState.Success, viewModel: AddItemTransactionViewModel) {
  val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()) }
  val transactionTypeLabels = TransactionType.entries.associateWith { stringResource(it.labelRes()) }

  Column(modifier = Modifier.fillMaxSize()) {
    SakhiHeader(sakhiName = state.sakhiName, programName = state.programs.find { it.id == state.selectedProgramId }?.name)
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
      AppDateField(
        label = stringResource(R.string.add_item_transaction_date_label),
        placeholder = stringResource(R.string.add_item_select_date),
        value = state.transactionDate?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() },
        onDateSelected = { viewModel.onDateSelected(it.format(formatter)) },
        // A transaction records something that already happened (a handover/return/etc. that took
        // place), so the backend rejects a future date with a bare HTTP 400 — capping the picker
        // here stops that error from being reachable in the first place.
        maxDate = LocalDate.now(),
      )
      SingleSelectDropdown(
        options = TransactionType.entries,
        selected = state.selectedType,
        optionLabel = { requireNotNull(transactionTypeLabels[it]) },
        placeholder = stringResource(R.string.add_item_select_transaction_type),
        onSelected = viewModel::onTypeSelected,
      )
      AppTextField(
        value = state.remarks,
        onValueChange = viewModel::onRemarksChanged,
        label = stringResource(R.string.add_item_remarks_label),
        placeholder = stringResource(R.string.add_item_remarks_placeholder),
      )
      ItemQuantityList(
        items = state.items,
        quantities = state.quantities,
        onQuantityChanged = viewModel::onQuantityChanged,
      )
      state.formError?.let { error ->
        Text(
          text = stringResource(error.messageRes()),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.error,
        )
      }
      PrimaryButton(
        text = stringResource(if (state.isEditing) R.string.edit_item_submit else R.string.add_item_submit),
        onClick = viewModel::onSubmit,
        loading = state.isSubmitting,
        containerColor = DashboardHeaderGreen,
      )
    }
  }
}

@Composable
private fun SakhiHeader(sakhiName: String, programName: String?) {
  Surface(color = DashboardHeaderGreen, modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    ) {
      Text(
        text = stringResource(R.string.assign_item_sakhi_name_label, sakhiName),
        style = MaterialTheme.typography.bodyLarge,
        color = White,
      )
      Text(
        text = stringResource(R.string.assign_item_project_label, programName ?: ""),
        style = MaterialTheme.typography.bodyLarge,
        color = White,
      )
    }
  }
}

private fun TransactionFormError.messageRes(): Int = when (this) {
  TransactionFormError.DATE_REQUIRED -> R.string.add_item_error_date_required
  TransactionFormError.DATE_IN_FUTURE -> R.string.add_item_error_date_in_future
  TransactionFormError.TYPE_REQUIRED -> R.string.add_item_error_type_required
  TransactionFormError.NO_ITEMS -> R.string.add_item_error_no_items
}
