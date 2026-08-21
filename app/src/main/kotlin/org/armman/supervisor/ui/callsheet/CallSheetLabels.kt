package org.armman.supervisor.ui.callsheet

import androidx.annotation.StringRes
import org.armman.supervisor.R

/** Display-label string resource for a [CallSheetStatKind]. Resolved to EN/MR text at display time. */
@StringRes
fun CallSheetStatKind.labelRes(): Int = when (this) {
  CallSheetStatKind.VISIT_DUE -> R.string.call_sheet_stat_visit_due
  CallSheetStatKind.VISIT_3_DAYS_TO_EXPIRE -> R.string.call_sheet_stat_visit_3_days_expire
  CallSheetStatKind.FOLLOWUP_PENDING -> R.string.call_sheet_stat_followup_pending
  CallSheetStatKind.CLOSURE_FORM_PENDING -> R.string.call_sheet_stat_closure_form_pending
  CallSheetStatKind.MISSED_VISIT -> R.string.call_sheet_stat_missed_visit
  CallSheetStatKind.HIGH_RISK_ANC -> R.string.call_sheet_stat_high_risk_anc
  CallSheetStatKind.HIGH_RISK_PNC -> R.string.call_sheet_stat_high_risk_pnc
}

/** Display-label string resource for a [SuccessOutcome]. Resolved to EN/MR text at display time. */
@StringRes
fun SuccessOutcome.labelRes(): Int = when (this) {
  SuccessOutcome.PICKED_UP_TALKED -> R.string.success_outcome_picked_up_talked
  SuccessOutcome.PICKED_UP_NO_ONE_TALKING -> R.string.success_outcome_picked_up_no_one_talking
  SuccessOutcome.PICKED_UP_CUT_MIDWAY -> R.string.success_outcome_picked_up_cut_midway
  SuccessOutcome.CALL_BACK -> R.string.success_outcome_call_back
  SuccessOutcome.UNKNOWN -> R.string.call_history_unrecognized_value
}

/** Display-label string resource for a [FailureReason]. Resolved to EN/MR text at display time. */
@StringRes
fun FailureReason.labelRes(): Int = when (this) {
  FailureReason.NOT_PICKED_UP -> R.string.failure_reason_not_picked_up
  FailureReason.RINGING -> R.string.failure_reason_ringing
  FailureReason.PHONE_OFF -> R.string.failure_reason_phone_off
  FailureReason.OUT_OF_NETWORK -> R.string.failure_reason_out_of_network
  FailureReason.UNKNOWN -> R.string.call_history_unrecognized_value
}

/** Display-label string resource for a [CallResponder]. Resolved to EN/MR text at display time. */
@StringRes
fun CallResponder.labelRes(): Int = when (this) {
  CallResponder.RELATIVE -> R.string.call_responder_relative
  CallResponder.HUSBAND -> R.string.call_responder_husband
  CallResponder.SAKHI -> R.string.call_responder_sakhi
  CallResponder.PERSON_WHO_DOES_NOT_KNOW_WOMAN -> R.string.call_responder_person_who_does_not_know_woman
  CallResponder.UNKNOWN -> R.string.call_history_unrecognized_value
}

/** Display-label string resource for a [RegistrationType]. Resolved to EN/MR text at display time. */
@StringRes
fun RegistrationType.labelRes(): Int = when (this) {
  RegistrationType.WOMEN -> R.string.registration_type_women
  RegistrationType.CHILD -> R.string.registration_type_child
  RegistrationType.UNKNOWN -> R.string.call_history_unrecognized_value
}

/** Display-label string resource for a [FollowupPendingReason]. Resolved to EN/MR text at display time. */
@StringRes
fun FollowupPendingReason.labelRes(): Int = when (this) {
  FollowupPendingReason.BENEFICIARY_NOT_AT_HOME -> R.string.followup_pending_reason_not_at_home
  FollowupPendingReason.HOSPITALIZE -> R.string.followup_pending_reason_hospitalize
  FollowupPendingReason.CHILD_NOT_REFERRABLE -> R.string.followup_pending_reason_child_not_referrable
  FollowupPendingReason.COUNSELLING -> R.string.followup_pending_reason_counselling
}

/** Display-label string resource for a [ClosurePendingReason]. Resolved to EN/MR text at display time. */
@StringRes
fun ClosurePendingReason.labelRes(): Int = when (this) {
  ClosurePendingReason.INFORMATION_NOT_RECEIVED -> R.string.closure_pending_reason_information_not_received
  ClosurePendingReason.APP_ISSUES -> R.string.closure_pending_reason_app_issues
  ClosurePendingReason.TIME_LEFT_TO_CLOSE -> R.string.closure_pending_reason_time_left
  ClosurePendingReason.BENEFICIARY_NOT_DELIVERED -> R.string.closure_pending_reason_not_delivered
  ClosurePendingReason.BENEFICIARY_HOSPITALIZED -> R.string.closure_pending_reason_hospitalized
  ClosurePendingReason.OTHERS -> R.string.closure_pending_reason_others
}

/** Display-label string resource for a [LastSyncReason]. Resolved to EN/MR text at display time. */
@StringRes
fun LastSyncReason.labelRes(): Int = when (this) {
  LastSyncReason.FORGOT_TO_SYNC -> R.string.last_sync_reason_forgot_to_sync
  LastSyncReason.NO_LIGHT_IN_VILLAGE -> R.string.last_sync_reason_no_light
  LastSyncReason.MOBILE_PROBLEM -> R.string.last_sync_reason_mobile_problem
  LastSyncReason.NO_RECHARGE -> R.string.last_sync_reason_no_recharge
}
