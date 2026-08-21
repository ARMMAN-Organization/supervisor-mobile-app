package org.armman.supervisor.data.calllog

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** One call log as returned by supervisor-operations-service (ERD §4.7 call_logs). */
data class CallLogDto(
  val id: String,
  val sakhiId: String,
  val callStatus: String,
  val notes: String?,
  val followupAction: String?,
  val callStartAt: String,
  val callEndAt: String?,
  val callDurationSeconds: Int?,
  val responder: String?,
)

/** Request body for `POST /call-logs` (FR-SV-3.1/3.2). `projectId`/`sakhiId`/`callDatetime`/
 * `callStartAt` identify the call; `supervisorId` is never sent — the backend derives it from
 * the authenticated caller. */
data class CreateCallLogRequestDto(
  val projectId: String,
  val sakhiId: String,
  val callDatetime: String,
  val callStatus: String,
  val callStartAt: String,
  val callDurationSeconds: Int?,
  val notes: String?,
  val followupAction: String?,
  val responder: String?,
)

/** Request body for `PATCH /call-logs/:callLogId` (FR-SV-3.2). All fields are optional on the
 * backend — only send what changed (e.g. just [followupAction]/[notes] when recording a Followup
 * Pending reason, matching [CallSheetRepository.submitReason]'s usage). */
data class UpdateCallLogRequestDto(
  val notes: String? = null,
  val followupAction: String? = null,
)

/** Envelope every api-gateway response uses, success or failure. */
data class CallLogEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: CallLogDto?,
)

data class CallLogsEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<CallLogDto>?,
)

/** One Call Sheet stats card row, as returned by `GET /call-sheet-stats` (7 fixed kinds). */
data class CallSheetStatRowDto(
  val kind: String,
  val updated: Int,
  val count: Int,
)

/** A Sakhi's full stats card, as returned by `GET /call-sheet-stats`. */
data class CallSheetStatsDto(
  val sakhiId: String,
  val lastDataSyncDate: String,
  val rows: List<CallSheetStatRowDto>,
)

data class CallSheetStatsListEnvelopeDto(
  val success: Boolean,
  val message: String?,
  val data: List<CallSheetStatsDto>?,
)

/** Retrofit contract for the call-logs endpoints owned by supervisor-operations-service. Paths
 * are relative to `API_BASE_URL` (`.../api/v1/`). */
interface CallLogApi {
  @POST("call-logs")
  suspend fun createCallLog(@Body request: CreateCallLogRequestDto): Response<CallLogEnvelopeDto>

  @GET("call-logs/by-sakhi/{sakhiId}")
  suspend fun getCallLogsBySakhi(@Path("sakhiId") sakhiId: String): Response<CallLogsEnvelopeDto>

  @PATCH("call-logs/{callLogId}")
  suspend fun updateCallLog(
    @Path("callLogId") callLogId: String,
    @Body request: UpdateCallLogRequestDto,
  ): Response<CallLogEnvelopeDto>

  /** Call-sheet stats for multiple Sakhis in one call (list-view card grid). An unauthorized or
   * unknown sakhiId is silently omitted from the response by the backend, not an error. */
  @GET("call-sheet-stats")
  suspend fun getCallSheetStatsBatch(@Query("sakhiIds") sakhiIds: String): Response<CallSheetStatsListEnvelopeDto>
}
