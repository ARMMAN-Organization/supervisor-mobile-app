package org.armman.supervisor.data.quickresponse

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/** Request body for `POST /missed-visit-escalations/{id}/decision` (SRS FR-SV-4.3 CTAs). Not
 * Approve/Reject — Transfer emails the beneficiary's Manager and removes them from the Sakhi's
 * list; Close notifies the Sakhi to fill a closure form. */
data class DecideMissedVisitEscalationRequestDto(val action: String)

data class DecideMissedVisitEscalationEnvelopeDto(
  val success: Boolean,
  val message: String?,
)

/** Retrofit contract for escalation-service's Missed Visit Escalation decision endpoint. Path
 * is relative to `API_BASE_URL` (`.../api/v1/`). */
interface MissedVisitEscalationApi {
  @POST("missed-visit-escalations/{id}/decision")
  suspend fun decide(
    @Path("id") id: String,
    @Body request: DecideMissedVisitEscalationRequestDto,
  ): Response<DecideMissedVisitEscalationEnvelopeDto>
}
