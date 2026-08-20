package org.armman.supervisor.data.events

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.JsonAdapter
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.lang.reflect.Type

/** [SupervisorEventDto.topicsJson] is documented/typed as a JSON-encoded string (e.g. `"{}"`), but
 * some live records return it as a raw JSON object instead (e.g. `{"topics":["..."]}`), which the
 * default String deserializer rejects with "Expected a string but was BEGIN_OBJECT". This
 * tolerates either shape and normalizes both to the raw JSON text, since this app only ever passes
 * [SupervisorEventDto.topicsJson] through unchanged — it has no domain model for it yet. */
private class TopicsJsonDeserializer : JsonDeserializer<String> {
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): String =
    if (json is JsonPrimitive) json.asString else json.toString()
}

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
  @JsonAdapter(TopicsJsonDeserializer::class)
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

/** Request body for `POST supervisor-events`. [eventDate] must be an ISO-8601/RFC3339 timestamp
 * (e.g. `2026-08-09T00:00:00.000Z`), matching the field's `format: date-time` in the response
 * schema — a `dd MMM yyyy` display string here 400s. No `supervisorId` field: the live OpenAPI
 * spec (`GET /api/v1/docs`) declares this request body with `additionalProperties: false` and
 * without `supervisorId` in its properties, so the server derives the supervisor from the auth
 * token, and sending it as an extra field 400s the whole request. */
data class CreateSupervisorEventRequest(
  val projectId: String,
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
