package org.armman.supervisor.ui.risksummary

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

/** UI state for the Risk Summary screen — covers loading, error and success. */
sealed interface RiskSummaryUiState {
  data object Loading : RiskSummaryUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : RiskSummaryUiState

  data class Success(
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val sakhis: List<SakhiRiskSummary>,
  ) : RiskSummaryUiState
}

@HiltViewModel
class RiskSummaryViewModel @Inject constructor(
  private val repository: RiskSummaryRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<RiskSummaryUiState>(RiskSummaryUiState.Loading)
  val uiState: StateFlow<RiskSummaryUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? RiskSummaryUiState.Success ?: return
    fetch(locationId = locationId, locations = current.locations)
  }

  private fun loadInitial() {
    _uiState.value = RiskSummaryUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val sakhis = repository.getRiskSummary(selected)
        _uiState.value = RiskSummaryUiState.Success(locations, selected, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RiskSummaryUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }

  private fun fetch(locationId: String?, locations: List<LocationOption>) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val sakhis = repository.getRiskSummary(locationId)
        _uiState.value = RiskSummaryUiState.Success(locations, locationId, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RiskSummaryUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }
}
