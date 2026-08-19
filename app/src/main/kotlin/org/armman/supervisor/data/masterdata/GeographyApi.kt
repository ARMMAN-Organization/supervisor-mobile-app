package org.armman.supervisor.data.masterdata

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** One geography unit, as returned by auth-service's geography master data. */
data class GeographyUnitDto(
  val geographyUnitId: String,
  val parentId: String?,
  val geoType: String,
  val geoCode: String?,
  val name: String,
  val status: String,
)

data class GeographyUnitsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<GeographyUnitDto>?,
)

/** One project-to-geography scope link, as returned by `/project-geography`. */
data class ProjectGeographyLinkDto(
  val id: String,
  val projectId: String,
  val geographyUnitId: String,
  val activeFrom: String,
  val activeTo: String?,
)

data class ProjectGeographyEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<ProjectGeographyLinkDto>?,
)

/** Retrofit contract for the geography tree (State/District/Block/PHC/Sub-Center/Village) and the
 * project↔geography scope mapping, both owned by auth-service. Paths are relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface GeographyApi {
  @GET("geography-units/roots")
  suspend fun getRoots(): Response<GeographyUnitsEnvelopeDto>

  @GET("geography-units")
  suspend fun getUnits(
    @Query("geoType") geoType: String,
    @Query("parentId") parentId: String,
  ): Response<GeographyUnitsEnvelopeDto>

  @GET("project-geography")
  suspend fun getProjectGeography(@Query("projectId") projectId: String): Response<ProjectGeographyEnvelopeDto>
}
