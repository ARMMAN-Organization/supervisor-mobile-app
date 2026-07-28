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
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.data.local.EventStatus
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the Meeting Detail screen. */
sealed interface MeetingDetailUiState {
  data object Loading : MeetingDetailUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : MeetingDetailUiState

  data class Success(
    val detail: MeetingDetail,
    /** True while a photo/attendance/complete/cancel action is in flight — prevents duplicate taps. */
    val isActionInProgress: Boolean = false,
    /** Set when Complete was attempted without a photo (FR-SV-2.3: "cannot be marked complete without a photo"). */
    val completeBlockedNoPhoto: Boolean = false,
  ) : MeetingDetailUiState
}

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val eventId: String = checkNotNull(savedStateHandle[Routes.MEETING_DETAIL_EVENT_ID_ARG])

  private val _uiState = MutableStateFlow<MeetingDetailUiState>(MeetingDetailUiState.Loading)
  val uiState: StateFlow<MeetingDetailUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  /** Reload after returning from Attendance/Reschedule/photo capture. */
  fun refresh() {
    load()
  }

  fun onAddPhoto(filePath: String) = runGuardedAction { repository.addPhoto(eventId, filePath) }

  fun onCancel() = runGuardedAction { repository.cancelMeeting(eventId) }

  fun onComplete() {
    val state = _uiState.value as? MeetingDetailUiState.Success ?: return
    if (state.isActionInProgress) return
    if (state.detail.photoPaths.isEmpty()) {
      _uiState.value = state.copy(completeBlockedNoPhoto = true)
      return
    }
    runGuardedAction { repository.completeMeeting(eventId) }
  }

  private fun runGuardedAction(action: suspend () -> Unit) {
    val state = _uiState.value as? MeetingDetailUiState.Success ?: return
    if (state.isActionInProgress || state.detail.status != EventStatus.SCHEDULED) return

    _uiState.value = state.copy(isActionInProgress = true, completeBlockedNoPhoto = false)
    viewModelScope.launch {
      try {
        action()
        load()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MeetingDetailUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = MeetingDetailUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getEventDetail(eventId)
        _uiState.value = MeetingDetailUiState.Success(detail)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MeetingDetailUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }
}
