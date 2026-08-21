package org.armman.supervisor.ui.quickresponse

import androidx.annotation.StringRes
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
import javax.inject.Inject

/** UI state for the Quick Response list screen — covers loading, error and success. */
sealed interface QuickResponseUiState {
  data object Loading : QuickResponseUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(@StringRes val fallbackMessageRes: Int, val exceptionMessage: String?) : QuickResponseUiState

  /**
   * [decidingRequestId] is set while a decision call for that card is in flight (disables its
   * buttons); [decisionErrorMessageRes] surfaces a failed decision without discarding the list.
   */
  data class Success(
    val requests: List<QuickResponseRequest>,
    val decidingRequestId: String? = null,
    @StringRes val decisionErrorMessageRes: Int? = null,
  ) : QuickResponseUiState
}

private const val HTTP_CONFLICT = 409
private const val HTTP_NOT_IMPLEMENTED = 501

@HiltViewModel
class QuickResponseViewModel @Inject constructor(
  private val repository: QuickResponseRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow<QuickResponseUiState>(QuickResponseUiState.Loading)
  val uiState: StateFlow<QuickResponseUiState> = _uiState.asStateFlow()

  private var loadJob: Job? = null

  init {
    loadRequests()
  }

  fun onRetry() {
    loadRequests()
  }

  /**
   * Submits [decision] for [requestId] (SRS FR-SV-4.2, 4.4, 4.5, 4.6, 4.7, 4.9 CTAs). [notes] is
   * Closure Review's editable supervisor-notes field, only meaningful on Reject.
   */
  fun onDecide(requestId: String, decision: QuickResponseDecision, notes: String? = null) {
    runDecisionAction(requestId) { repository.decide(requestId, decision, notes) }
  }

  /** Submits [action] for the Missed Visit Escalation card [requestId] (SRS FR-SV-4.3 CTAs).
   * Transfer currently 501s on the backend (pending a roster-removal + Manager-email capability
   * that doesn't exist yet) — surfaced as its own message rather than the generic failure text.
   * Close is fully implemented and should never actually 501, so it keeps the generic message. */
  fun onEscalationAction(requestId: String, action: QuickResponseEscalationAction) {
    val notImplementedMessageRes = if (action == QuickResponseEscalationAction.TRANSFER) {
      R.string.quick_response_error_transfer_unavailable
    } else {
      null
    }
    runDecisionAction(requestId, notImplementedMessageRes = notImplementedMessageRes) {
      repository.decideEscalation(requestId, action)
    }
  }

  /** Acknowledges the EDD Nearing card [requestId] (SRS FR-SV-4.8's single "Okay" CTA). */
  fun onAcknowledgeEddNearing(requestId: String) {
    runDecisionAction(requestId) { repository.acknowledgeEddNearing(requestId) }
  }

  /** Shared in-flight guard / success-removal / error-mapping for every card action, whatever
   * the underlying call. [notImplementedMessageRes] overrides the generic failure message for a
   * 501 response — used by Transfer's known-unbuilt backend capability. */
  private fun runDecisionAction(
    requestId: String,
    @StringRes notImplementedMessageRes: Int? = null,
    action: suspend () -> Unit,
  ) {
    val currentState = _uiState.value as? QuickResponseUiState.Success ?: return
    if (currentState.decidingRequestId != null) return
    _uiState.value = currentState.copy(decidingRequestId = requestId, decisionErrorMessageRes = null)
    viewModelScope.launch {
      try {
        action()
        _uiState.update { state ->
          (state as? QuickResponseUiState.Success)?.copy(
            requests = state.requests.filterNot { it.id == requestId },
            decidingRequestId = null,
          ) ?: state
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val httpStatusCode = (e as? QuickResponseDecisionException)?.httpStatusCode
        val messageRes = when {
          httpStatusCode == HTTP_CONFLICT -> R.string.quick_response_error_decision_conflict
          httpStatusCode == HTTP_NOT_IMPLEMENTED && notImplementedMessageRes != null -> notImplementedMessageRes
          else -> R.string.quick_response_error_decision_failed
        }
        _uiState.update { state ->
          (state as? QuickResponseUiState.Success)?.copy(
            decidingRequestId = null,
            decisionErrorMessageRes = messageRes,
          ) ?: state
        }
      }
    }
  }

  private fun loadRequests() {
    _uiState.value = QuickResponseUiState.Loading
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val requests = repository.getRequests()
        _uiState.value = QuickResponseUiState.Success(requests)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = QuickResponseUiState.Error(R.string.quick_response_error_load, e.message)
      }
    }
  }
}
