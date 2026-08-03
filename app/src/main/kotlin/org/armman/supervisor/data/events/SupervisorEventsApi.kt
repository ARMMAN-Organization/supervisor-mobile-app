package org.armman.supervisor.data.events

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** One Meeting/Training event as returned/accepted by supervisor-operations-service. Flat shape —
 * no gatherings/attendance/marks/photos concept exists server-side (those remain local-only, see
 * [org.armman.supervisor.data.local.SupervisorEventDao]). [topicsJson] is passed through as a raw
 * JSON string; this app has no domain model for it yet. */
data class SupervisorEventDto(
  val id: String,
  val projectId: String,
  val supervisorId: String,
  val eventType: String,
  val eventDate: String,
  val topicsJson: String,
  val remarks: String?,
  val status: String,
  val photoMediaId: String?,
  val createdAt: String,
  val updatedAt: String,
)

data class SupervisorEventsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<SupervisorEventDto>?,
)

data class SupervisorEventEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: SupervisorEventDto?,
)

/** Request body for `POST supervisor-events`. Unlike inventory-transactions, [supervisorId] IS
 * client-supplied here — must be sourced from the current session, never invented. */
data class CreateSupervisorEventRequest(
  val projectId: String,
  val supervisorId: String,
  val eventType: String,
  val eventDate: String,
  val topicsJson: String,
  val remarks: String?,
  val status: String,
  val photoMediaId: String? = null,
)

/** Retrofit contract for Meeting/Training events, owned by supervisor-operations-service. Paths
 * are relative to `API_BASE_URL` (`.../api/v1/`). */
interface SupervisorEventsApi {
  @GET("supervisor-events")
  suspend fun getEvents(): Response<SupervisorEventsEnvelopeDto>

  @POST("supervisor-events")
  suspend fun createEvent(@Body request: CreateSupervisorEventRequest): Response<SupervisorEventEnvelopeDto>
}
