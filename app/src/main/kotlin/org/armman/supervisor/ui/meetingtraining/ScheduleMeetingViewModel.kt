package org.armman.supervisor.ui.meetingtraining

import androidx.annotation.StringRes
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
import org.armman.supervisor.model.LocationOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** Which form field failed validation on submit, so the screen can show the right message. */
enum class ScheduleMeetingFormError { PROJECT_REQUIRED, START_DATE_REQUIRED, INVALID_DATE_RANGE }

/** UI state for the Schedule Meeting screen. */
sealed interface ScheduleMeetingUiState {
  data object Loading : ScheduleMeetingUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : ScheduleMeetingUiState

  data class Success(
    val projects: List<LocationOption>,
    val selectedProjectId: String?,
    val startDate: String?,
    val endDate: String?,
    val remarks: String,
    val formError: ScheduleMeetingFormError?,
    val isSubmitting: Boolean,
    val submitted: Boolean,
  ) : ScheduleMeetingUiState
}

@HiltViewModel
class ScheduleMeetingViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ScheduleMeetingUiState>(ScheduleMeetingUiState.Loading)
  val uiState: StateFlow<ScheduleMeetingUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onProjectSelected(projectId: String) = updateSuccess { it.copy(selectedProjectId = projectId, formError = null) }

  fun onStartDateSelected(date: String) = updateSuccess { it.copy(startDate = date, formError = null) }

  fun onEndDateSelected(date: String) = updateSuccess { it.copy(endDate = date, formError = null) }

  fun onRemarksChanged(remarks: String) = updateSuccess { it.copy(remarks = remarks) }

  fun onSubmit() {
    val state = _uiState.value as? ScheduleMeetingUiState.Success ?: return
    if (state.isSubmitting) return

    val error = validate(state)
    if (error != null) {
      _uiState.value = state.copy(formError = error)
      return
    }

    _uiState.value = state.copy(isSubmitting = true, formError = null)
    viewModelScope.launch {
      try {
        val project = state.projects.first { it.id == state.selectedProjectId }
        val request = ScheduleMeetingRequest(
          projectId = project.id,
          projectName = project.name,
          startDate = checkNotNull(state.startDate),
          endDate = state.endDate ?: state.startDate,
          remarks = state.remarks,
        )
        repository.scheduleMeeting(request)
        _uiState.value = state.copy(isSubmitting = false, submitted = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = ScheduleMeetingUiState.Error(R.string.meeting_training_error_submit, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = ScheduleMeetingUiState.Loading
    viewModelScope.launch {
      try {
        val projects = repository.getProjects()
        _uiState.value = ScheduleMeetingUiState.Success(
          projects = projects,
          selectedProjectId = null,
          startDate = null,
          endDate = null,
          remarks = "",
          formError = null,
          isSubmitting = false,
          submitted = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = ScheduleMeetingUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun validate(state: ScheduleMeetingUiState.Success): ScheduleMeetingFormError? = when {
    state.selectedProjectId == null -> ScheduleMeetingFormError.PROJECT_REQUIRED
    state.startDate.isNullOrBlank() -> ScheduleMeetingFormError.START_DATE_REQUIRED
    state.endDate != null && parseDate(state.endDate) < parseDate(state.startDate) -> ScheduleMeetingFormError.INVALID_DATE_RANGE
    else -> null
  }

  private fun parseDate(text: String): LocalDate = LocalDate.parse(text, DATE_FORMATTER)

  private inline fun updateSuccess(transform: (ScheduleMeetingUiState.Success) -> ScheduleMeetingUiState.Success) {
    _uiState.update { current -> if (current is ScheduleMeetingUiState.Success) transform(current) else current }
  }
}
