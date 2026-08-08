package org.armman.supervisor.data.dashboard

import retrofit2.Response
import retrofit2.http.GET

/** `GET /beneficiaries/registration-summary` payload. */
data class RegistrationSummaryDto(
  val total: Int,
  val motherCount: Int,
  val childCount: Int,
)

/** `GET /beneficiaries/risk-summary` payload. */
data class RiskSummaryTotalsDto(
  val total: Int,
  val byGrade: Map<String, Int>,
  val everAtRiskCount: Int,
  val referralTriggerCount: Int,
)

/** `GET /visits/visit-summary` payload. */
data class VisitSummaryTotalsDto(
  val total: Int,
  val byStatus: Map<String, Int>,
)

data class RegistrationSummaryEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: RegistrationSummaryDto?,
)

data class RiskSummaryTotalsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: RiskSummaryTotalsDto?,
)

data class VisitSummaryTotalsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: VisitSummaryTotalsDto?,
)

/** Retrofit contract for the Supervisor dashboard's aggregate stat endpoints. Paths are relative
 * to `API_BASE_URL` (`.../api/v1/`). */
interface DashboardApi {
  @GET("beneficiaries/registration-summary")
  suspend fun getRegistrationSummary(): Response<RegistrationSummaryEnvelopeDto>

  @GET("beneficiaries/risk-summary")
  suspend fun getRiskSummary(): Response<RiskSummaryTotalsEnvelopeDto>

  @GET("visits/visit-summary")
  suspend fun getVisitSummary(): Response<VisitSummaryTotalsEnvelopeDto>
}
