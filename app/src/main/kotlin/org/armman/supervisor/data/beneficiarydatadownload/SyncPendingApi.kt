package org.armman.supervisor.data.beneficiarydatadownload

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/** One outstanding (not-yet-synced) sync item, as returned by `/sync/pending`. */
data class SyncPendingItemDto(val id: String, val entityType: String, val status: String)

data class SyncPendingEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<SyncPendingItemDto>?,
)

/** Retrofit contract for the Sakhi Not Uploaded Data endpoint owned by sync-service, built
 * specifically for the Beneficiary Data Download flow. Path is relative to `API_BASE_URL`
 * (`.../api/v1/`). */
interface SyncPendingApi {
  @GET("sync/pending")
  suspend fun getPendingSyncItems(@Query("userId") userId: String): Response<SyncPendingEnvelopeDto>
}
