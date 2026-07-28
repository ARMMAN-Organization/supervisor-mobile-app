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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** UI state for the Reschedule Meeting screen. */
sealed interface RescheduleMeetingUiState {
  data object Loading : RescheduleMeetingUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : RescheduleMeetingUiState

  data class Success(
    val originalStartDate: String,
    val originalEndDate: String,
    val newStartDate: String?,
    val newEndDate: String?,
    val invalidRange: Boolean,
    val isSubmitting: Boolean,
    val submitted: Boolean,
  ) : RescheduleMeetingUiState
}

@HiltViewModel
class RescheduleMeetingViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val eventId: String = checkNotNull(savedStateHandle[Routes.MEETING_DETAIL_EVENT_ID_ARG])

  private val _uiState = MutableStateFlow<RescheduleMeetingUiState>(RescheduleMeetingUiState.Loading)
  val uiState: StateFlow<RescheduleMeetingUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onNewStartDateSelected(date: String) = updateSuccess { it.copy(newStartDate = date, invalidRange = false) }

  fun onNewEndDateSelected(date: String) = updateSuccess { it.copy(newEndDate = date, invalidRange = false) }

  fun onSubmit() {
    val state = _uiState.value as? RescheduleMeetingUiState.Success ?: return
    if (state.isSubmitting) return
    val start = state.newStartDate ?: state.originalStartDate
    val end = state.newEndDate ?: state.originalEndDate
    if (LocalDate.parse(end, DATE_FORMATTER) < LocalDate.parse(start, DATE_FORMATTER)) {
      _uiState.value = state.copy(invalidRange = true)
      return
    }

    _uiState.value = state.copy(isSubmitting = true)
    viewModelScope.launch {
      try {
        repository.rescheduleMeeting(eventId, start, end)
        _uiState.value = state.copy(isSubmitting = false, submitted = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RescheduleMeetingUiState.Error(R.string.meeting_training_error_submit, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = RescheduleMeetingUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getEventDetail(eventId)
        check(detail.status == EventStatus.SCHEDULED) { "Event $eventId is not SCHEDULED" }
        _uiState.value = RescheduleMeetingUiState.Success(
          originalStartDate = detail.startDate,
          originalEndDate = detail.endDate,
          newStartDate = null,
          newEndDate = null,
          invalidRange = false,
          isSubmitting = false,
          submitted = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = RescheduleMeetingUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private inline fun updateSuccess(transform: (RescheduleMeetingUiState.Success) -> RescheduleMeetingUiState.Success) {
    _uiState.update { current -> if (current is RescheduleMeetingUiState.Success) transform(current) else current }
  }
}
