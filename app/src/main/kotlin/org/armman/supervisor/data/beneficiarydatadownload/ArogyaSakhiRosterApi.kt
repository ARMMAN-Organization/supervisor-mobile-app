package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** One Arogya Sakhi roster entry, as returned by `/arogya-sakhi-roster`. */
data class ArogyaSakhiRosterEntryDto(val id: String, val userId: String, val displayName: String)

data class ArogyaSakhiRosterEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<ArogyaSakhiRosterEntryDto>?,
)

/** One registration target row, as returned by `/registration-targets`. */
data class RegistrationTargetDto(val id: String, val sakhiId: String)

data class RegistrationTargetsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<RegistrationTargetDto>?,
)

/** Retrofit contract for the Arogya Sakhi and Registration Target endpoints. Paths are relative
 * to `API_BASE_URL` (`.../api/v1/`). */
interface ArogyaSakhiRosterApi {
  @GET("arogya-sakhi-roster")
  suspend fun getArogyaSakhiRoster(@Query("projectId") projectId: String): Response<ArogyaSakhiRosterEnvelopeDto>

  @GET("registration-targets")
  suspend fun getRegistrationTargets(@Query("sakhiId") sakhiId: String): Response<RegistrationTargetsEnvelopeDto>
}
