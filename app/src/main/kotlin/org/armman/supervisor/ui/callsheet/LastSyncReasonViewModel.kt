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

/** UI state for the "Last Sync Date Reason" drill-down screen. */
sealed interface LastSyncReasonUiState {
  data object Loading : LastSyncReasonUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : LastSyncReasonUiState

  /** [reason] is null when no reason has been submitted yet for this Sakhi's last sync date. */
  data class Success(val sakhiName: String, val reason: SyncReasonItem?) : LastSyncReasonUiState
}

@HiltViewModel
class LastSyncReasonViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<LastSyncReasonUiState>(LastSyncReasonUiState.Loading)
  val uiState: StateFlow<LastSyncReasonUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  private fun load() {
    _uiState.value = LastSyncReasonUiState.Loading
    viewModelScope.launch {
      try {
        val sakhi = repository.getSakhiOption(sakhiId)
        val reason = repository.getLastSyncReason(sakhiId)
        _uiState.value = LastSyncReasonUiState.Success(sakhi.name, reason)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = LastSyncReasonUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
