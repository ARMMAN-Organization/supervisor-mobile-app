package org.armman.supervisor.data.visitsummary

import retrofit2.Response
import retrofit2.http.GET

/** One visit instance as returned by visit-form-service. Only the fields this app actually uses
 * are declared — Gson ignores the rest of the wire payload.
 *
 * NOTE: `GET /visits` has no `sakhiId` query filter and no working pagination (confirmed against
 * the live gateway — `?sakhiId=`/`?limit=`/`?cursor=` are all silently ignored). This always
 * returns the same server-side page for every caller; [VisitSummaryRepositoryImpl] filters by
 * Sakhi client-side. Visits older than that page are invisible to this screen until the backend
 * adds real filtering/pagination. */
data class VisitInstanceDto(
  val beneficiaryId: String,
  val sakhiId: String,
  val statusLookupValueId: String?,
)

data class VisitListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<VisitInstanceDto>?,
)

/** Retrofit contract for visit-form-service's visit list. Path is relative to `API_BASE_URL`
 * (`.../api/v1/`). */
interface VisitApi {
  @GET("visits")
  suspend fun getVisits(): Response<VisitListEnvelopeDto>
}
