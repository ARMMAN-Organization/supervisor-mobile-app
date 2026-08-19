package org.armman.supervisor.ui.meetingtraining

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
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

  /** The in-flight mutating action (add photo/cancel/complete), if any. Never cancelled by a
   * plain [refresh] — a resume racing an action must not abort the action's own mutation. */
  private var actionJob: Job? = null

  /** The in-flight read-only reload from [load]. Safe to cancel/replace freely since it never
   * mutates data — superseding it just means one fewer redundant read. */
  private var refreshJob: Job? = null

  /** The action that produced the current [MeetingDetailUiState.Error], so [onRetry] can resume it
   * instead of just reloading — critical for [onComplete]: a failed complete may have already
   * uploaded and attached its photo, and a plain reload's "Retry" would otherwise be a dead end,
   * since the underlying HTTP failure (e.g. missing attendance) never gets re-attempted. Null when
   * the error came from [load] itself, where a plain reload is already the correct retry. Cleared
   * whenever a fresh [Success]/[load] supersedes the error it belongs to. */
  private var failedAction: (suspend () -> Unit)? = null

  /** The last [MeetingDetailUiState.Success] seen, kept so [onRetry] can re-enter
   * [runGuardedAction] after the state has moved to [MeetingDetailUiState.Error] — that state
   * transition would otherwise lose the base [Success] a guarded retry needs to run from. */
  private var lastSuccess: MeetingDetailUiState.Success? = null

  init {
    load(showLoading = true)
  }

  fun onRetry() {
    val action = failedAction
    val base = lastSuccess
    if (action != null && base != null) runGuardedAction(base, action) else load(showLoading = true)
  }

  /** Reload after returning from Attendance/Reschedule/photo capture. Skips the Loading state
   * when data is already showing, so a resume-triggered refresh doesn't flash the spinner. A
   * no-op while an action is in flight — the action's own completion already triggers a reload. */
  fun refresh() {
    if (actionJob?.isActive == true) return
    load(showLoading = _uiState.value !is MeetingDetailUiState.Success)
  }

  fun onAddPhoto(filePath: String) {
    val state = _uiState.value as? MeetingDetailUiState.Success ?: return
    runGuardedAction(state) { repository.addPhoto(eventId, filePath) }
  }

  fun onCancel() {
    val state = _uiState.value as? MeetingDetailUiState.Success ?: return
    runGuardedAction(state) { repository.cancelMeeting(eventId) }
  }

  fun onComplete() {
    val state = _uiState.value as? MeetingDetailUiState.Success ?: return
    if (state.isActionInProgress) return
    if (state.detail.photoPaths.isEmpty()) {
      _uiState.value = state.copy(completeBlockedNoPhoto = true)
      return
    }
    runGuardedAction(state) { repository.completeMeeting(eventId) }
  }

  /** [base] is the [Success] state to run from — normally the current state, but on a retry after
   * [onRetry] moved the state to [Error], it's the last [Success] seen ([lastSuccess]) instead. */
  private fun runGuardedAction(base: MeetingDetailUiState.Success, action: suspend () -> Unit) {
    if (base.isActionInProgress || base.detail.status != EventStatus.SCHEDULED) return

    _uiState.value = base.copy(isActionInProgress = true, completeBlockedNoPhoto = false)
    refreshJob?.cancel()
    actionJob = viewModelScope.launch {
      try {
        action()
        val detail = repository.getEventDetail(eventId)
        setSuccess(detail)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failedAction = action
        _uiState.value = MeetingDetailUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun load(showLoading: Boolean) {
    if (showLoading) _uiState.value = MeetingDetailUiState.Loading
    refreshJob?.cancel()
    refreshJob = viewModelScope.launch {
      try {
        val detail = repository.getEventDetail(eventId)
        setSuccess(detail)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failedAction = null
        _uiState.value = MeetingDetailUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun setSuccess(detail: MeetingDetail) {
    failedAction = null
    val success = MeetingDetailUiState.Success(detail)
    lastSuccess = success
    _uiState.value = success
  }
}
