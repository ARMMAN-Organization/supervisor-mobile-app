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
