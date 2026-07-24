package org.armman.supervisor.ui.assignitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.armman.supervisor.R
import org.armman.supervisor.ui.components.BrandTopAppBar
import org.armman.supervisor.ui.components.DatePill
import org.armman.supervisor.ui.components.PrimaryButton
import org.armman.supervisor.ui.components.TransactionCard
import org.armman.supervisor.ui.components.TransactionItemRow
import org.armman.supervisor.ui.theme.DashboardHeaderGreen
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White

/** Assign Item to Sakhi detail screen: Sakhi context + a list of item-assignment transactions. */
@Composable
fun AssignItemDetailScreen(
  onBack: () -> Unit,
  onAddTransaction: () -> Unit,
  onEditTransaction: (String) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AssignItemDetailViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // Re-fetch whenever this screen resumes (e.g. returning from Add/Edit after a submit).
  LifecycleResumeEffect(Unit) {
    viewModel.refresh()
    onPauseOrDispose { }
  }

  Scaffold(
    modifier = modifier,
    topBar = { BrandTopAppBar(title = stringResource(R.string.assign_item_detail_title), onBack = onBack) },
    floatingActionButton = {
      FloatingActionButton(onClick = onAddTransaction, containerColor = DashboardHeaderGreen) {
        Icon(
          painter = painterResource(R.drawable.ic_plus),
          contentDescription = stringResource(R.string.cd_add_transaction),
          tint = White,
        )
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (val state = uiState) {
        is AssignItemDetailUiState.Loading -> LoadingContent()
        is AssignItemDetailUiState.Error -> ErrorContent(
          message = state.exceptionMessage ?: stringResource(state.fallbackMessageRes),
          onRetry = viewModel::onRetry,
        )
        is AssignItemDetailUiState.Success -> SuccessContent(
          state = state,
          onEditTransaction = onEditTransaction,
          onDeleteTransaction = viewModel::onDeleteTransaction,
        )
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
private fun SuccessContent(
  state: AssignItemDetailUiState.Success,
  onEditTransaction: (String) -> Unit,
  onDeleteTransaction: (String) -> Unit,
) {
  // Id of the transaction pending a delete confirmation, or null when no dialog is showing.
  var pendingDeleteId by remember { mutableStateOf<String?>(null) }

  Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    SakhiInfoHeader(detail = state.detail)
    Column(
      modifier = Modifier.fillMaxWidth().padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
    ) {
      if (state.transactions.isEmpty()) {
        Text(
          text = stringResource(R.string.assign_item_detail_empty_transactions),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(top = Dimens.ItemSpacing),
        )
      } else {
        state.transactions.forEach { transaction ->
          DatePill(text = transaction.date, backgroundColor = DashboardHeaderGreen)
          TransactionCard(
            transactionTypeLabel = stringResource(
              R.string.assign_item_transaction_type_label,
              stringResource(transaction.transactionType.labelRes()),
            ),
            allItemsLabel = stringResource(R.string.assign_item_all_items_label),
            items = transaction.items.map {
              TransactionItemRow(it.itemName, stringResource(R.string.assign_item_qty_label, it.quantity))
            },
            onEdit = { onEditTransaction(transaction.id) },
            onDelete = { pendingDeleteId = transaction.id },
          )
        }
      }
    }
  }

  pendingDeleteId?.let { id ->
    DeleteConfirmDialog(
      onConfirm = {
        pendingDeleteId = null
        onDeleteTransaction(id)
      },
      onDismiss = { pendingDeleteId = null },
    )
  }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.delete_transaction_confirm_title)) },
    text = { Text(stringResource(R.string.delete_transaction_confirm_message)) },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

@Composable
private fun SakhiInfoHeader(detail: SakhiDetail) {
  Surface(color = DashboardHeaderGreen, modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(Dimens.ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
    ) {
      Text(
        text = stringResource(R.string.assign_item_sakhi_name_label, detail.sakhiName),
        style = MaterialTheme.typography.bodyLarge,
        color = White,
      )
      Text(
        text = stringResource(R.string.assign_item_project_label, detail.projectName),
        style = MaterialTheme.typography.bodyLarge,
        color = White,
      )
      Text(
        text = stringResource(R.string.assign_item_address_label, detail.address),
        style = MaterialTheme.typography.bodyLarge,
        color = White,
      )
    }
  }
}
