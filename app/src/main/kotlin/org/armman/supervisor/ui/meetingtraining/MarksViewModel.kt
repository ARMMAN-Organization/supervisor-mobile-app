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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.armman.supervisor.R
import org.armman.supervisor.data.local.MarksType
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** UI state for the Pre/Post Marks screen. */
sealed interface MarksUiState {
  data object Loading : MarksUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : MarksUiState

  data class Success(
    val marksType: MarksType,
    val topics: List<TrainingTopic>,
    val selectedTopicId: String?,
    val roster: List<MarksEntry>,
    /** True while [MarksViewModel.loadRosterForTopic] is in flight — Save/Complete must not act
     * on a roster that may still be the empty placeholder from [MarksViewModel.onTopicSelected]. */
    val isLoadingRoster: Boolean,
    val completed: Boolean,
    val isSaving: Boolean,
    val isCompleting: Boolean,
    val saved: Boolean,
    val invalidValueError: Boolean,
  ) : MarksUiState
}

@HiltViewModel
class MarksViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val eventId: String = checkNotNull(savedStateHandle[Routes.MEETING_DETAIL_EVENT_ID_ARG])
  private val gatheringId: String = checkNotNull(savedStateHandle[Routes.GATHERING_ID_ARG])
  private val marksType: MarksType = MarksType.valueOf(checkNotNull(savedStateHandle[Routes.MARKS_TYPE_ARG]))

  private val _uiState = MutableStateFlow<MarksUiState>(MarksUiState.Loading)
  val uiState: StateFlow<MarksUiState> = _uiState.asStateFlow()

  /** Tracks the in-flight [loadRosterForTopic] call so switching topics cancels the previous
   * topic's still-pending load instead of letting two loads race and clobber each other. */
  private var rosterLoadJob: Job? = null

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onTopicSelected(topicId: String) {
    val state = _uiState.value as? MarksUiState.Success ?: return
    if (topicId == state.selectedTopicId) return
    _uiState.value = state.copy(selectedTopicId = topicId, roster = emptyList(), isLoadingRoster = true, invalidValueError = false)
    rosterLoadJob?.cancel()
    rosterLoadJob = viewModelScope.launch { loadRosterForTopic(topicId) }
  }

  fun onMarksChanged(sakhiId: String, rawValue: String) = updateSuccess { state ->
    val marks = rawValue.toIntOrNull()
    if (rawValue.isNotEmpty() && (marks == null || marks !in MIN_MARKS..MAX_MARKS)) {
      return@updateSuccess state.copy(invalidValueError = true)
    }
    state.copy(
      roster = state.roster.map { if (it.sakhiId == sakhiId) it.copy(marks = marks) else it },
      invalidValueError = false,
    )
  }

  fun onSave() {
    val state = _uiState.value as? MarksUiState.Success ?: return
    val topicId = state.selectedTopicId ?: return
    if (state.isSaving || state.isCompleting || state.isLoadingRoster || state.completed || state.invalidValueError) return

    _uiState.value = state.copy(isSaving = true)
    viewModelScope.launch {
      try {
        repository.saveMarks(eventId, topicId, marksType, state.roster)
        _uiState.value = state.copy(isSaving = false, saved = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MarksUiState.Error(R.string.marks_error_submit, e.message)
      }
    }
  }

  fun onCompleteAndClose() {
    val state = _uiState.value as? MarksUiState.Success ?: return
    val topicId = state.selectedTopicId ?: return
    if (state.isSaving || state.isCompleting || state.isLoadingRoster || state.completed) return

    _uiState.value = state.copy(isCompleting = true)
    viewModelScope.launch {
      try {
        repository.saveMarks(eventId, topicId, marksType, state.roster)
        repository.completeMarks(eventId, topicId, marksType)
        _uiState.value = state.copy(isCompleting = false, completed = true, saved = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MarksUiState.Error(R.string.marks_error_submit, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = MarksUiState.Loading
    viewModelScope.launch {
      try {
        val topics = repository.getTopicsForGathering(gatheringId)
        _uiState.value = MarksUiState.Success(
          marksType = marksType,
          topics = topics,
          selectedTopicId = null,
          roster = emptyList(),
          isLoadingRoster = false,
          completed = false,
          isSaving = false,
          isCompleting = false,
          saved = false,
          invalidValueError = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = MarksUiState.Error(R.string.marks_error_load, e.message)
      }
    }
  }

  private suspend fun loadRosterForTopic(topicId: String) {
    try {
      val detail = repository.getEventDetail(eventId)
      val projectId = repository.getProjects().find { it.name == detail.projectName }?.id
      val roster = repository.getSakhiRoster(projectId)
      val existingMarks = repository.getMarks(topicId, marksType).associateBy { it.sakhiId }
      val gatheringTopics = repository.getTopicsForGathering(gatheringId)
      val completed = detail.gatherings
        .find { it.gatheringId == gatheringId }
        ?.topics?.find { it.topicId == topicId }
        ?.let { if (marksType == MarksType.PRE) it.preMarksCompleted else it.postMarksCompleted }
        ?: false

      updateSuccess { state ->
        if (state.selectedTopicId != topicId) return@updateSuccess state
        state.copy(
          topics = gatheringTopics,
          roster = roster.map { entry -> existingMarks[entry.sakhiId] ?: MarksEntry(entry.sakhiId, entry.sakhiName, marks = null) },
          isLoadingRoster = false,
          completed = completed,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      _uiState.value = MarksUiState.Error(R.string.marks_error_load, e.message)
    }
  }

  private inline fun updateSuccess(transform: (MarksUiState.Success) -> MarksUiState.Success) {
    _uiState.update { current -> if (current is MarksUiState.Success) transform(current) else current }
  }

  private companion object {
    const val MIN_MARKS = 0
    const val MAX_MARKS = 100
  }
}
