package org.armman.supervisor.ui.callsheet

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
import org.armman.supervisor.ui.navigation.Routes
import javax.inject.Inject

/** Form state for the Call Outcome screen. */
data class CallOutcomeFormState(
  val connected: CallConnected? = null,
  val successOutcome: SuccessOutcome? = null,
  val responder: CallResponder? = null,
  val durationMinutesText: String = "",
  val notes: String = "",
  val followUpAction: String = "",
  val failureReason: FailureReason? = null,
  val isSubmitting: Boolean = false,
  val validationErrorRes: Int? = null,
  val submitErrorMessage: String? = null,
  val submitted: Boolean = false,
) {
  val showResponder: Boolean get() = connected == CallConnected.YES && successOutcome == SuccessOutcome.PICKED_UP_TALKED
}

@HiltViewModel
class CallOutcomeViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val sakhiId: String = checkNotNull(savedStateHandle[Routes.CALL_SHEET_SAKHI_ID_ARG])

  private val _formState = MutableStateFlow(CallOutcomeFormState())
  val formState: StateFlow<CallOutcomeFormState> = _formState.asStateFlow()

  private var submitJob: Job? = null

  fun onConnectedChanged(connected: CallConnected) {
    _formState.update {
      it.copy(
        connected = connected,
        successOutcome = null,
        responder = null,
        durationMinutesText = "",
        notes = "",
        followUpAction = "",
        failureReason = null,
        validationErrorRes = null,
      )
    }
  }

  fun onSuccessOutcomeChanged(outcome: SuccessOutcome) {
    _formState.update {
      it.copy(
        successOutcome = outcome,
        responder = if (outcome == SuccessOutcome.PICKED_UP_TALKED) it.responder else null,
        validationErrorRes = null,
      )
    }
  }

  fun onResponderChanged(responder: CallResponder) {
    _formState.update { it.copy(responder = responder, validationErrorRes = null) }
  }

  fun onFailureReasonChanged(reason: FailureReason) {
    _formState.update { it.copy(failureReason = reason, validationErrorRes = null) }
  }

  fun onDurationChanged(text: String) {
    _formState.update { it.copy(durationMinutesText = text, validationErrorRes = null) }
  }

  fun onNotesChanged(text: String) {
    _formState.update { it.copy(notes = text) }
  }

  fun onFollowUpActionChanged(text: String) {
    _formState.update { it.copy(followUpAction = text) }
  }

  fun onSubmit() {
    if (_formState.value.isSubmitting) return
    val validationError = validate(_formState.value)
    if (validationError != null) {
      _formState.update { it.copy(validationErrorRes = validationError) }
      return
    }
    submitJob?.cancel()
    submitJob = viewModelScope.launch {
      _formState.update { it.copy(isSubmitting = true, submitErrorMessage = null) }
      try {
        val state = _formState.value
        repository.logCall(
          CallLogSubmission(
            sakhiId = sakhiId,
            connected = checkNotNull(state.connected),
            successOutcome = state.successOutcome,
            failureReason = state.failureReason,
            responder = state.responder,
            durationMinutes = state.durationMinutesText.toIntOrNull(),
            notes = state.notes.ifBlank { null },
            followUpAction = state.followUpAction.ifBlank { null },
          ),
        )
        _formState.update { it.copy(isSubmitting = false, submitted = true) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.update { it.copy(isSubmitting = false, submitErrorMessage = e.message) }
      }
    }
  }

  private fun validate(state: CallOutcomeFormState): Int? {
    if (state.connected == null) return R.string.call_outcome_error_connected_required
    return when (state.connected) {
      CallConnected.YES -> when {
        state.successOutcome == null -> R.string.call_outcome_error_success_outcome_required
        state.successOutcome == SuccessOutcome.PICKED_UP_TALKED && state.responder == null ->
          R.string.call_outcome_error_responder_required
        state.durationMinutesText.isNotBlank() && state.durationMinutesText.toIntOrNull() == null ->
          R.string.call_outcome_error_duration_invalid
        else -> null
      }
      CallConnected.NO -> if (state.failureReason == null) R.string.call_outcome_error_failure_reason_required else null
    }
  }
}
