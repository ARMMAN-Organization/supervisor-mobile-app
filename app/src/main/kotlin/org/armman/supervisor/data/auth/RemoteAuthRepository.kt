package org.armman.supervisor.data.auth

import android.util.Log
import org.armman.supervisor.data.auth.session.OfflineCredentialCache
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val REQUIRED_ROLE = "SUPERVISOR"

/**
 * Real [AuthRepository] backed by the auth-service API, with an offline fallback.
 *
 * Online: calls [AuthApi], decodes the access token's subject id, enforces the SUPERVISOR-only
 * role check (this app is single-role; a Sakhi/Manager/Admin account must be rejected here per
 * the SRS's strict per-app role separation), then persists both the session (for "stay logged
 * in") and a salted credential hash (for offline re-login).
 *
 * Offline (per [ConnectivityChecker]): never attempts the network call — instead verifies
 * against [OfflineCredentialCache] and, on a match, restores the last [SessionStore] session so
 * the Supervisor can keep working until connectivity returns.
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
  private val authApi: AuthApi,
  private val jwtClaimsDecoder: JwtClaimsDecoder,
  private val sessionStore: SessionStore,
  private val offlineCredentialCache: OfflineCredentialCache,
  private val connectivityChecker: ConnectivityChecker,
) : AuthRepository {

  override suspend fun login(request: LoginRequest): LoginResult {
    if (!connectivityChecker.isOnline()) {
      return loginOffline(request)
    }
    return try {
      loginOnline(request)
    } catch (e: IOException) {
      // The connectivity check passed but the call itself didn't reach/complete with the
      // server (timeout, connection reset, DNS failure) — distinct from the deliberate
      // offline path above, and distinct from a server-returned error.
      LoginResult.Failure(LoginFailureReason.NETWORK_ERROR)
    } catch (e: JwtDecodeException) {
      LoginResult.Failure(LoginFailureReason.UNKNOWN)
    }
  }

  override suspend fun logout() {
    try {
      sessionStore.clearSession()
    } catch (e: Exception) {
      // Logout must never throw — the user must still land on the login screen.
      // Log the failure so it is visible in debug builds and crash-reporting tools.
      Log.e("RemoteAuthRepository", "clearSession() failed during logout; session may not be cleared", e)
    }
  }

  private suspend fun loginOnline(request: LoginRequest): LoginResult {
    val response = authApi.login(LoginRequestDto(username = request.username, password = request.password))
    val body = response.body()
    return when {
      response.isSuccessful && body?.success == true && body.data != null ->
        onLoginSucceeded(request, body.data)
      response.code() == 400 -> LoginResult.Failure(LoginFailureReason.VALIDATION_ERROR)
      response.code() == 401 -> LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS)
      else -> LoginResult.Failure(LoginFailureReason.UNKNOWN)
    }
  }

  private fun onLoginSucceeded(request: LoginRequest, data: LoginResponseData): LoginResult {
    if (REQUIRED_ROLE !in data.roles) {
      return LoginResult.Failure(LoginFailureReason.WRONG_ROLE)
    }
    val claims = jwtClaimsDecoder.decode(data.accessToken)
    val expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + data.expiresIn
    val session = UserSession(
      username = request.username,
      subjectId = claims.subjectId,
      roles = data.roles,
      projectId = data.projectId,
      geographyUnitId = data.geographyUnitId,
      accessToken = data.accessToken,
      refreshToken = data.refreshToken,
      accessTokenExpiresAtEpochSeconds = expiresAtEpochSeconds,
    )
    sessionStore.saveSession(session)
    offlineCredentialCache.store(request.username, request.password.toCharArray(), session)
    return LoginResult.Success(session)
  }

  private fun loginOffline(request: LoginRequest): LoginResult {
    // The session snapshot lives in OfflineCredentialCache, not SessionStore — a logout
    // performed while offline clears SessionStore (so the login form reappears) but must not
    // erase the one thing that lets this same Supervisor get back in without connectivity.
    val restoredSession = offlineCredentialCache.verifyAndRestoreSession(
      request.username,
      request.password.toCharArray(),
    )
    if (restoredSession == null) {
      return if (offlineCredentialCache.hasAnyEntry()) {
        LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS)
      } else {
        LoginResult.Failure(LoginFailureReason.OFFLINE_NO_CACHE)
      }
    }
    // Reject a restored session whose access token has already expired — any API call
    // made with it would receive a 401.  The user must reconnect and log in online to
    // refresh the token.
    if (restoredSession.accessTokenExpiresAtEpochSeconds < System.currentTimeMillis() / 1000L) {
      return LoginResult.Failure(LoginFailureReason.OFFLINE_SESSION_EXPIRED)
    }
    // Restore "stay logged in" too, so a second offline relaunch skips the form again.
    sessionStore.saveSession(restoredSession)
    return LoginResult.Success(restoredSession)
  }
}
