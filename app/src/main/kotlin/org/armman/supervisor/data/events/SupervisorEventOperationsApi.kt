package org.armman.supervisor.data.events

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** One Sakhi's attendance row, as accepted/returned by `/supervisor-events/{id}/attendance`. The
 * server has no separate "meeting" vs "per-gathering" attendance concept — every save is flat per
 * eventId; this app's own gathering scoping ([org.armman.supervisor.data.local.EventAttendanceEntity.gatheringId])
 * is local-only and not sent. */
data class AttendanceEntryDto(
  val sakhiId: String,
  val attendanceStatus: String,
  val preTrainingScore: Int? = null,
  val postTrainingScore: Int? = null,
  val remarks: String? = null,
)

data class SaveAttendanceRequest(val attendance: List<AttendanceEntryDto>)

data class AttendanceEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<AttendanceEntryDto>?,
)

data class RescheduleEventRequest(val eventDate: String, val remarks: String)

data class AttachPhotoRequest(val mediaId: String)

data class AttachPhotoDto(val id: String, val eventId: String, val mediaId: String)

data class AttachPhotoEnvelopeDto(val success: Boolean, val message: String?, val data: AttachPhotoDto?)

data class AddGatheringRequest(val gatheringDate: String, val topicIds: List<String>, val remarks: String)

data class GatheringDto(val id: String, val gatheringDate: String, val topicIds: List<String>, val remarks: String?)

data class GatheringEnvelopeDto(val success: Boolean, val message: String?, val data: GatheringDto?)

data class SaveMarkRequest(val gatheringId: String, val sakhiId: String, val markType: String, val score: Int)

data class CompleteMarkRequest(val gatheringId: String, val sakhiId: String, val markType: String)

data class MarkDto(val sakhiId: String, val markType: String, val score: Int)

data class MarksEnvelopeDto(val success: Boolean, val message: String?, val data: MarkDto?)

/** Retrofit contract for the `supervisor-events` sub-resource writes confirmed live on
 * `API_BASE_URL` (`.../api/v1/`): attendance, cancel/complete/reschedule, adding a gathering, and
 * per-topic marks. Kept separate from [SupervisorEventsApi] (event create/list) to keep both files
 * focused. Reuses [SupervisorEventDto]/[SupervisorEventEnvelopeDto] for cancel/complete/reschedule,
 * which all return the updated event. */
interface SupervisorEventOperationsApi {
  @GET("supervisor-events/{id}/attendance")
  suspend fun getAttendance(@Path("id") eventId: String): Response<AttendanceEnvelopeDto>

  @PUT("supervisor-events/{id}/attendance")
  suspend fun saveAttendance(@Path("id") eventId: String, @Body request: SaveAttendanceRequest): Response<AttendanceEnvelopeDto>

  @PATCH("supervisor-events/{id}/cancel")
  suspend fun cancelEvent(@Path("id") eventId: String): Response<SupervisorEventEnvelopeDto>

  @PATCH("supervisor-events/{id}/complete")
  suspend fun completeEvent(@Path("id") eventId: String): Response<SupervisorEventEnvelopeDto>

  @POST("supervisor-events/{id}/photos")
  suspend fun attachPhoto(@Path("id") eventId: String, @Body request: AttachPhotoRequest): Response<AttachPhotoEnvelopeDto>

  @POST("supervisor-events/{id}/reschedule")
  suspend fun rescheduleEvent(@Path("id") eventId: String, @Body request: RescheduleEventRequest): Response<SupervisorEventEnvelopeDto>

  @POST("supervisor-events/{id}/gatherings")
  suspend fun addGathering(@Path("id") eventId: String, @Body request: AddGatheringRequest): Response<GatheringEnvelopeDto>

  @GET("topics/{topicId}/marks")
  suspend fun getMarks(
    @Path("topicId") topicId: String,
    @Query("gatheringId") gatheringId: String,
    @Query("sakhiId") sakhiId: String,
    @Query("type") markType: String,
  ): Response<MarksEnvelopeDto>

  @PUT("topics/{topicId}/marks")
  suspend fun saveMark(@Path("topicId") topicId: String, @Body request: SaveMarkRequest): Response<MarksEnvelopeDto>

  @POST("topics/{topicId}/marks/complete")
  suspend fun completeMark(@Path("topicId") topicId: String, @Body request: CompleteMarkRequest): Response<MarksEnvelopeDto>
}
