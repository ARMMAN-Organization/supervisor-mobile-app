package org.armman.supervisor.data.quickresponse

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** One Quick Response card as returned by the thin `GET /quick-response` list (SRS FR-SV-4.1).
 * Only the fields this app actually uses are declared — Gson ignores the rest of the wire
 * payload. Full per-type fields come from [QuickResponseCardDetailDto] via
 * `GET /quick-response/{cardId}`, not this thin shape. */
data class QuickResponseCardDto(
  val cardId: String,
  val cardType: String,
  val cardSource: String,
  val beneficiaryId: String?,
  val raisedAt: String,
)

/** Body of `GET /quick-response`'s `data` field. */
data class QuickResponseListDto(
  val cards: List<QuickResponseCardDto>,
  val nextCursor: String?,
)

/** Envelope every api-gateway response uses, success or failure. */
data class QuickResponseListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: QuickResponseListDto?,
)

/** One risk condition as returned within `riskDetails` (SRS "Beneficiary risk details" field). */
data class RiskDetailDto(
  val conditionName: String,
  val latestGrade: String?,
)

/**
 * Full per-card payload as returned by `GET /quick-response/{cardId}` — one call replaces the
 * beneficiary/Sakhi/Pada/risk-state/type-detail join chain this app used before backend added
 * this endpoint. Every field beyond the thin [QuickResponseCardDto] shape is nullable: which
 * ones are populated depends on [cardType]. Field names below are exactly what backend returns
 * per type (verified live against each of the 8 types on 2026-08-20) — e.g. Reopen's reason is
 * `reasonForReopen`, not `reason`; Missed Visit Escalation has no visits-missed *count*, only
 * [visitType] (the raw trigger code, e.g. `"ANC_2_MISSED"`).
 */
data class QuickResponseCardDetailDto(
  val cardId: String,
  val cardType: String,
  val cardSource: String,
  val beneficiaryId: String?,
  val raisedAt: String,
  // Base fields shared across most types (SRS FR-SV-4.1)
  val padaName: String?,
  val sakhiName: String?,
  val sakhiId: String?,
  val sakhiContactNumber: String?,
  val beneficiaryName: String?,
  val riskDetails: List<RiskDetailDto>?,
  val status: String?,
  // LMP Change (FR-SV-4.2)
  val oldLmpDate: String?,
  val newLmpDate: String?,
  val sonographyImageAssetId: String?,
  // Missed Visit Escalation (FR-SV-4.3) — cardType "MISSED_VISIT"
  val visitType: String?,
  // Closure Review (FR-SV-4.4)
  val closureType: String?,
  val closureReasonLookupValueId: String?,
  val closureDate: String?,
  val supervisorNotes: String?,
  // Referral Follow-up Incomplete (FR-SV-4.5) and Accompanied Referral (FR-SV-4.9) share these
  val referralDate: String?,
  val facilityName: String?,
  val facilityType: String?,
  val photoEvidenceAssetId: String?,
  val visitReference: String?,
  val referralsMissedCount: Int?,
  val reason: String?,
  // Beneficiary Reopen Request (FR-SV-4.7)
  val reasonForReopen: String?,
  // EDD Nearing Request (FR-SV-4.8) — reuses [reason] above
  val eddDate: String?,
)

data class QuickResponseCardDetailEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: QuickResponseCardDetailDto?,
)

/** Request body for `POST /quick-response/{cardId}/decision`. `cardSource` identifies which
 * backing table the card came from. `decisionReasonCodeLookupId` has no populated lookup
 * category on approval-service yet and is rejected if sent as JSON `null` (must be omitted,
 * not nulled) — Gson's default encoder (no `serializeNulls()`) already does this. */
data class DecideQuickResponseRequestDto(
  val cardSource: String,
  val decision: String,
  val decisionReasonCodeLookupId: String? = null,
  val decisionNotes: String? = null,
)

data class DecideQuickResponseDto(
  val cardId: String,
  val cardSource: String,
  val decision: String,
)

data class DecideQuickResponseEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: DecideQuickResponseDto?,
)

/** Retrofit contract for the Quick Response endpoints owned by approval-service. Paths are
 * relative to `API_BASE_URL` (`.../api/v1/`). */
interface QuickResponseApi {
  @GET("quick-response")
  suspend fun getQuickResponseCards(
    @Query("status") status: String,
    @Query("cursor") cursor: String?,
    @Query("limit") limit: Int?,
  ): Response<QuickResponseListEnvelopeDto>

  @GET("quick-response/{cardId}")
  suspend fun getQuickResponseCardDetail(@Path("cardId") cardId: String): Response<QuickResponseCardDetailEnvelopeDto>

  @POST("quick-response/{cardId}/decision")
  suspend fun decideQuickResponseCard(
    @Path("cardId") cardId: String,
    @Body request: DecideQuickResponseRequestDto,
  ): Response<DecideQuickResponseEnvelopeDto>
}
