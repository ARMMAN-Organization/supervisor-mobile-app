package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET

/** Risk monitoring rollup, as returned by `/risk-monitoring`. Backend's own OpenAPI doc marks this
 * route as an "UNCONFIRMED alias for GET /beneficiaries/risk-summary" — "Risk Monitoring" has no
 * definition anywhere in the SRS/ERD/HLD, so this is a best-guess mapping, not a product-confirmed
 * shape. It's a single aggregate object (counts), not a downloadable list — [everAtRiskCount] is
 * used as this row's "record count" since there's no natural list length here. */
data class RiskMonitoringDto(
  val total: Int,
  val everAtRiskCount: Int,
  val referralTriggerCount: Int,
)

data class RiskMonitoringEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: RiskMonitoringDto?,
)

/** Retrofit contract for the (unconfirmed) Risk Monitoring endpoint. Path is relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface RiskMonitoringApi {
  @GET("risk-monitoring")
  suspend fun getRiskMonitoring(): Response<RiskMonitoringEnvelopeDto>
}
