package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** One risk state snapshot, as returned nested under `/beneficiaries/{id}/risk`'s `currentState`. */
data class RiskStateSnapshotDto(val id: String, val phase: String, val asOfDate: String)

/** One risk assessment, as returned nested under `/beneficiaries/{id}/risk`'s `assessments`. Only
 * the fields this app actually uses are declared — Gson ignores the rest of the wire payload. */
data class RiskAssessmentSummaryDto(val id: String, val evaluatedAt: String)

/** A beneficiary's risk profile: current state snapshots per phase, plus full assessment
 * history — not a flat list, so its own "record count" is `currentState.size + assessments.size`. */
data class BeneficiaryRiskProfileDto(
  val beneficiaryId: String,
  val currentState: List<RiskStateSnapshotDto>,
  val assessments: List<RiskAssessmentSummaryDto>,
)

data class BeneficiaryRiskEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: BeneficiaryRiskProfileDto?,
)

/** One risk referral, as returned by `/beneficiaries/{id}/risk-referrals` (header rows only). */
data class RiskReferralDto(
  val id: String,
  val beneficiaryId: String,
  val referralDate: String,
  val status: String,
)

data class RiskReferralsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<RiskReferralDto>?,
)

/** One followup or trigger-source row under a referral's detail. */
data class ReferralFollowupDto(val id: String)
data class ReferralTriggerSourceDto(val id: String)

/** A single referral's followups + trigger sources — not a flat list, so its own "record count" is
 * `followups.size + triggerSources.size`. */
data class RiskReferralDetailsDto(
  val referralId: String,
  val followups: List<ReferralFollowupDto>,
  val triggerSources: List<ReferralTriggerSourceDto>,
)

data class RiskReferralDetailsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: RiskReferralDetailsDto?,
)

/** Retrofit contract for the Beneficiary Risk / Risk Referral endpoints owned by
 * risk-referral-service, built specifically for the Beneficiary Data Download flow. Paths are
 * relative to `API_BASE_URL` (`.../api/v1/`). */
interface BeneficiaryRiskApi {
  @GET("beneficiaries/{beneficiaryId}/risk")
  suspend fun getBeneficiaryRisk(@Path("beneficiaryId") beneficiaryId: String): Response<BeneficiaryRiskEnvelopeDto>

  @GET("beneficiaries/{beneficiaryId}/risk-referrals")
  suspend fun getRiskReferrals(@Path("beneficiaryId") beneficiaryId: String): Response<RiskReferralsEnvelopeDto>

  @GET("beneficiaries/{beneficiaryId}/risk-referrals/{referralId}/details")
  suspend fun getRiskReferralDetails(
    @Path("beneficiaryId") beneficiaryId: String,
    @Path("referralId") referralId: String,
  ): Response<RiskReferralDetailsEnvelopeDto>
}
