package org.armman.supervisor.data.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Request body sent to the auth-service login endpoint. Field names are the wire contract. */
data class LoginRequestDto(
  val username: String,
  val password: String,
)

/** `data` payload of a successful login response — tokens plus role/scope claims directly,
 * confirmed against the real auth-service response shape (no need to decode the access token
 * for these fields; only `sub` requires decoding). */
data class LoginResponseData(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
  val roles: List<String>,
  val projectId: String?,
  val geographyUnitId: String?,
)

/** Envelope every auth-service response uses, success or failure. */
data class LoginResponseDto(
  val success: Boolean,
  val message: String?,
  val data: LoginResponseData?,
)

/** Error envelope shape for 400/401/etc — `details` is a free-form map, not asserted on. */
data class ErrorResponseDto(
  val success: Boolean,
  val message: String?,
  val errorCode: String?,
)

/** Retrofit contract for the auth-service login endpoint. Path is relative to `API_BASE_URL`
 * (`.../api/v1/`), so the full request URL resolves to `.../api/v1/auth/login`. */
interface AuthApi {
  @POST("auth/login")
  suspend fun login(@Body request: LoginRequestDto): Response<LoginResponseDto>
}
