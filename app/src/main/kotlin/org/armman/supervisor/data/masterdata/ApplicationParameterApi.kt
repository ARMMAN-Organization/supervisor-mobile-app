package org.armman.supervisor.data.masterdata

import retrofit2.Response
import retrofit2.http.GET

/** One application-wide config parameter, as returned by `/application-parameters`
 * (e.g. `MAX_UPLOAD_SIZE_MB`, `MIN_SUPPORTED_APP_VERSION`, `SYNC_INTERVAL_MINUTES`). */
data class ApplicationParameterDto(
  val id: String,
  val paramKey: String,
  val paramValue: String,
  val description: String?,
  val isActive: Boolean,
)

data class ApplicationParametersEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<ApplicationParameterDto>?,
)

/** Retrofit contract for app-wide config parameters. Path is relative to `API_BASE_URL`
 * (`.../api/v1/`). */
interface ApplicationParameterApi {
  @GET("application-parameters")
  suspend fun getApplicationParameters(): Response<ApplicationParametersEnvelopeDto>
}
