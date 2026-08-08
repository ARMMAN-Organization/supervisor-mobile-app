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
 * `API_BASE_URL` (`.../api/v1/`). */
interface BeneficiaryListApi {
  @GET("beneficiaries")
  suspend fun getBeneficiaries(@Query("sakhiId") sakhiId: String): Response<BeneficiaryListEnvelopeDto>

  @GET("beneficiaries")
  suspend fun getAtRiskBeneficiaries(
    @Query("sakhiId") sakhiId: String,
    @Query("atRiskOnly") atRiskOnly: Boolean = true,
  ): Response<BeneficiaryListEnvelopeDto>
}
