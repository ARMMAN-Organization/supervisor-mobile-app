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
import org.armman.supervisor.ui.navigation.Routes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

/** UI state for the Add Training Topics screen. */
sealed interface AddTrainingTopicsUiState {
  data object Loading : AddTrainingTopicsUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : AddTrainingTopicsUiState

  data class Success(
    val projectName: String,
    val date: String,
    val catalog: List<TrainingTopic>,
    val selectedTopicIds: Set<String>,
    val isSaving: Boolean,
    val saved: Boolean,
  ) : AddTrainingTopicsUiState
}

@HiltViewModel
class AddTrainingTopicsViewModel @Inject constructor(
  private val repository: MeetingTrainingRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val eventId: String = checkNotNull(savedStateHandle[Routes.MEETING_DETAIL_EVENT_ID_ARG])

  private val _uiState = MutableStateFlow<AddTrainingTopicsUiState>(AddTrainingTopicsUiState.Loading)
  val uiState: StateFlow<AddTrainingTopicsUiState> = _uiState.asStateFlow()

  init {
    load()
  }

  fun onRetry() {
    load()
  }

  fun onDateSelected(date: String) = updateSuccess { it.copy(date = date) }

  fun onTopicToggled(topicId: String) = updateSuccess { state ->
    val updated = state.selectedTopicIds.toMutableSet().apply {
      if (!add(topicId)) remove(topicId)
    }
    state.copy(selectedTopicIds = updated)
  }

  fun onSave() {
    val state = _uiState.value as? AddTrainingTopicsUiState.Success ?: return
    if (state.isSaving || state.selectedTopicIds.isEmpty() || state.date.isBlank()) return

    _uiState.value = state.copy(isSaving = true)
    viewModelScope.launch {
      try {
        val topicNames = state.catalog.filter { it.id in state.selectedTopicIds }.map { it.name }
        repository.addGathering(eventId, topicNames, state.date)
        _uiState.value = state.copy(isSaving = false, saved = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AddTrainingTopicsUiState.Error(R.string.meeting_training_error_submit, e.message)
      }
    }
  }

  private fun load() {
    _uiState.value = AddTrainingTopicsUiState.Loading
    viewModelScope.launch {
      try {
        val detail = repository.getEventDetail(eventId)
        val catalog = repository.getTrainingTopicsCatalog()
        _uiState.value = AddTrainingTopicsUiState.Success(
          projectName = detail.projectName,
          date = LocalDate.now().format(DATE_FORMATTER),
          catalog = catalog,
          selectedTopicIds = emptySet(),
          isSaving = false,
          saved = false,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = AddTrainingTopicsUiState.Error(R.string.meeting_training_error_load, e.message)
      }
    }
  }

  private inline fun updateSuccess(transform: (AddTrainingTopicsUiState.Success) -> AddTrainingTopicsUiState.Success) {
    _uiState.update { current -> if (current is AddTrainingTopicsUiState.Success) transform(current) else current }
  }
}
