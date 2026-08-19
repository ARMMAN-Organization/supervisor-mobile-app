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
import org.armman.supervisor.data.meetingtraining.EventScheduleResult
import org.armman.supervisor.model.LocationOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** Which form field failed validation on submit, so the screen can show the right message. */
enum class ScheduleTrainingFormError { PROJECT_REQUIRED, START_DATE_REQUIRED, INVALID_DATE_RANGE, TOPIC_REQUIRED }

/** UI state for the Schedule Training screen. */
sealed interface ScheduleTrainingUiState {
  data object Loading : ScheduleTrainingUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : ScheduleTrainingUiState

  data class Success(
    val projects: List<LocationOption>,
    val selectedProjectId: String?,
    val startDate: String?,
    val endDate: String?,
    val prePostMarksApplicable: Boolean,
    val remarks: String,
    /** Per FR-SV-2.2, a Training's first session's topic list is collected here, at scheduling
     * time, rather than requiring a separate "Add Topics" step afterward. */
    val catalog: List<TrainingTopic>,
    val selectedTopicIds: Set<String>,
    val formError: ScheduleTrainingFormError?,
    val isSubmitting: Boolean,
    val submitted: Boolean,
    /** Set when [onSubmit]'s network call fails — shown inline (red text) without discarding the
     * form, unlike [Error] which replaces the whole screen and is reserved for [load] failures,
     * where there's no form to preserve. Mirrors AddReasonViewModel's submitErrorMessage. */
    val submitErrorMessage: String? = null,
  ) : ScheduleTrainingUiState
}

@HiltViewModel
class ScheduleTrainingViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<ScheduleTrainingUiState>(ScheduleTrainingUiState.Loading)
  val uiState: StateFlow<ScheduleTrainingUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onProjectSelected(projectId: String) =
    updateSuccess { it.copy(selectedProjectId = projectId, formError = null, submitErrorMessage = null) }

  fun onStartDateSelected(date: String) =
    updateSuccess { it.copy(startDate = date, formError = null, submitErrorMessage = null) }

  fun onEndDateSelected(date: String) =
    updateSuccess { it.copy(endDate = date, formError = null, submitErrorMessage = null) }

  fun onPrePostMarksToggled(checked: Boolean) =
    updateSuccess { it.copy(prePostMarksApplicable = checked, submitErrorMessage = null) }

  fun onRemarksChanged(remarks: String) = updateSuccess { it.copy(remarks = remarks, submitErrorMessage = null) }

  fun onTopicToggled(topicId: String) = updateSuccess { state ->
    val updated = state.selectedTopicIds.toMutableSet().apply {
      if (!add(topicId)) remove(topicId)
    }
    state.copy(selectedTopicIds = updated, formError = null, submitErrorMessage = null)
  }

  fun onSubmit() {
    val state = _uiState.value as? ScheduleTrainingUiState.Success ?: return
    if (state.isSubmitting) return

    val error = validate(state)
    if (error != null) {
      _uiState.value = state.copy(formError = error)
      return
    }

    _uiState.value = state.copy(isSubmitting = true, formError = null, submitErrorMessage = null)
    viewModelScope.launch {
      try {
        val project = state.projects.first { it.id == state.selectedProjectId }
        val request = ScheduleTrainingRequest(
          projectId = project.id,
          projectName = project.name,
          startDate = checkNotNull(state.startDate),
          endDate = state.endDate ?: state.startDate,
          prePostMarksApplicable = state.prePostMarksApplicable,
          remarks = state.remarks,
        )
        val scheduled = repository.scheduleTraining(request)
        val eventId = when (scheduled) {
          is EventScheduleResult.Synced -> scheduled.entry.id
          is EventScheduleResult.QueuedOffline -> scheduled.entry.id
        }
        val topicNames = state.catalog.filter { it.id in state.selectedTopicIds }.map { it.name }
        repository.addGathering(eventId, topicNames, request.startDate)
        _uiState.value = state.copy(isSubmitting = false, submitted = true, submitErrorMessage = null)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Inline, not the full-screen Error state — that would discard everything the user
        // entered. Error stays reserved for load() failures, where there's no form to preserve.
        _uiState.value = state.copy(isSubmitting = false, submitErrorMessage = e.message ?: "")
      }
    }
  }

  private fun load() {
    _uiState.value = ScheduleTrainingUiState.Loading
    viewModelScope.launch {
      try {
        val projects = repository.getProjects()
        val catalog = repository.getTrainingTopicsCatalog()
        _uiState.value = ScheduleTrainingUiState.Success(
          projects = projects,
          selectedProjectId = null,
          startDate = LocalDate.now().format(DATE_FORMATTER),
          endDate = null,
          prePostMarksApplicable = false,
          remarks = "",
          catalog = catalog,
          selectedTopicIds = emptySet(),
          formError = null,
          isSubmitting = false,
          submitted = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = ScheduleTrainingUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private fun validate(state: ScheduleTrainingUiState.Success): ScheduleTrainingFormError? = when {
    state.selectedProjectId == null -> ScheduleTrainingFormError.PROJECT_REQUIRED
    state.startDate.isNullOrBlank() -> ScheduleTrainingFormError.START_DATE_REQUIRED
    state.endDate != null && parseDate(state.endDate) < parseDate(state.startDate) -> ScheduleTrainingFormError.INVALID_DATE_RANGE
    state.selectedTopicIds.isEmpty() -> ScheduleTrainingFormError.TOPIC_REQUIRED
    else -> null
  }

  private fun parseDate(text: String): LocalDate = LocalDate.parse(text, DATE_FORMATTER)

  private inline fun updateSuccess(transform: (ScheduleTrainingUiState.Success) -> ScheduleTrainingUiState.Success) {
    _uiState.update { current -> if (current is ScheduleTrainingUiState.Success) transform(current) else current }
  }
}
