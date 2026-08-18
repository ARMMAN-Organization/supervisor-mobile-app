package org.armman.supervisor.data.auth.session

import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.auth.JwtClaimsDecoder
import org.armman.supervisor.data.auth.LoginResponseDto
import org.armman.supervisor.data.auth.UserSession
import org.armman.supervisor.di.UnauthenticatedClient
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes an expired access token on a 401 and retries the request once, so a session stays
 * usable across its whole "stay logged in" lifetime instead of failing every call once the
 * short-lived access token expires (which, before this, is exactly what happened — nothing in
 * the app ever exchanged the stored refresh token for a new access token).
 *
 * Calls `auth/refresh` as a plain synchronous OkHttp request rather than through the Retrofit
 * suspend-fun [org.armman.supervisor.data.auth.AuthApi]. [okhttp3.Authenticator.authenticate] is
 * a synchronous callback invoked on OkHttp's own dispatcher thread — bridging into a suspend
 * Retrofit call from there (via `runBlocking`) is exactly the code shape that broke under R8 in
 * this app's release build: `isMinifyEnabled=false` fixed the release-only "Something went
 * wrong" on every authenticated screen, and further bisection (dontobfuscate / dontoptimize)
 * showed shrinking specifically was the culprit, not renaming or inlining — i.e. R8 was tree-
 * shaking something in that call path that plain reflection-based `-keep` rules didn't surface.
 * A raw synchronous OkHttp call avoids the exotic bridging pattern entirely, matching the
 * standard `Authenticator` recipe used across the Android ecosystem.
 *
 * [client] must be built with neither [AuthInterceptor] nor this authenticator attached, so the
 * refresh call itself is never sent a stale bearer token and can never trigger a recursive 401
 * back into this same authenticator.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
  @UnauthenticatedClient private val client: OkHttpClient,
  private val jwtClaimsDecoder: JwtClaimsDecoder,
  private val sessionStore: SessionStore,
) : Authenticator {

  private val gson = Gson()

  // Guards concurrent 401s so simultaneous requests trigger one refresh call, not one each.
  private val refreshLock = Object()

  override fun authenticate(route: Route?, response: Response): Request? {
    // Already retried once for this chain of requests — don't loop forever if the server
    // keeps returning 401 even with a fresh token.
    if (responseCount(response) >= MAX_RETRIES) return null

    val session = sessionStore.readSession() ?: return null

    // Another request on a different thread may have already attached a refreshed token to the
    // request that just failed. If the session's current token differs from what this failed
    // request sent, retry with it instead of refreshing again.
    val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
    if (failedToken != null && failedToken != session.accessToken) {
      return response.request.newBuilder()
        .header("Authorization", "Bearer ${session.accessToken}")
        .build()
    }

    val refreshedSession = refreshSession(session) ?: return null
    return response.request.newBuilder()
      .header("Authorization", "Bearer ${refreshedSession.accessToken}")
      .build()
  }

  private fun refreshSession(current: UserSession): UserSession? = synchronized(refreshLock) {
    // Re-read: another thread may have refreshed while this one waited for the lock.
    val latest = sessionStore.readSession() ?: return null
    if (latest.accessToken != current.accessToken) return latest

    val requestJson = gson.toJson(mapOf("refreshToken" to latest.refreshToken))
    val request = Request.Builder()
      .url("${BuildConfig.API_BASE_URL}auth/refresh")
      .post(requestJson.toRequestBody("application/json".toMediaType()))
      .build()

    val bodyJson = try {
      client.newCall(request).execute().use { httpResponse ->
        if (!httpResponse.isSuccessful) null else httpResponse.body?.string()
      }
    } catch (e: IOException) {
      // Network failure during refresh — the refresh token itself may still be valid, so leave
      // the stored session alone; the next attempt can retry once connectivity returns.
      return null
    }
    if (bodyJson == null) {
      // Server responded and rejected the refresh token (expired/invalid) — the session cannot
      // be recovered; clear it so the next login starts clean.
      sessionStore.clearSession()
      return null
    }

    val data = try {
      gson.fromJson(bodyJson, LoginResponseDto::class.java)?.data
    } catch (e: Exception) {
      null
    }
    if (data == null) {
      sessionStore.clearSession()
      return null
    }

    val claims = try {
      jwtClaimsDecoder.decode(data.accessToken)
    } catch (e: Exception) {
      sessionStore.clearSession()
      return null
    }
    val refreshed = UserSession(
      username = latest.username,
      subjectId = claims.subjectId,
      roles = data.roles,
      projectId = data.projectId,
      geographyUnitId = data.geographyUnitId,
      accessToken = data.accessToken,
      refreshToken = data.refreshToken,
      accessTokenExpiresAtEpochSeconds = System.currentTimeMillis() / 1000L + data.expiresIn,
    )
    sessionStore.saveSession(refreshed)
    refreshed
  }

  private fun responseCount(response: Response): Int {
    var result = 1
    var prior = response.priorResponse
    while (prior != null) {
      result++
      prior = prior.priorResponse
    }
    return result
  }

  private companion object {
    const val MAX_RETRIES = 2
  }
}
