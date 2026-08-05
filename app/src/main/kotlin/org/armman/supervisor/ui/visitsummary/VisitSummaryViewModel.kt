package org.armman.supervisor.ui.visitsummary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.armman.supervisor.model.LocationOption
import javax.inject.Inject

/** UI state for the Visit Summary screen — covers loading, error and success. */
sealed interface VisitSummaryUiState {
  data object Loading : VisitSummaryUiState

  data object Error : VisitSummaryUiState

  data class Success(
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val sakhis: List<SakhiVisitSummary>,
  ) : VisitSummaryUiState
}

@HiltViewModel
class VisitSummaryViewModel @Inject constructor(
  private val repository: VisitSummaryRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<VisitSummaryUiState>(VisitSummaryUiState.Loading)
  val uiState: StateFlow<VisitSummaryUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? VisitSummaryUiState.Success ?: return
    fetch(locationId = locationId, locations = current.locations)
  }

  private fun loadInitial() {
    _uiState.value = VisitSummaryUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val sakhis = repository.getVisitSummary(selected)
        _uiState.value = VisitSummaryUiState.Success(locations, selected, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = VisitSummaryUiState.Error
      }
    }
  }

  private fun fetch(locationId: String?, locations: List<LocationOption>) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val sakhis = repository.getVisitSummary(locationId)
        _uiState.value = VisitSummaryUiState.Success(locations, locationId, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = VisitSummaryUiState.Error
      }
    }
  }
}
