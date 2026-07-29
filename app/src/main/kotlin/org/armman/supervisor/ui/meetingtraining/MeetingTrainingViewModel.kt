package org.armman.supervisor.ui.meetingtraining

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
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.model.LocationOption
import javax.inject.Inject

/** Which list tab is showing — mirrors the SCHEDULED/COMPLETED tabs on the Meeting & Training screen. */
enum class MeetingTrainingTab { SCHEDULED, COMPLETED }

/** UI state for the Meeting & Training list screen. */
sealed interface MeetingTrainingUiState {
  data object Loading : MeetingTrainingUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : MeetingTrainingUiState

  data class Success(
    val tab: MeetingTrainingTab,
    val projects: List<LocationOption>,
    val selectedProjectId: String?,
    val selectedEventTypes: Set<EventType>,
    val events: List<MeetingEntry>,
  ) : MeetingTrainingUiState
}

@HiltViewModel
class MeetingTrainingViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  private val photoCleanup: EventPhotoCleanup,
) : ViewModel() {
  private val _uiState = MutableStateFlow<MeetingTrainingUiState>(MeetingTrainingUiState.Loading)
  val uiState: StateFlow<MeetingTrainingUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadInitial()
    cleanupOrphanedPhotos()
  }

  /** Opportunistic, best-effort cleanup of event photo files that are no longer referenced by
   * any Room row (e.g. a capture that never made it into [MeetingTrainingRepository.addPhoto]).
   * Runs once per screen load, off the list-loading path so a failure here never blocks it. */
  private fun cleanupOrphanedPhotos() {
    viewModelScope.launch {
      try {
        val referenced = repository.getAllPhotoFilePaths().toSet()
        photoCleanup.deleteUnreferenced(referenced)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Best-effort: a cleanup failure must never surface to the user or block the screen.
      }
    }
  }

  fun onRetry() {
    loadInitial()
  }

  /** Reload the current tab/filters after returning from Schedule Meeting/Training, without
   * flashing back to Loading if the list is already showing. */
  fun refresh() {
    val current = _uiState.value as? MeetingTrainingUiState.Success ?: return loadInitial()
    fetchEvents(current)
  }

  fun onTabSelected(tab: MeetingTrainingTab) {
    val current = _uiState.value as? MeetingTrainingUiState.Success ?: return
    fetchEvents(current.copy(tab = tab))
  }

  fun onProjectSelected(projectId: String?) {
    val current = _uiState.value as? MeetingTrainingUiState.Success ?: return
    fetchEvents(current.copy(selectedProjectId = projectId))
  }

  fun onEventTypeToggled(eventType: EventType) {
    val current = _uiState.value as? MeetingTrainingUiState.Success ?: return
    val updated = current.selectedEventTypes.toMutableSet().apply {
      if (!add(eventType)) remove(eventType)
    }
    fetchEvents(current.copy(selectedEventTypes = updated))
  }

  private fun loadInitial() {
    _uiState.value = MeetingTrainingUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val projects = repository.getProjects()
        val events = repository.getEvents(EventStatus.SCHEDULED)
        _uiState.value = MeetingTrainingUiState.Success(
          tab = MeetingTrainingTab.SCHEDULED,
          projects = projects,
          selectedProjectId = null,
          selectedEventTypes = setOf(EventType.MEETING, EventType.TRAINING),
          events = events,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MeetingTrainingUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun fetchEvents(next: MeetingTrainingUiState.Success) {
    loadJob?.cancel()
    _uiState.value = next
    loadJob = viewModelScope.launch {
      try {
        val status = if (next.tab == MeetingTrainingTab.SCHEDULED) EventStatus.SCHEDULED else EventStatus.COMPLETED
        val events = repository.getEvents(status).filter { entry ->
          (next.selectedProjectId == null || entry.projectName == projectNameFor(next, next.selectedProjectId)) &&
            entry.eventType in next.selectedEventTypes
        }
        _uiState.value = next.copy(events = events)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MeetingTrainingUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun projectNameFor(state: MeetingTrainingUiState.Success, projectId: String?): String? =
    state.projects.find { it.id == projectId }?.name
}
