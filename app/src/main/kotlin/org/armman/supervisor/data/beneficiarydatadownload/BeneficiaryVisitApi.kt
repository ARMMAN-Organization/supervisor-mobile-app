package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** One visit instance, as returned by `/beneficiaries/{id}/visits` — the beneficiary's full
 * offline-reference visit history. */
data class BeneficiaryVisitDto(val id: String, val beneficiaryId: String, val actualVisitDate: String?)

data class BeneficiaryVisitsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<BeneficiaryVisitDto>?,
)

/** Retrofit contract for the Beneficiary Visit endpoint owned by visit-form-service, built
 * specifically for the Beneficiary Data Download flow. Path is relative to `API_BASE_URL`
 * (`.../api/v1/`). */
interface BeneficiaryVisitApi {
  @GET("beneficiaries/{beneficiaryId}/visits")
  suspend fun getBeneficiaryVisits(@Path("beneficiaryId") beneficiaryId: String): Response<BeneficiaryVisitsEnvelopeDto>
}
