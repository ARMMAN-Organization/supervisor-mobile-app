package org.armman.supervisor.data.lookups

import retrofit2.Response
import retrofit2.http.GET

data class LookupValueDto(
  val id: String,
  val valueCode: String,
)

data class LookupCategoryDto(
  val categoryCode: String,
  val values: List<LookupValueDto>,
)

data class LookupsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<LookupCategoryDto>?,
)

/** Retrofit contract for the platform-wide lookup-value master data. Path is relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface LookupsApi {
  @GET("lookups")
  suspend fun getLookups(): Response<LookupsEnvelopeDto>
}
