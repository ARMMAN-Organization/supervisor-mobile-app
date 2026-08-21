package org.armman.supervisor.data.quickresponse

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Path

data class AcknowledgeEddNearingEnvelopeDto(
  val success: Boolean,
  val message: String?,
)

/** Retrofit contract for escalation-service's EDD Nearing acknowledge endpoint (SRS FR-SV-4.8 —
 * single "Okay" CTA, no reason, no reject path, no Sakhi notification). Path is relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface EddNearingApi {
  @POST("edd-nearing-requests/{id}/acknowledge")
  suspend fun acknowledge(@Path("id") id: String): Response<AcknowledgeEddNearingEnvelopeDto>
}
