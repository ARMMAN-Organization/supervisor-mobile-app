package org.armman.supervisor.data.lookups

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

data class LookupValueDto(
  val id: String,
  val valueCode: String,
  val valueLabel: String? = null,
)

data class LookupCategoryDto(
  val categoryCode: String,
  val categoryName: String? = null,
  val values: List<LookupValueDto>,
)

data class LookupsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<LookupCategoryDto>?,
)

/** Envelope for `/lookups/{categoryCode}`, which returns a single category, unlike `/lookups`'s list. */
data class LookupCategoryEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: LookupCategoryDto?,
)

/** Retrofit contract for the platform-wide lookup-value master data. Path is relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface LookupsApi {
  @GET("lookups")
  suspend fun getLookups(): Response<LookupsEnvelopeDto>

  @GET("lookups/{categoryCode}")
  suspend fun getCategory(@Path("categoryCode") categoryCode: String): Response<LookupCategoryEnvelopeDto>
}
