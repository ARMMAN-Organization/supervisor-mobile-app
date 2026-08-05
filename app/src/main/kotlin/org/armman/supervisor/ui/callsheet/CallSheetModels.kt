package org.armman.supervisor.ui.callsheet

import org.armman.supervisor.ui.assignitem.SakhiOption

/** A Sakhi is flagged "recently called" on the Call Sheet within this window (SRS FR-SV-3.4). */
const val RECENTLY_CALLED_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

/** Whether a call attempt connected (SRS FR-SV-3.2 "call status"). */
enum class CallConnected { YES, NO }

/** Outcome recorded when a call connected. [UNKNOWN] is never user-selectable — it's a display-only
 * fallback for a backend `callStatus` value this app version doesn't recognize (the backend's
 * call-status lookup can grow independently of an app release, see SRS `call_logs.call_status`). */
enum class SuccessOutcome { PICKED_UP_TALKED, PICKED_UP_NO_ONE_TALKING, PICKED_UP_CUT_MIDWAY, CALL_BACK, UNKNOWN }

/** Reason recorded when a call did not connect. [UNKNOWN] is never user-selectable — see [SuccessOutcome.UNKNOWN]. */
enum class FailureReason { NOT_PICKED_UP, RINGING, PHONE_OFF, OUT_OF_NETWORK, UNKNOWN }

/** Who answered the call — only captured when [SuccessOutcome.PICKED_UP_TALKED]. [UNKNOWN] is never
 * user-selectable — see [SuccessOutcome.UNKNOWN]. */
enum class CallResponder { RELATIVE, HUSBAND, SAKHI, PERSON_WHO_DOES_NOT_KNOW_WOMAN, UNKNOWN }

/** Which Info row of the Call Sheet stats table a [CallSheetStatValue] belongs to. */
enum class CallSheetStatKind {
  VISIT_DUE, VISIT_3_DAYS_TO_EXPIRE, FOLLOWUP_PENDING, CLOSURE_FORM_PENDING, MISSED_VISIT, HIGH_RISK_ANC, HIGH_RISK_PNC
}

/** One Info/Updated/Count row on a Call Sheet Sakhi card's stats table. */
data class CallSheetStatValue(val kind: CallSheetStatKind, val updated: Int, val count: Int)

/** The Info/Updated/Count stats shown on a Call Sheet Sakhi card. */
data class CallSheetStats(val rows: List<CallSheetStatValue>, val lastDataSyncDate: String)

/** One Sakhi row on the Call Sheet list screen, with her stats and last-called timestamp. */
data class SakhiCallSummary(
  val sakhi: SakhiOption,
  val stats: CallSheetStats,
  val lastCalledAtEpochMillis: Long?,
)

/** One logged call attempt, shown in a Sakhi's call history timeline (SRS FR-SV-3.2/3.3). */
data class CallLogEntry(
  val id: String,
  val timestampEpochMillis: Long,
  val connected: CallConnected,
  val successOutcome: SuccessOutcome?,
  val failureReason: FailureReason?,
  val responder: CallResponder?,
  val durationMinutes: Int?,
  val notes: String?,
  val followUpAction: String?,
)

/** Fields submitted from the Call Outcome form (SRS FR-SV-3.2). */
data class CallLogSubmission(
  val sakhiId: String,
  val connected: CallConnected,
  val successOutcome: SuccessOutcome?,
  val failureReason: FailureReason?,
  val responder: CallResponder?,
  val durationMinutes: Int?,
  val notes: String?,
  val followUpAction: String?,
)
