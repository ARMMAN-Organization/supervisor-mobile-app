package org.armman.supervisor.data.masterdata

import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupCategoryEnvelopeDto
import retrofit2.Response
import retrofit2.http.GET

/** Envelope for `/ddl-items`, which returns every generic lookup category as a list — the same
 * shape as `/lookups`, under a different path/purpose. */
data class LookupCategoryListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<LookupCategoryDto>?,
)

/**
 * Retrofit contract for the master-data endpoints that each return a single lookup category (or,
 * for DDL Item, every category). Paths are relative to `API_BASE_URL` (`.../api/v1/`).
 */
interface MasterDataCategoryApi {
  @GET("risk-categories")
  suspend fun getRiskCategories(): Response<LookupCategoryEnvelopeDto>

  @GET("risk-types")
  suspend fun getRiskTypes(): Response<LookupCategoryEnvelopeDto>

  @GET("risk-languages")
  suspend fun getRiskLanguages(): Response<LookupCategoryEnvelopeDto>

  @GET("visit-categories")
  suspend fun getVisitCategories(): Response<LookupCategoryEnvelopeDto>

  @GET("item-categories")
  suspend fun getItemCategories(): Response<LookupCategoryEnvelopeDto>

  @GET("uom-list")
  suspend fun getUomList(): Response<LookupCategoryEnvelopeDto>

  @GET("transaction-types")
  suspend fun getTransactionTypes(): Response<LookupCategoryEnvelopeDto>

  @GET("gathering-statuses")
  suspend fun getGatheringStatuses(): Response<LookupCategoryEnvelopeDto>

  @GET("gathering-types")
  suspend fun getGatheringTypes(): Response<LookupCategoryEnvelopeDto>

  @GET("ddl-items")
  suspend fun getDdlItems(): Response<LookupCategoryListEnvelopeDto>
}
