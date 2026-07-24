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
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the Assign Item to Sakhi detail screen — covers loading, error and success. */
sealed interface AssignItemDetailUiState {
  data object Loading : AssignItemDetailUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : AssignItemDetailUiState

  data class Success(val detail: SakhiDetail, val transactions: List<TransactionEntry>) : AssignItemDetailUiState
}

@HiltViewModel
class AssignItemDetailViewModel @Inject constructor(
  private val repository: AssignItemRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<AssignItemDetailUiState>(AssignItemDetailUiState.Loading)
  val uiState: StateFlow<AssignItemDetailUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  /** Reload after returning from the Add screen with a newly-submitted transaction. */
  fun refresh() {
    load()
  }

  fun onDeleteTransaction(transactionId: String) {
    viewModelScope.launch {
      try {
        repository.deleteTransaction(sakhiId, transactionId)
        val detail = repository.getSakhiDetail(sakhiId)
        val transactions = repository.getTransactions(sakhiId)
        _uiState.value = AssignItemDetailUiState.Success(detail, transactions)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AssignItemDetailUiState.Error(R.string.assign_item_error_delete_transaction, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = AssignItemDetailUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getSakhiDetail(sakhiId)
        val transactions = repository.getTransactions(sakhiId)
        _uiState.value = AssignItemDetailUiState.Success(detail, transactions)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AssignItemDetailUiState.Error(R.string.assign_item_error_load_detail, e.message)
      }
    }
  }
}
