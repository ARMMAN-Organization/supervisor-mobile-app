package org.armman.supervisor.data.beneficiaries

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class MotherCaseDetailsDto(
  val lmpDate: String?,
  val eddDate: String?,
  val heightCm: String?,
  val bmiAtRegistration: String?,
)

data class ChildCaseDetailsDto(
  val dateOfBirth: String?,
)

data class BeneficiaryPiiDto(
  val fullName: String,
  val mobileNumber: String?,
)

/** One beneficiary case as returned by beneficiary-service's list endpoint. Only the fields this
 * app actually uses are declared — Gson ignores the rest of the wire payload. */
data class BeneficiaryCaseDto(
  val id: String,
  val caseType: String,
  val registrationDate: String,
  val pii: BeneficiaryPiiDto,
  val motherCaseDetails: MotherCaseDetailsDto?,
  val childCaseDetails: ChildCaseDetailsDto?,
  val sakhiName: String?,
  val projectName: String?,
  val villageName: String?,
)

data class BeneficiaryListPageDto(
  val items: List<BeneficiaryCaseDto>,
  val nextCursor: String?,
)

data class BeneficiaryListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: BeneficiaryListPageDto?,
)

/** One entry from `GET /beneficiaries/by-ids-with-risk` — [riskLevel] is the worst current grade
 * across the beneficiary's risk conditions, collapsed to a 4-bucket vocabulary. */
data class BeneficiaryWithRiskDto(
  val id: String,
  val beneficiaryName: String,
  val riskLevel: String,
)

data class BeneficiaryWithRiskEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<BeneficiaryWithRiskDto>?,
)

/** One risk condition on a beneficiary, as returned by `GET /beneficiaries/risk-condition-summary`. */
data class RiskConditionSummaryDto(
  val riskConditionId: String,
  val latestGrade: String?,
  val conditionName: String?,
)

/** One beneficiary's risk condition summaries, as returned by `GET /beneficiaries/risk-condition-summary`. */
data class BeneficiaryRiskConditionSummaryDto(
  val beneficiaryId: String,
  val riskConditionSummaries: List<RiskConditionSummaryDto>,
)

data class BeneficiaryRiskConditionSummaryEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<BeneficiaryRiskConditionSummaryDto>?,
)

/** Retrofit contract for beneficiary-service's list endpoint. Path is relative to
 * `API_BASE_URL` (`.../api/v1/`). `limit` defaults to 50 server-side, so callers must follow
 * `nextCursor` (see [fetchAllBeneficiaryPages]) to avoid silently truncating results. */
interface BeneficiaryListApi {
  @GET("beneficiaries")
  suspend fun getBeneficiaries(
    @Query("sakhiId") sakhiId: String,
    @Query("cursor") cursor: String? = null,
  ): Response<BeneficiaryListEnvelopeDto>

  @GET("beneficiaries")
  suspend fun getAtRiskBeneficiaries(
    @Query("sakhiId") sakhiId: String,
    @Query("atRiskOnly") atRiskOnly: Boolean = true,
    @Query("cursor") cursor: String? = null,
  ): Response<BeneficiaryListEnvelopeDto>

  /** Batched risk grade for a set of beneficiary ids, scoped server-side to the caller's roster.
   * [ids] is comma-separated; an id outside scope or not found is silently absent from the result. */
  @GET("beneficiaries/by-ids-with-risk")
  suspend fun getBeneficiariesWithRisk(
    @Query("ids") ids: String,
  ): Response<BeneficiaryWithRiskEnvelopeDto>

  /** Batched risk-condition detail (e.g. condition name) for a set of beneficiary ids, mirroring
   * [getBeneficiariesWithRisk]'s scoping/silent-drop semantics. [beneficiaryIds] is comma-separated. */
  @GET("beneficiaries/risk-condition-summary")
  suspend fun getRiskConditionSummaries(
    @Query("beneficiaryIds") beneficiaryIds: String,
  ): Response<BeneficiaryRiskConditionSummaryEnvelopeDto>
}

/** Follows [BeneficiaryListPageDto.nextCursor] until exhausted, concatenating every page's
 * items. Shared by all call sites that page through beneficiary-service's list endpoint
 * (`getBeneficiaries`/`getAtRiskBeneficiaries`), regardless of which query params they fix. */
suspend fun fetchAllBeneficiaryPages(
  fetchPage: suspend (cursor: String?) -> Response<BeneficiaryListEnvelopeDto>,
): List<BeneficiaryCaseDto> {
  val allItems = mutableListOf<BeneficiaryCaseDto>()
  var cursor: String? = null
  do {
    val response = fetchPage(cursor)
    if (!response.isSuccessful) error("Failed to load beneficiaries: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty beneficiaries response")
    if (!body.success) error(body.message ?: "Failed to load beneficiaries")
    val page = body.data
    allItems += page?.items.orEmpty()
    cursor = page?.nextCursor
  } while (cursor != null)
  return allItems
}
