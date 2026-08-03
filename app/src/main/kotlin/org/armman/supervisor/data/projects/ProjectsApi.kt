package org.armman.supervisor.data.projects

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/** One project as returned by the auth-service project master data. Only the fields this app
 * actually uses are declared — Gson ignores the rest of the wire payload. */
data class ProjectDto(
  val projectId: String,
  val projectName: String,
)

/** One Sakhi under a project, as returned by the auth-service Sakhi roster endpoint. */
data class SakhiDto(
  val sakhiId: String,
  val displayName: String,
  val mobileNumber: String,
  val primaryProjectId: String,
  val supervisorId: String?,
)

/** Envelope every api-gateway response uses, success or failure. */
data class ProjectsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<ProjectDto>?,
)

data class SakhisEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<SakhiDto>?,
)

/** Retrofit contract for project/Sakhi master data owned by auth-service. Paths are relative to
 * `API_BASE_URL` (`.../api/v1/`). */
interface ProjectsApi {
  @GET("projects")
  suspend fun getProjects(): Response<ProjectsEnvelopeDto>

  @GET("projects/{projectId}/sakhis")
  suspend fun getSakhis(@Path("projectId") projectId: String): Response<SakhisEnvelopeDto>
}
