package org.armman.supervisor.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.armman.supervisor.model.LocationOption
import javax.inject.Inject

private const val POLL_INTERVAL_MILLIS = 15_000L

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

  /** Emits once per notification detected as newly-seen by a [repository] call (initial load,
   * location switch, or a poll tick) — the Screen collects this to play the notification sound.
   * A hot [SharedFlow] with no replay: a collector that starts late (e.g. after
   * process restart) doesn't get a backlog of sounds to play at once. */
  private val _newNotificationEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)
  val newNotificationEvents: SharedFlow<Unit> = _newNotificationEvents.asSharedFlow()

  private var loadJob: Job? = null
  private var pollingJob: Job? = null

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

  /** Starts polling for new notifications every [POLL_INTERVAL_MILLIS] while the Dashboard is the
   * visible screen — call from a lifecycle-aware effect (e.g. on resume) and pair with
   * [stopPolling] (e.g. on pause) so it doesn't keep running, and hitting the network, once the
   * user has navigated away. */
  fun startPolling() {
    if (pollingJob?.isActive == true) return
    pollingJob = viewModelScope.launch {
      while (true) {
        delay(POLL_INTERVAL_MILLIS)
        val current = _uiState.value as? DashboardUiState.Success ?: continue
        runCatching { repository.getDashboard(current.selectedLocationId) }
          .onSuccess { data ->
            _uiState.value = current.copy(data = data)
            repeat(data.newlyDetectedNotifications.size) { _newNotificationEvents.tryEmit(Unit) }
          }
        // A failed poll tick is silently ignored — the existing dashboard state is left as-is
        // rather than surfacing an error for a background refresh the user didn't initiate.
      }
    }
  }

  fun stopPolling() {
    pollingJob?.cancel()
    pollingJob = null
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
        repeat(data.newlyDetectedNotifications.size) { _newNotificationEvents.tryEmit(Unit) }
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
        repeat(data.newlyDetectedNotifications.size) { _newNotificationEvents.tryEmit(Unit) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = DashboardUiState.Error
      }
    }
  }
}
