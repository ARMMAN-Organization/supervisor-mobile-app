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
        repository.saveAttendance(eventId, state.roster)
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
        val roster = repository.getSakhiRoster(projectIdFor(detail)).map { rosterEntry ->
          AttendanceEntry(rosterEntry.sakhiId, rosterEntry.sakhiName, present = false)
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

  /** The roster is looked up by project id, but [MeetingDetail] only carries the project's display
   * name — resolved here so the repository's project-scoped roster lookup stays intact. */
  private suspend fun projectIdFor(detail: MeetingDetail): String? =
    repository.getProjects().find { it.name == detail.projectName }?.id

  private inline fun updateSuccess(transform: (AttendanceUiState.Success) -> AttendanceUiState.Success) {
    _uiState.update { current -> if (current is AttendanceUiState.Success) transform(current) else current }
  }
}
