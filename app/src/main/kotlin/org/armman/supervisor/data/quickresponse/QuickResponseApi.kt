package org.armman.supervisor.data.quickresponse

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** One Quick Response card as returned by approval-service, merged from `approval_requests` and
 * `escalation_events` (SRS FR-SV-4.1). Only the fields this app actually uses are declared —
 * Gson ignores the rest of the wire payload (e.g. escalation-only `visitId`/`referralId`). */
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

/** Request body for `POST /quick-response/{cardId}/decision`. `cardSource` identifies which
 * backing table the card came from; `decision`'s valid values differ per card type
 * (e.g. APPROVE/REJECT for REOPEN, OKAY for EDD_NEARING). */
data class DecideQuickResponseRequestDto(
  val cardSource: String,
  val decision: String,
  val decisionReasonCodeLookupId: String?,
  val decisionNotes: String?,
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

  @POST("quick-response/{cardId}/decision")
  suspend fun decideQuickResponseCard(
    @Path("cardId") cardId: String,
    @Body request: DecideQuickResponseRequestDto,
  ): Response<DecideQuickResponseEnvelopeDto>
}
