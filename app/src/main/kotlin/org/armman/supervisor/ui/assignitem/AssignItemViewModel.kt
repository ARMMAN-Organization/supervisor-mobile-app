package org.armman.supervisor.ui.assignitem

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

/** UI state for the Assign Item list screen — covers loading, error and success. */
sealed interface AssignItemUiState {
  data object Loading : AssignItemUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : AssignItemUiState

  data class Success(
    val locations: List<LocationOption>,
    val selectedLocationId: String?,
    val sakhis: List<SakhiOption>,
  ) : AssignItemUiState
}

@HiltViewModel
class AssignItemViewModel @Inject constructor(
  private val repository: AssignItemRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<AssignItemUiState>(AssignItemUiState.Loading)
  val uiState: StateFlow<AssignItemUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
  }

  fun onRetry() {
    loadInitial()
  }

  fun onLocationSelected(locationId: String) {
    val current = _uiState.value as? AssignItemUiState.Success ?: return
    fetchSakhis(locationId = locationId, locations = current.locations)
  }

  private fun loadInitial() {
    _uiState.value = AssignItemUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val locations = repository.getLocations()
        val selected = locations.firstOrNull()?.id
        val sakhis = repository.getSakhis(selected)
        _uiState.value = AssignItemUiState.Success(locations, selected, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AssignItemUiState.Error(R.string.assign_item_error_load_sakhis, e.message)
      }
    }
  }

  private fun fetchSakhis(locationId: String?, locations: List<LocationOption>) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val sakhis = repository.getSakhis(locationId)
        _uiState.value = AssignItemUiState.Success(locations, locationId, sakhis)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AssignItemUiState.Error(R.string.assign_item_error_load_sakhis, e.message)
      }
    }
  }
}
