package org.armman.supervisor.ui.dashboard

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

/** UI state for the Supervisor Dashboard — covers loading, error and success (with a live-refresh flag). */
sealed interface DashboardUiState {
  data object Loading : DashboardUiState

  data object Error : DashboardUiState

  data class Success(
    val data: DashboardData,
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val isRefreshing: Boolean,
  ) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
  private val repository: DashboardRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? DashboardUiState.Success ?: return
    fetch(locationId = locationId, locations = current.locations, showFullLoading = false)
  }

  private fun loadInitial() {
    _uiState.value = DashboardUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val data = repository.getDashboard(selected)
        _uiState.value = DashboardUiState.Success(data, locations, selected, isRefreshing = false)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = DashboardUiState.Error
      }
    }
  }

  private fun fetch(locationId: String?, locations: List<LocationOption>, showFullLoading: Boolean) {
    if (showFullLoading) _uiState.value = DashboardUiState.Loading
    val previous = _uiState.value as? DashboardUiState.Success
    if (previous != null) {
      _uiState.value = previous.copy(selectedLocationId = locationId, isRefreshing = true)
    }
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val data = repository.getDashboard(locationId)
        _uiState.value = DashboardUiState.Success(data, locations, locationId, isRefreshing = false)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = DashboardUiState.Error
      }
    }
  }
}
