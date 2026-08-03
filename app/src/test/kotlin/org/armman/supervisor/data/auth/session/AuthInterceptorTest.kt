package org.armman.supervisor.data.auth.session

import okhttp3.Request
import okhttp3.Response
import org.armman.supervisor.data.auth.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInterceptorTest {

  private fun fakeSession(accessToken: String = "access-token-123") = UserSession(
    username = "super01",
    subjectId = "subject-1",
    roles = listOf("SUPERVISOR"),
    projectId = "project-1",
    geographyUnitId = "geo-1",
    accessToken = accessToken,
    refreshToken = "refresh-token",
    accessTokenExpiresAtEpochSeconds = 1_800_000_000L,
  )

  /** Captures the request actually sent onward, without making a real network call. */
  private class CapturingChain(private val original: Request) : okhttp3.Interceptor.Chain {
    var proceededWith: Request? = null

    override fun request(): Request = original

    override fun proceed(request: Request): Response {
      proceededWith = request
      return Response.Builder()
        .request(request)
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
    }

    override fun connection() = null
    override fun call() = throw UnsupportedOperationException()
    override fun connectTimeoutMillis() = 0
    override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun readTimeoutMillis() = 0
    override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
    override fun writeTimeoutMillis() = 0
    override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
  }

  private fun request() = Request.Builder().url("https://example.armman.org/api/v1/projects").build()

  @Test
  fun `attaches the session's bearer token when a session exists`() {
    val store = FakeSecureKeyValueStore()
    val sessionStore = SessionStore(store)
    sessionStore.saveSession(fakeSession(accessToken = "abc123"))
    val interceptor = AuthInterceptor(sessionStore)
    val chain = CapturingChain(request())

    interceptor.intercept(chain)

    assertEquals("Bearer abc123", chain.proceededWith?.header("Authorization"))
  }

  @Test
  fun `does not attach an Authorization header when no session exists`() {
    val sessionStore = SessionStore(FakeSecureKeyValueStore())
    val interceptor = AuthInterceptor(sessionStore)
    val chain = CapturingChain(request())

    interceptor.intercept(chain)

    assertNull(chain.proceededWith?.header("Authorization"))
  }
}
