package org.armman.supervisor.ui.callsheet

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the Call History screen — covers loading, error and success. */
sealed interface CallHistoryUiState {
  data object Loading : CallHistoryUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : CallHistoryUiState

  data class Success(val sakhi: SakhiOption, val entries: List<CallLogEntry>) : CallHistoryUiState
}

@HiltViewModel
class CallHistoryViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<CallHistoryUiState>(CallHistoryUiState.Loading)
  val uiState: StateFlow<CallHistoryUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  /** Reloads history — call when returning from the Call Outcome form after a submit. */
  fun onResumed() {
    load()
  }

  private fun load() {
    _uiState.value = CallHistoryUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val sakhi = repository.getSakhiOption(sakhiId)
        val entries = repository.getCallHistory(sakhiId)
        _uiState.value = CallHistoryUiState.Success(sakhi, entries)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = CallHistoryUiState.Error(R.string.call_history_error_load, e.message)
      }
    }
  }
}
