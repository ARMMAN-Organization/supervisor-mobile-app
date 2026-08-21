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

/** UI state for the "Followup Form Pending" drill-down screen. */
sealed interface FollowupPendingUiState {
  data object Loading : FollowupPendingUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : FollowupPendingUiState

  data class Success(val items: List<FollowupPendingItem>) : FollowupPendingUiState
}

@HiltViewModel
class FollowupPendingViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])

  private val _uiState = MutableStateFlow<FollowupPendingUiState>(FollowupPendingUiState.Loading)
  val uiState: StateFlow<FollowupPendingUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  /** Re-fetch on resume (e.g. returning from Add Reason after submitting one) so an item that's
   * just been actioned drops out of the list instead of lingering until the user backs out and
   * back in. Only refreshes once a load has already succeeded, matching [CallSheetViewModel]. */
  fun onResumed() {
    if (_uiState.value is FollowupPendingUiState.Success) load()
  }

  private fun load() {
    _uiState.value = FollowupPendingUiState.Loading
    viewModelScope.launch {
      try {
        val items = repository.getFollowupPending(sakhiId)
        _uiState.value = FollowupPendingUiState.Success(items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = FollowupPendingUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
