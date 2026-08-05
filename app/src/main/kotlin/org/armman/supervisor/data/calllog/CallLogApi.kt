package org.armman.supervisor.data.calllog

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

/** Retrofit contract for the call-logs endpoints owned by supervisor-operations-service. Paths
 * are relative to `API_BASE_URL` (`.../api/v1/`). */
interface CallLogApi {
  @POST("call-logs")
  suspend fun createCallLog(@Body request: CreateCallLogRequestDto): Response<CallLogEnvelopeDto>

  @GET("call-logs/by-sakhi/{sakhiId}")
  suspend fun getCallLogsBySakhi(@Path("sakhiId") sakhiId: String): Response<CallLogsEnvelopeDto>
}
