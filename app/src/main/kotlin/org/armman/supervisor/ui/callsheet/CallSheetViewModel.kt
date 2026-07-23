package org.armman.supervisor.ui.callsheet

import androidx.annotation.StringRes
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
import org.armman.supervisor.model.LocationOption
import javax.inject.Inject

/** UI state for the Call Sheet list screen — covers loading, error and success. */
sealed interface CallSheetUiState {
  data object Loading : CallSheetUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : CallSheetUiState

  data class Success(
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val sakhiSummaries: List<SakhiCallSummary>,
  ) : CallSheetUiState
}

@HiltViewModel
class CallSheetViewModel @Inject constructor(
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<CallSheetUiState>(CallSheetUiState.Loading)
  val uiState: StateFlow<CallSheetUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? CallSheetUiState.Success ?: return
    fetchSakhiSummaries(locationId = locationId, locations = current.locations)
  }

  /** Reloads the currently selected location's Sakhi summaries — call on returning from a call log. */
  fun onResumed() {
    val current = _uiState.value as? CallSheetUiState.Success ?: return
    fetchSakhiSummaries(locationId = current.selectedLocationId, locations = current.locations)
  }

  private fun loadInitial() {
    _uiState.value = CallSheetUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val summaries = repository.getSakhiSummaries(selected)
        _uiState.value = CallSheetUiState.Success(locations, selected, summaries)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = CallSheetUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }

  private fun fetchSakhiSummaries(locationId: String?, locations: List<LocationOption>) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val summaries = repository.getSakhiSummaries(locationId)
        _uiState.value = CallSheetUiState.Success(locations, locationId, summaries)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = CallSheetUiState.Error(R.string.call_sheet_error_load, e.message)
      }
    }
  }
}
