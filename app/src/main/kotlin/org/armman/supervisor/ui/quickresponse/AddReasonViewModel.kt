package org.armman.supervisor.ui.quickresponse

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

/** Form state for the Add Reason screen. */
data class AddReasonFormState(
  val selectedReason: ReasonOption? = null,
  val isSubmitting: Boolean = false,
  val validationErrorRes: Int? = null,
  val submitErrorMessage: String? = null,
  val submitted: Boolean = false,
)

@HiltViewModel
class AddReasonViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: QuickResponseRepository,
) : ViewModel() {
  private val requestId: String = checkNotNull(savedStateHandle[Routes.QUICK_RESPONSE_REQUEST_ID_ARG])

  private val _formState = MutableStateFlow(AddReasonFormState())
  val formState: StateFlow<AddReasonFormState> = _formState.asStateFlow()

  private var submitJob: Job? = null

  fun onReasonSelected(reason: ReasonOption) {
    _formState.update { it.copy(selectedReason = reason, validationErrorRes = null) }
  }

  fun onSubmit() {
    if (_formState.value.isSubmitting) return
    val reason = _formState.value.selectedReason
    if (reason == null) {
      _formState.update { it.copy(validationErrorRes = R.string.quick_response_error_reason_required) }
      return
    }
    submitJob?.cancel()
    submitJob = viewModelScope.launch {
      _formState.update { it.copy(isSubmitting = true, submitErrorMessage = null) }
      try {
        repository.submitReason(requestId, reason)
        _formState.update { it.copy(isSubmitting = false, submitted = true) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.update { it.copy(isSubmitting = false, submitErrorMessage = e.message) }
      }
    }
  }
}
