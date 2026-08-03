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
import javax.inject.Inject

/** Which form field failed validation on submit, so the screen can show the right message. */
enum class TransactionFormError { DATE_REQUIRED, TYPE_REQUIRED, NO_ITEMS }

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
        val items = repository.getInventoryItems()
        val editing = editTransactionId?.let { id ->
          repository.getTransactions(sakhiId).firstOrNull { id in it.ids }
            ?: error("Unknown transaction id: $id")
        }
        val itemIdsByName = items.associate { it.name to it.id }
        val editingQuantitiesByItemId = editing?.items?.associate { entry ->
          val itemId = itemIdsByName[entry.itemName] ?: error("Unknown item name: ${entry.itemName}")
          itemId to entry.quantity
        }.orEmpty()
        editingRowIdsByItemId = editing?.items?.associate { entry ->
          val itemId = itemIdsByName[entry.itemName] ?: error("Unknown item name: ${entry.itemName}")
          itemId to entry.id
        }.orEmpty()

        _uiState.value = AddItemTransactionUiState.Success(
          sakhiName = detail.sakhiName,
          programs = programs,
          items = items,
          selectedProgramId = programs.firstOrNull { it.name == detail.projectName }?.id ?: programs.firstOrNull()?.id,
          selectedType = editing?.transactionType,
          transactionDate = editing?.date,
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
   * changed — adding a brand-new item id isn't supported, since there is no API to add/remove
   * item lines on an existing transaction. */
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
          repository.updateTransaction(submission)
        } else {
          repository.submitTransaction(submission)
        }
        _uiState.value = state.copy(isSubmitting = false, submitted = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AddItemTransactionUiState.Error(R.string.add_item_error_submit, e.message)
      }
    }
  }

  private fun validate(state: AddItemTransactionUiState.Success): TransactionFormError? = when {
    state.transactionDate.isNullOrBlank() -> TransactionFormError.DATE_REQUIRED
    state.selectedType == null -> TransactionFormError.TYPE_REQUIRED
    state.quantities.values.none { it > 0 } -> TransactionFormError.NO_ITEMS
    else -> null
  }

  private inline fun updateSuccess(transform: (AddItemTransactionUiState.Success) -> AddItemTransactionUiState.Success) {
    _uiState.update { current -> if (current is AddItemTransactionUiState.Success) transform(current) else current }
  }
}
