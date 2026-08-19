package org.armman.supervisor.data.masterdata

import retrofit2.Response
import retrofit2.http.GET

/** One risk condition, as returned by risk-referral-service's master data (called with no query
 * params, this returns every ACTIVE condition — not just a code-lookup batch). */
data class RiskConditionDto(
  val id: String,
  val conditionCode: String,
  val conditionName: String,
  val entityType: String,
  val phase: String,
  val gradeScale: String,
  val referralRequiredDefault: Boolean,
  val educationRequiredDefault: Boolean,
  val status: String,
)

data class RiskConditionsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<RiskConditionDto>?,
)

/** One funder, as returned by auth-service's funder master data. */
data class FunderDto(
  val funderId: String,
  val funderCode: String,
  val funderName: String,
  val status: String,
)

data class FundersEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<FunderDto>?,
)

/** One risk parameter, as returned by `/risk-parameters` (called with no query params, returns
 * every ACTIVE parameter — confirmed distinct from [RiskConditionDto], not aliased). */
data class RiskParameterDto(
  val id: String,
  val parameterCode: String,
  val parameterName: String,
  val entityType: String,
  val unit: String?,
  val dataType: String,
  val status: String,
)

data class RiskParametersEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<RiskParameterDto>?,
)

/** One visit master row, as returned by `/visit-masters` (called with no query params, returns
 * every ACTIVE visit type — confirmed distinct from the Visit Category lookup, not aliased). */
data class VisitMasterDto(
  val id: String,
  val visitCode: String,
  val visitType: String,
  val displayName: String,
  val entityType: String,
  val sequenceOrder: Int?,
  val description: String?,
  val isActive: Boolean,
)

data class VisitMastersEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<VisitMasterDto>?,
)

// No IncentiveRateDto/API here: confirmed with backend that GET /incentive-rates/active resolves
// ONE rate for a required `rateType` param (VISIT/REFERRAL/MEETING/TRAINING/RETAINER) — it's not a
// bulk list endpoint, and no such endpoint exists today. See MasterDataEntity.INCENTIVE_RATE's
// doc comment; add this API back once backend ships a real download/list route.

/** Retrofit contract for Risk Conditions, Risk Parameters, Funders, and Visit Masters — unrelated
 * services' master data, grouped in one file because each is a single simple GET with no other
 * endpoints of its own yet. Paths are relative to `API_BASE_URL` (`.../api/v1/`). */
interface RiskAndFundersApi {
  @GET("risk-conditions")
  suspend fun getRiskConditions(): Response<RiskConditionsEnvelopeDto>

  @GET("risk-parameters")
  suspend fun getRiskParameters(): Response<RiskParametersEnvelopeDto>

  @GET("funders")
  suspend fun getFunders(): Response<FundersEnvelopeDto>

  @GET("visit-masters")
  suspend fun getVisitMasters(): Response<VisitMastersEnvelopeDto>
}
