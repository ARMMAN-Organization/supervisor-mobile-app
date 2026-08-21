package org.armman.supervisor.ui.quickresponse

import org.armman.supervisor.R

/**
 * Type of Quick Response card (SRS FR-SV-4.1 — all 8 event types).
 * - [LMP_CHANGE] (FR-SV-4.2), [CLOSURE_REVIEW] (FR-SV-4.4), [REOPEN] (FR-SV-4.7),
 *   [ACCOMPANIED_REFERRAL] (FR-SV-4.9), [REFERRAL_INCOMPLETE] (FR-SV-4.5), [DATA_RESTORE]
 *   (FR-SV-4.6): plain Approve/Reject CTA via `POST /quick-response/{cardId}/decision`.
 * - [MISSED_VISIT_ESCALATION] (FR-SV-4.3): Transfer/Close CTA via
 *   `POST /missed-visit-escalations/{id}/decision` — Transfer currently 501s pending a
 *   beneficiary-roster-removal + Manager-email capability that doesn't exist yet. Backend's
 *   wire value for this type is `"MISSED_VISIT"`, not `"MISSED_VISIT_ESCALATION"`.
 * - [EDD_NEARING] (FR-SV-4.8): single "Okay" acknowledge CTA via
 *   `POST /edd-nearing-requests/{id}/acknowledge` — informational only, no reject path.
 */
enum class QuickResponseRequestType {
  LMP_CHANGE,
  CLOSURE_REVIEW,
  REOPEN,
  ACCOMPANIED_REFERRAL,
  REFERRAL_INCOMPLETE,
  DATA_RESTORE,
  MISSED_VISIT_ESCALATION,
  EDD_NEARING,
}

fun QuickResponseRequestType.labelRes(): Int = when (this) {
  QuickResponseRequestType.LMP_CHANGE -> R.string.quick_response_type_lmp_change
  QuickResponseRequestType.CLOSURE_REVIEW -> R.string.quick_response_type_closure_review
  QuickResponseRequestType.REOPEN -> R.string.quick_response_type_reopen
  QuickResponseRequestType.ACCOMPANIED_REFERRAL -> R.string.quick_response_type_accompanied_referral
  QuickResponseRequestType.REFERRAL_INCOMPLETE -> R.string.quick_response_type_referral_incomplete
  QuickResponseRequestType.DATA_RESTORE -> R.string.quick_response_type_data_restore
  QuickResponseRequestType.MISSED_VISIT_ESCALATION -> R.string.quick_response_type_missed_visit_escalation
  QuickResponseRequestType.EDD_NEARING -> R.string.quick_response_type_edd_nearing
}

/** The CTA row a card shows, dictated by [QuickResponseRequestType] (SRS's per-type CTAs). */
enum class QuickResponseCtaKind { APPROVE_REJECT, TRANSFER_CLOSE, OKAY }

fun QuickResponseRequestType.ctaKind(): QuickResponseCtaKind = when (this) {
  QuickResponseRequestType.MISSED_VISIT_ESCALATION -> QuickResponseCtaKind.TRANSFER_CLOSE
  QuickResponseRequestType.EDD_NEARING -> QuickResponseCtaKind.OKAY
  else -> QuickResponseCtaKind.APPROVE_REJECT
}

/** Decision a Supervisor can make on an Approve/Reject-CTA card (SRS FR-SV-4.2, 4.4, 4.5, 4.6,
 * 4.7, 4.9 CTAs). */
enum class QuickResponseDecision { APPROVE, REJECT }

/** Action a Supervisor can take on a Missed Visit Escalation card (SRS FR-SV-4.3 CTAs). */
enum class QuickResponseEscalationAction { TRANSFER, CLOSE }

/**
 * Per-type fields shown on a card, beyond the common fields in [QuickResponseRequest] (SRS
 * FR-SV-4.2, 4.3, 4.4, 4.5, 4.7, 4.8, 4.9). [DataRestore] carries no fields beyond the base
 * [QuickResponseRequest.sakhiName] — SRS FR-SV-4.6 only asks for Sakhi name/ID, both already
 * covered there.
 */
sealed interface QuickResponseCardDetail {
  data class LmpChange(
    val oldLmpDateEpochMillis: Long?,
    val newLmpDateEpochMillis: Long?,
    val sonographyImageAssetId: String?,
  ) : QuickResponseCardDetail

  /** @param reasonLabel resolved via `GET /lookups`; falls back to the raw code if unresolved. */
  data class ClosureReview(
    val reasonLabel: String?,
    val closureDateEpochMillis: Long?,
    val supervisorNotes: String?,
  ) : QuickResponseCardDetail

  data class Reopen(val reasonForReopen: String?) : QuickResponseCardDetail

  data class AccompaniedReferral(
    val facilityName: String?,
    val facilityType: String?,
    val referralDateEpochMillis: Long?,
    val photoEvidenceAssetId: String?,
  ) : QuickResponseCardDetail

  /** Same `referrals` table as [AccompaniedReferral], but a distinct request type server-side —
   * approval-service type-guards the decision so one can't be decided as the other. */
  data class ReferralIncomplete(
    val facilityName: String?,
    val facilityType: String?,
    val referralDateEpochMillis: Long?,
    val referralsMissedCount: Int?,
    val visitReference: String?,
    val reason: String?,
  ) : QuickResponseCardDetail

  /** SRS FR-SV-4.3's "Type of Visit missed" field. Backend doesn't return a separate visits-
   * missed count — only [visitType] (the raw escalation trigger code, e.g. `"ANC_2_MISSED"`). */
  data class MissedVisitEscalation(val visitType: String?) : QuickResponseCardDetail

  /** SRS FR-SV-4.8 card fields beyond the base ones: EDD date, Reason. */
  data class EddNearing(val eddDateEpochMillis: Long?, val reason: String?) : QuickResponseCardDetail
}

/** One risk condition summary row (SRS "Beneficiary risk details" field). */
data class QuickResponseRiskCondition(val conditionName: String, val latestGrade: String?)

/** One pending Quick Response card shown on the list screen. Base fields shown on every card
 * type (SRS FR-SV-4.1); [detail] carries the fields specific to [requestType]. [beneficiaryName]
 * and [padaName] are null for [QuickResponseRequestType.DATA_RESTORE] — that type has no
 * beneficiary (SRS FR-SV-4.6 only involves the Sakhi, identified by [sakhiId]). */
data class QuickResponseRequest(
  val id: String,
  val requestedAtEpochMillis: Long,
  val requestType: QuickResponseRequestType,
  val beneficiaryName: String?,
  val sakhiName: String?,
  val sakhiId: String?,
  val sakhiPhoneNumber: String?,
  val padaName: String?,
  val requestStatus: String?,
  val riskConditions: List<QuickResponseRiskCondition>,
  val detail: QuickResponseCardDetail?,
)
