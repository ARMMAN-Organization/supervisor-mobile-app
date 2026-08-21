package org.armman.supervisor.ui.callsheet

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

/** UI state for the "Closure Form Pending" drill-down screen. */
sealed interface ClosurePendingUiState {
  data object Loading : ClosurePendingUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : ClosurePendingUiState

  data class Success(val items: List<ClosurePendingItem>) : ClosurePendingUiState
}

@HiltViewModel
class ClosurePendingViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<ClosurePendingUiState>(ClosurePendingUiState.Loading)
  val uiState: StateFlow<ClosurePendingUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  private fun load() {
    _uiState.value = ClosurePendingUiState.Loading
    viewModelScope.launch {
      try {
        val items = repository.getClosurePending(sakhiId)
        _uiState.value = ClosurePendingUiState.Success(items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = ClosurePendingUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
