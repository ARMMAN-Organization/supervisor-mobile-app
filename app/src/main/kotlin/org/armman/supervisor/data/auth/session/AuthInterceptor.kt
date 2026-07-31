package org.armman.supervisor.data.auth.session

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches the current session's access token to every request. `auth/login` and `auth/refresh`
 * don't need one (that's the point of those endpoints), but sending a stale/absent header on
 * them is harmless — the server ignores Authorization on unauthenticated routes. */
class AuthInterceptor @Inject constructor(
  private val sessionStore: SessionStore,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val accessToken = sessionStore.readSession()?.accessToken
    val authorized = if (accessToken != null) {
      request.newBuilder().addHeader("Authorization", "Bearer $accessToken").build()
    } else {
      request
    }
    return chain.proceed(authorized)
  }
}
