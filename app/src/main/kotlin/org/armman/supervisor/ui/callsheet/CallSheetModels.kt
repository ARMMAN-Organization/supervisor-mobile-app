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

/** Beneficiary registration type shown on Call Sheet drill-down rows. [UNKNOWN] is a display-only
 * fallback for a backend value this app version doesn't recognize — see [FailureReason.UNKNOWN]. */
enum class RegistrationType { WOMEN, CHILD, UNKNOWN }

/** Risk name shown on a Call Sheet drill-down row (free-form, backend-driven — not a fixed enum;
 * kept as a plain [String], matching how [CallSheetRepository] already treats open-ended lookups
 * like `call_logs.call_status`, see [CallSheetRepositoryImpl.enumOfOrUnknown] usage). */
typealias RiskName = String

/** One row on the "Due Visit" / "Visit 3 Days to expire" / "Missed Visit" drill-down screens —
 * these three stats share the same row shape (SRS has no distinct spec for them; see Call Sheet
 * plan's noted assumption). */
data class DueVisitItem(
  val beneficiaryId: String,
  val beneficiaryName: String,
  val villageName: String,
  val uniqueId: String,
  val registrationType: RegistrationType,
  val visit: String,
  val scheduledDate: String,
  val balancedDays: Int,
  val risk: RiskName,
)

/** One row on the "Followup Form Pending" drill-down screen — backed by a real `call_logs` row
 * (there is no beneficiary/village/risk data on a call log, so this only shows what the backend
 * actually has: the call's date and any notes already recorded). [callLogId] is the target of
 * "Add Reason" (`PATCH /call-logs/:callLogId`, see [CallSheetRepository.submitReason]). */
data class FollowupPendingItem(
  val callLogId: String,
  val callDate: String,
  val notes: String?,
)

/** One row on the "Closure Form Pending" drill-down screen. */
data class ClosurePendingItem(
  val beneficiaryId: String,
  val beneficiaryName: String,
  val villageName: String,
  val uniqueId: String,
  val registrationType: RegistrationType,
  val registrationDate: String,
  val dateOfBirth: String,
  val risk: RiskName,
  val overdueDays: Int,
)

/** Which High Risk drill-down is being viewed — ANC and PNC share a screen, discriminated by this. */
enum class HighRiskType { ANC, PNC }

/** One row on the "High Risk ANC"/"High Risk PNC" drill-down screen. */
data class HighRiskItem(
  val beneficiaryId: String,
  val beneficiaryName: String,
  val villageName: String,
  val uniqueId: String,
  val riskName: RiskName,
)

/** The recorded reason for a Sakhi's last data sync date, shown on "Last Sync Date Reason". */
data class SyncReasonItem(val syncDate: String, val reason: String)

/** Which reason-submission flow [AddReasonScreen] is rendering — each has its own fixed reason
 * lookup (see [FollowupPendingReason]/[ClosurePendingReason]/[LastSyncReason]) and is keyed
 * differently: Followup/Closure by [itemId] (a beneficiary record), Last Sync by the Sakhi itself. */
enum class ReasonContext { FOLLOWUP_PENDING, CLOSURE_PENDING, LAST_SYNC }

/** Fixed reason lookup for the Followup Form Pending "Add Reason" flow (screenshot-derived; no
 * backend lookup endpoint exists yet — see [CallSheetRepository]). */
enum class FollowupPendingReason { BENEFICIARY_NOT_AT_HOME, HOSPITALIZE, CHILD_NOT_REFERRABLE, COUNSELLING }

/** Fixed reason lookup for the Closure Form Pending "Add Reason" flow (screenshot-derived). */
enum class ClosurePendingReason {
  INFORMATION_NOT_RECEIVED, APP_ISSUES, TIME_LEFT_TO_CLOSE, BENEFICIARY_NOT_DELIVERED, BENEFICIARY_HOSPITALIZED, OTHERS
}

/** Fixed reason lookup for the Last Sync Date "Add Reason" flow (screenshot-derived). */
enum class LastSyncReason { FORGOT_TO_SYNC, NO_LIGHT_IN_VILLAGE, MOBILE_PROBLEM, NO_RECHARGE }

/** Submission payload for any of the three [ReasonContext] flows. Exactly one of [itemId]/
 * [sakhiId] is meaningful per context — Followup/Closure key by [itemId], Last Sync by [sakhiId]. */
data class ReasonSubmission(
  val context: ReasonContext,
  val itemId: String?,
  val sakhiId: String?,
  val reasonCode: String,
  val remark: String?,
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
