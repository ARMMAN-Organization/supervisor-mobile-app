package org.armman.supervisor.ui.quickresponse

import androidx.compose.runtime.Composable
import org.armman.supervisor.R

/** Renders the extra fields specific to [detail]'s card type, if any (SRS FR-SV-4.2, 4.4, 4.5,
 * 4.7). [QuickResponseCardDetail.ReferralIncomplete] deliberately renders the same fields as
 * [QuickResponseCardDetail.AccompaniedReferral] — they share the underlying `referrals` table —
 * but stays a distinct branch since the two are separate request types server-side. */
@Composable
internal fun CardDetailBlocks(detail: QuickResponseCardDetail?) {
  when (detail) {
    is QuickResponseCardDetail.LmpChange -> {
      detail.oldLmpDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_old_lmp, value = it.toDisplayDateOnly()) }
      detail.newLmpDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_new_lmp, value = it.toDisplayDateOnly()) }
      // detail.sonographyImageAssetId: no image-loading capability in the app yet — see
      // QUICK_RESPONSE_BACKEND_TASKS.md. Every record checked so far has this null anyway.
    }
    is QuickResponseCardDetail.ClosureReview -> {
      detail.reasonLabel?.let { FieldBlock(labelRes = R.string.quick_response_field_closure_reason, value = it) }
      detail.closureType?.let { FieldBlock(labelRes = R.string.quick_response_field_closure_type, value = it) }
      detail.closureDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_closure_date, value = it.toDisplayDateOnly()) }
      detail.supervisorNotes?.let { FieldBlock(labelRes = R.string.quick_response_field_supervisor_notes, value = it) }
    }
    is QuickResponseCardDetail.Reopen -> {
      detail.reasonForReopen?.let { FieldBlock(labelRes = R.string.quick_response_field_reopen_reason, value = it) }
    }
    is QuickResponseCardDetail.AccompaniedReferral -> {
      detail.facilityName?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility, value = it) }
      detail.facilityType?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility_type, value = it) }
      detail.referralDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_date, value = it.toDisplayDateOnly()) }
      // detail.photoEvidenceAssetId: no image-loading capability in the app yet — see
      // QUICK_RESPONSE_BACKEND_TASKS.md. Every record checked so far has this null anyway.
    }
    is QuickResponseCardDetail.ReferralIncomplete -> {
      detail.facilityName?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility, value = it) }
      detail.facilityType?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_facility_type, value = it) }
      detail.referralDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_date, value = it.toDisplayDateOnly()) }
      detail.visitReference?.let { FieldBlock(labelRes = R.string.quick_response_field_visit_reference, value = it) }
      detail.referralsMissedCount?.let { FieldBlock(labelRes = R.string.quick_response_field_referrals_missed_count, value = it.toString()) }
      detail.reason?.let { FieldBlock(labelRes = R.string.quick_response_field_referral_incomplete_reason, value = it) }
    }
    is QuickResponseCardDetail.MissedVisitEscalation -> {
      detail.visitType?.let { FieldBlock(labelRes = R.string.quick_response_field_visit_type, value = it) }
    }
    is QuickResponseCardDetail.EddNearing -> {
      detail.eddDateEpochMillis?.let { FieldBlock(labelRes = R.string.quick_response_field_edd_date, value = it.toDisplayDateOnly()) }
      detail.reason?.let { FieldBlock(labelRes = R.string.quick_response_field_edd_reason, value = it) }
    }
    null -> Unit
  }
}
