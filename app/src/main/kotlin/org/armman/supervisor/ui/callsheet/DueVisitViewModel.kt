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

/** Which Due-Visit-shaped stat a [DueVisitScreen] instance is showing — all three share this
 * ViewModel/repository-call shape (see Call Sheet plan's noted assumption for the latter two). */
enum class DueVisitKind { DUE, EXPIRING_SOON, MISSED }

/** UI state for the "Visit Due" / "Visit 3 Days to expire" / "Missed Visit" drill-down screens. */
sealed interface DueVisitUiState {
  data object Loading : DueVisitUiState

  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : DueVisitUiState

  data class Success(val items: List<DueVisitItem>) : DueVisitUiState
}

@HiltViewModel
class DueVisitViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])
  private val kind: DueVisitKind = DueVisitKind.valueOf(checkNotNull(savedStateHandle[Routes.DUE_VISIT_KIND_ARG]))

  private val _uiState = MutableStateFlow<DueVisitUiState>(DueVisitUiState.Loading)
  val uiState: StateFlow<DueVisitUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  private fun load() {
    _uiState.value = DueVisitUiState.Loading
    viewModelScope.launch {
      try {
        val items = when (kind) {
          DueVisitKind.DUE -> repository.getDueVisits(sakhiId)
          DueVisitKind.EXPIRING_SOON -> repository.getVisitsExpiringSoon(sakhiId)
          DueVisitKind.MISSED -> repository.getMissedVisits(sakhiId)
        }
        _uiState.value = DueVisitUiState.Success(items)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = DueVisitUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
