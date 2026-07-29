package org.armman.supervisor.ui.meetingtraining

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the Attendance screen. */
sealed interface AttendanceUiState {
  data object Loading : AttendanceUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : AttendanceUiState

  data class Success(
    val roster: List<AttendanceEntry>,
    val readOnly: Boolean,
    val isSaving: Boolean,
    val saved: Boolean,
  ) : AttendanceUiState {
    val presentCount: Int get() = roster.count { it.present }
  }
}

@HiltViewModel
class AttendanceViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val eventId: String = checkNotNull(savedStateHandle[Routes.MEETING_DETAIL_EVENT_ID_ARG])

  /** Set only for Training's per-Gathering-Date attendance; null for Meeting's single per-event
   * attendance, which keeps its original behavior unchanged. */
  private val gatheringId: String? = savedStateHandle[Routes.GATHERING_ID_ARG]

  private val _uiState = MutableStateFlow<AttendanceUiState>(AttendanceUiState.Loading)
  val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onToggle(sakhiId: String) = updateSuccess { state ->
    if (state.readOnly) return@updateSuccess state
    state.copy(roster = state.roster.map { if (it.sakhiId == sakhiId) it.copy(present = !it.present) else it })
  }

  fun onMarkAllPresent() = updateSuccess { state ->
    if (state.readOnly) return@updateSuccess state
    state.copy(roster = state.roster.map { it.copy(present = true) })
  }

  fun onSave() {
    val state = _uiState.value as? AttendanceUiState.Success ?: return
    if (state.isSaving || state.readOnly) return

    _uiState.value = state.copy(isSaving = true)
    viewModelScope.launch {
      try {
        val gathering = gatheringId
        if (gathering != null) {
          repository.saveGatheringAttendance(eventId, gathering, state.roster)
        } else {
          repository.saveAttendance(eventId, state.roster)
        }
        _uiState.value = state.copy(isSaving = false, saved = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AttendanceUiState.Error(R.string.meeting_training_error_submit, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = AttendanceUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getEventDetail(eventId)
        val gathering = gatheringId
        val roster = if (gathering != null) {
          loadGatheringRoster(detail, gathering)
        } else {
          loadMeetingRoster(detail)
        }
        _uiState.value = AttendanceUiState.Success(
          roster = roster,
          readOnly = detail.status != EventStatus.SCHEDULED,
          isSaving = false,
          saved = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AttendanceUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  /** Merges the project roster with any attendance already saved for this gathering, so
   * reopening the screen shows prior selections instead of resetting everyone to absent. */
  private suspend fun loadGatheringRoster(detail: MeetingDetail, gatheringId: String): List<AttendanceEntry> {
    val saved = repository.getGatheringAttendanceRoster(gatheringId).associateBy { it.sakhiId }
    return repository.getSakhiRoster(projectIdFor(detail)).map { rosterEntry ->
      saved[rosterEntry.sakhiId] ?: AttendanceEntry(rosterEntry.sakhiId, rosterEntry.sakhiName, present = false)
    }
  }

  /** Merges the project roster with any attendance already saved for this Meeting, so
   * reopening the screen shows prior selections instead of resetting everyone to absent. */
  private suspend fun loadMeetingRoster(detail: MeetingDetail): List<AttendanceEntry> {
    val saved = repository.getSavedAttendance(eventId).associateBy { it.sakhiId }
    return repository.getSakhiRoster(projectIdFor(detail)).map { rosterEntry ->
      saved[rosterEntry.sakhiId] ?: AttendanceEntry(rosterEntry.sakhiId, rosterEntry.sakhiName, present = false)
    }
  }

  /** The roster is looked up by project id, but [MeetingDetail] only carries the project's display
   * name — resolved here so the repository's project-scoped roster lookup stays intact. */
  private suspend fun projectIdFor(detail: MeetingDetail): String? =
    repository.getProjects().find { it.name == detail.projectName }?.id

  private inline fun updateSuccess(transform: (AttendanceUiState.Success) -> AttendanceUiState.Success) {
    _uiState.update { current -> if (current is AttendanceUiState.Success) transform(current) else current }
  }
}
