package org.armman.supervisor.ui.callsheet

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
import org.armman.supervisor.ui.navigation.Routes
import java.net.URLDecoder
import javax.inject.Inject

/** One selectable reason option in [AddReasonScreen]'s dropdown — [code] is the wire value sent to
 * [CallSheetRepository.submitReason], [labelRes] its EN/MR display string. */
data class ReasonChoice(val code: String, @StringRes val labelRes: Int)

/** The fixed reason lookup for each [ReasonContext] — screenshot-derived, no backend lookup
 * endpoint exists yet (see [CallSheetRepository]). */
fun ReasonContext.choices(): List<ReasonChoice> = when (this) {
  ReasonContext.FOLLOWUP_PENDING -> FollowupPendingReason.entries.map { ReasonChoice(it.name, it.labelRes()) }
  ReasonContext.CLOSURE_PENDING -> ClosurePendingReason.entries.map { ReasonChoice(it.name, it.labelRes()) }
  ReasonContext.LAST_SYNC -> LastSyncReason.entries.map { ReasonChoice(it.name, it.labelRes()) }
}

/** Form state for [AddReasonScreen]. */
data class AddReasonFormState(
  val context: ReasonContext,
  val selectedReason: ReasonChoice? = null,
  val remark: String = "",
  val isSubmitting: Boolean = false,
  val validationErrorRes: Int? = null,
  val submitErrorMessage: String? = null,
  val submitted: Boolean = false,
)

@HiltViewModel
class AddReasonViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val repository: CallSheetRepository,
) : ViewModel() {
  private val context: ReasonContext = ReasonContext.valueOf(checkNotNull(savedStateHandle[Routes.REASON_CONTEXT_ARG]))
  private val itemId: String? = savedStateHandle.get<String>(Routes.REASON_ITEM_ID_ARG)?.let(::decode)
  private val sakhiId: String? = savedStateHandle.get<String>(Routes.CALL_SHEET_SAKHI_ID_ARG)?.let(::decode)

  private val _formState = MutableStateFlow(AddReasonFormState(context = context))
  val formState: StateFlow<AddReasonFormState> = _formState.asStateFlow()

  fun onReasonSelected(choice: ReasonChoice) {
    _formState.value = _formState.value.copy(selectedReason = choice, validationErrorRes = null)
  }

  fun onRemarkChanged(remark: String) {
    _formState.value = _formState.value.copy(remark = remark)
  }

  fun onSubmit() {
    val current = _formState.value
    if (current.isSubmitting) return
    val reason = current.selectedReason
    if (reason == null) {
      _formState.value = current.copy(validationErrorRes = R.string.add_reason_error_reason_required)
      return
    }
    _formState.value = current.copy(isSubmitting = true, submitErrorMessage = null)
    viewModelScope.launch {
      try {
        repository.submitReason(
          ReasonSubmission(
            context = context,
            itemId = itemId,
            sakhiId = sakhiId,
            reasonCode = reason.code,
            remark = current.remark.ifBlank { null },
          ),
        )
        _formState.value = _formState.value.copy(isSubmitting = false, submitted = true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _formState.value = _formState.value.copy(
          isSubmitting = false,
          submitErrorMessage = e.message ?: "",
        )
      }
    }
  }

  private companion object {
    /** [Routes.callSheetAddReason] URL-encodes [sakhiId]/[itemId] before putting them in the
     * route string (Navigation-Compose does not decode string args itself — see
     * [org.armman.supervisor.ui.villagerisksummary.VillageRiskDetailViewModel]'s identical
     * decode step for its own encoded args), so they must be decoded back here. */
    fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
  }
}
