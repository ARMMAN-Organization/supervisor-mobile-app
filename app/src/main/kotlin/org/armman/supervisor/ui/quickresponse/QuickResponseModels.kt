package org.armman.supervisor.ui.quickresponse

import org.armman.supervisor.R

/** Type of Quick Response card (SRS FR-SV-4.1). Only [DATA_RESTORE] is built for now. */
enum class QuickResponseRequestType { DATA_RESTORE }

fun QuickResponseRequestType.labelRes(): Int = when (this) {
  QuickResponseRequestType.DATA_RESTORE -> R.string.quick_response_type_data_restore
}

/** Status shown read-only on a Quick Response card (SRS FR-SV-4.6 CTAs / decision outcome). */
enum class QuickResponseRequestStatus { PENDING, APPROVED, REJECTED, RESTORED }

fun QuickResponseRequestStatus.labelRes(): Int = when (this) {
  QuickResponseRequestStatus.PENDING -> R.string.quick_response_status_pending
  QuickResponseRequestStatus.APPROVED -> R.string.quick_response_status_approved
  QuickResponseRequestStatus.REJECTED -> R.string.quick_response_status_rejected
  QuickResponseRequestStatus.RESTORED -> R.string.quick_response_status_restored
}

/** One Quick Response card (Data Restore request) shown on the list screen. */
data class QuickResponseRequest(
  val id: String,
  val requestedAtEpochMillis: Long,
  val requestType: QuickResponseRequestType,
  val projectName: String,
  val sakhiName: String,
  val status: QuickResponseRequestStatus,
)

/** Reason selected on the Add Reason screen for a Quick Response request. */
enum class ReasonOption { RESTORED, PENDING, REJECT, APPROVE }

fun ReasonOption.labelRes(): Int = when (this) {
  ReasonOption.RESTORED -> R.string.quick_response_reason_restored
  ReasonOption.PENDING -> R.string.quick_response_reason_pending
  ReasonOption.REJECT -> R.string.quick_response_reason_reject
  ReasonOption.APPROVE -> R.string.quick_response_reason_approve
}
