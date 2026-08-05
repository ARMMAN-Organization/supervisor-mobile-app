package org.armman.supervisor.ui.registrations

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

/** UI state for the Registrations screen — covers loading, error and success. */
sealed interface RegistrationsUiState {
  data object Loading : RegistrationsUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : RegistrationsUiState

  data class Success(
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val sakhis: List<SakhiRegistrationSummary>,
  ) : RegistrationsUiState
}

@HiltViewModel
class RegistrationsViewModel @Inject constructor(
  private val repository: RegistrationsRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<RegistrationsUiState>(RegistrationsUiState.Loading)
  val uiState: StateFlow<RegistrationsUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? RegistrationsUiState.Success ?: return
    fetch(locationId = locationId, locations = current.locations)
  }

  private fun loadInitial() {
    _uiState.value = RegistrationsUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val sakhis = repository.getRegistrations(selected)
        _uiState.value = RegistrationsUiState.Success(locations, selected, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RegistrationsUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }

  private fun fetch(locationId: String?, locations: List<LocationOption>) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val sakhis = repository.getRegistrations(locationId)
        _uiState.value = RegistrationsUiState.Success(locations, locationId, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RegistrationsUiState.Error(R.string.dashboard_error_generic, e.message)
      }
    }
  }
}
