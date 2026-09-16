package org.armman.supervisor.ui.assignitem

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.navigation.Routes
import java.time.LocalDate
import javax.inject.Inject

/** Which form field failed validation on submit, so the screen can show the right message. */
enum class TransactionFormError { DATE_REQUIRED, DATE_IN_FUTURE, TYPE_REQUIRED, NO_ITEMS }

/** UI state for the Add Item Transaction screen. */
sealed interface AddItemTransactionUiState {
  data object Loading : AddItemTransactionUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : AddItemTransactionUiState

  data class Success(
    val sakhiName: String,
    val programs: List<LocationOption>,
    val items: List<InventoryItem>,
    val selectedProgramId: String?,
    val selectedType: TransactionType?,
    val transactionDate: String?,
    val remarks: String,
    val quantities: Map<String, Int>,
    val formError: TransactionFormError?,
    val isSubmitting: Boolean,
    val submitted: Boolean,
    val isEditing: Boolean,
  ) : AddItemTransactionUiState
}

@HiltViewModel
class AddItemTransactionViewModel @Inject constructor(
  private val repository: AssignItemRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG])
  private val editTransactionId: String? = savedStateHandle[Routes.ADD_ITEM_TRANSACTION_EDIT_ID_ARG]

  private val _uiState = MutableStateFlow<AddItemTransactionUiState>(AddItemTransactionUiState.Loading)
  val uiState: StateFlow<AddItemTransactionUiState> = _uiState.asStateFlow()

  // Item id -> server row id being edited, one entry per item line in the transaction group being
  // edited. Empty in create mode. Used on submit to tell `updateTransaction` which row each edited
  // quantity belongs to.
  private var editingRowIdsByItemId: Map<String, String> = emptyMap()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  private fun load() {
    _uiState.value = AddItemTransactionUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getSakhiDetail(sakhiId)
        val programs = repository.getPrograms()
        val catalogItems = repository.getInventoryItems()
        val editing = editTransactionId?.let { id ->
          repository.getTransactions(sakhiId).firstOrNull { id in it.ids }
            ?: error("Unknown transaction id: $id")
        }
        // getInventoryItems() dedupes catalog rows that share a (name, category) pair down to one
        // survivor id (see AssignItemRepositoryImpl.dedupedForSelection). If the transaction being
        // edited references the itemId that dedup discarded, swap the survivor's row for one
        // carrying the transaction's real itemId — otherwise the rendered row and
        // editingRowIdsByItemId disagree on id, and onQuantityChanged rejects every edit.
        val items = editing?.items.orEmpty().fold(catalogItems) { acc, entry ->
          val survivor = acc.firstOrNull { it.name == entry.itemName }
          if (survivor == null || survivor.id == entry.itemId) acc
          else acc.map { if (it.id == survivor.id) it.copy(id = entry.itemId) else it }
        }
        val editingQuantitiesByItemId = editing?.items?.associate { it.itemId to it.quantity }.orEmpty()
        editingRowIdsByItemId = editing?.items?.associate { it.itemId to it.id }.orEmpty()

        _uiState.value = AddItemTransactionUiState.Success(
          sakhiName = detail.sakhiName,
          programs = programs,
          items = items,
          selectedProgramId = programs.firstOrNull { it.name == detail.projectName }?.id ?: programs.firstOrNull()?.id,
          selectedType = editing?.transactionType,
          transactionDate = editing?.date?.toTransactionDisplayDate(),
          remarks = "",
          quantities = editingQuantitiesByItemId,
          formError = null,
          isSubmitting = false,
          submitted = false,
          isEditing = editTransactionId != null,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AddItemTransactionUiState.Error(R.string.add_item_error_load_form, e.message)
      }
    }
  }

  fun onTypeSelected(type: TransactionType) = updateSuccess { it.copy(selectedType = type, formError = null) }

  fun onDateSelected(date: String) = updateSuccess { it.copy(transactionDate = date, formError = null) }

  fun onRemarksChanged(remarks: String) = updateSuccess { it.copy(remarks = remarks) }

  /** In edit mode, only quantities for the transaction group's existing item lines can be
   * changed — adding a brand-new item id isn't supported, since there is no API to add an item
   * line to an existing transaction. Zeroing an existing line's quantity IS supported: [onSubmit]
   * deletes that line's row instead of updating it. */
  fun onQuantityChanged(itemId: String, quantity: Int) = updateSuccess { state ->
    if (state.isEditing && itemId !in editingRowIdsByItemId) return@updateSuccess state
    val updated = state.quantities.toMutableMap().also {
      if (quantity > 0) it[itemId] = quantity else it.remove(itemId)
    }
    state.copy(quantities = updated, formError = null)
  }

  fun onSubmit() {
    val state = _uiState.value as? AddItemTransactionUiState.Success ?: return
    if (state.isSubmitting) return

    val error = validate(state)
    if (error != null) {
      _uiState.value = state.copy(formError = error)
      return
    }

    _uiState.value = state.copy(isSubmitting = true, formError = null)
    viewModelScope.launch {
      // Tracks whether the delete step below has already completed server-side, so a failure in
      // the update step that follows it can be reported accurately instead of implying nothing
      // happened at all (see the comment on the edit-mode branch).
      var deletedRemovedRows = false
      try {
        val submission = TransactionSubmission(
          sakhiId = sakhiId,
          projectId = checkNotNull(state.selectedProgramId),
          transactionType = checkNotNull(state.selectedType),
          transactionDate = checkNotNull(state.transactionDate),
          remarks = state.remarks.ifBlank { null },
          items = state.quantities.map { (itemId, qty) ->
            TransactionItemQuantity(itemId, qty, existingRowId = editingRowIdsByItemId[itemId])
          },
        )
        if (editTransactionId != null) {
          // An existing item line the user zeroed out (see onQuantityChanged) drops out of
          // state.quantities entirely, so it's absent from submission.items — updateTransaction
          // alone would never touch that row, leaving its old quantity in place server-side while
          // the UI reports success. Deleting its row explicitly is how "remove this item from the
          // transaction" actually happens (deleteTransaction operates per-row, same as removing a
          // whole card — see AssignItemRepository.deleteTransaction).
          val removedRowIds = editingRowIdsByItemId.filterKeys { it !in state.quantities }.values.toList()
          if (removedRowIds.isNotEmpty()) {
            repository.deleteTransaction(sakhiId, removedRowIds)
            deletedRemovedRows = true
          }
          if (submission.items.isNotEmpty()) repository.updateTransaction(submission)
        } else {
          repository.submitTransaction(submission)
        }
        _uiState.value = state.copy(isSubmitting = false, submitted = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // If the delete already went through before updateTransaction threw, a generic "failed to
        // submit" message would wrongly suggest none of the edit was applied — the removed rows
        // are already gone server-side, so say so instead of masking that partial completion.
        val messageRes = if (deletedRemovedRows) R.string.add_item_error_partial_update else R.string.add_item_error_submit
        _uiState.value = AddItemTransactionUiState.Error(messageRes, e.message.takeIf { !deletedRemovedRows })
      }
    }
  }

  /** A transaction records something that already happened (a handover/return/etc. that took
   * place) — the backend rejects a future [AddItemTransactionUiState.Success.transactionDate]
   * with a bare HTTP 400, so it's checked client-side for a clear message instead. The date
   * picker itself is already capped at today (see [org.armman.supervisor.ui.assignitem.AddItemTransactionScreen]),
   * but this still guards a stale edit-mode date or a device clock change landing here.
   * [parseTransactionDate] returning null (an unparseable date somehow reached this state) is
   * surfaced as DATE_REQUIRED rather than crashing or being misreported as DATE_IN_FUTURE — see
   * [toTransactionDisplayDate] for why a raw server date can't just be assumed to already be in
   * the "dd MMM yyyy" display format. */
  private fun validate(state: AddItemTransactionUiState.Success): TransactionFormError? {
    if (state.transactionDate.isNullOrBlank()) return TransactionFormError.DATE_REQUIRED
    val parsedDate = parseTransactionDate(state.transactionDate) ?: return TransactionFormError.DATE_REQUIRED
    return when {
      parsedDate.isAfter(LocalDate.now()) -> TransactionFormError.DATE_IN_FUTURE
      state.selectedType == null -> TransactionFormError.TYPE_REQUIRED
      state.quantities.values.none { it > 0 } -> TransactionFormError.NO_ITEMS
      else -> null
    }
  }

  private fun parseTransactionDate(date: String): LocalDate? =
    runCatching { LocalDate.parse(date, TransactionDateDisplayFormatter) }.getOrNull()

  private inline fun updateSuccess(transform: (AddItemTransactionUiState.Success) -> AddItemTransactionUiState.Success) {
    _uiState.update { current -> if (current is AddItemTransactionUiState.Success) transform(current) else current }
  }
}
