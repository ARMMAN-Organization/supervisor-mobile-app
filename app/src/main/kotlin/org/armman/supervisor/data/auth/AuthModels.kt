package org.armman.supervisor.data.auth

/** Credentials submitted from the login screen. Field names mirror the auth-service DTO. */
data class LoginRequest(
  val username: String,
  val password: String,
)

/**
 * Authenticated Supervisor session. Most fields come directly from the login response body
 * (`roles`, `projectId`, `geographyUnitId`, `expiresIn`); only `subjectId` requires decoding the
 * access token, since the response doesn't otherwise include the user's own id.
 */
data class UserSession(
  val username: String,
  val subjectId: String,
  val roles: List<String>,
  val projectId: String?,
  val geographyUnitId: String?,
  val accessToken: String,
  val refreshToken: String,
  val accessTokenExpiresAtEpochSeconds: Long,
) {
  /** Display name shown in the UI. No display-name field exists in the login response yet;
   * falls back to username. */
  val displayName: String get() = username

  /** Convenience for the RBAC check already enforced at login time. */
  val role: String get() = roles.firstOrNull().orEmpty()
}

/** Domain result of a login attempt — success with a session, or a typed failure. */
sealed interface LoginResult {
  data class Success(val session: UserSession) : LoginResult
  data class Failure(val reason: LoginFailureReason) : LoginResult
}

/** Typed failure reasons so the UI can show localized, user-friendly messages. */
enum class LoginFailureReason {
  /** Server rejected the credentials (401) — or, offline, the cached hash didn't match. */
  INVALID_CREDENTIALS,

  /** Server rejected the request shape (400) — e.g. blank/malformed fields it didn't catch. */
  VALIDATION_ERROR,

  /** No connectivity and no prior successful login on this device to verify against offline. */
  OFFLINE_NO_CACHE,

  /** Login succeeded against the API, but the account's roles don't include SUPERVISOR. */
  WRONG_ROLE,

  /** Request never reached the server, or timed out, while connectivity appeared available. */
  NETWORK_ERROR,

  /** The cached offline session's access token has expired; the user must log in online to refresh it. */
  OFFLINE_SESSION_EXPIRED,

  /** Anything else: 5xx, malformed response body, unexpected exception. */
  UNKNOWN,
}
