package org.armman.supervisor.data.auth.session

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.auth.JwtClaimsDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.armman.supervisor.data.auth.UserSession

class TokenAuthenticatorTest {

  private fun fakeAccessToken(subjectId: String = "subject-1"): String {
    val header = encodeSegment("""{"alg":"RS256"}""")
    val payload = encodeSegment("""{"sub":"$subjectId"}""")
    return "$header.$payload.signature"
  }

  private fun encodeSegment(json: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

  private fun fakeSession(accessToken: String) = UserSession(
    username = "super01",
    subjectId = "subject-1",
    roles = listOf("SUPERVISOR"),
    projectId = "project-1",
    geographyUnitId = "geo-1",
    accessToken = accessToken,
    refreshToken = "refresh-token-1",
    accessTokenExpiresAtEpochSeconds = 1_800_000_000L,
  )

  /** Fake for the raw OkHttp call [TokenAuthenticator] makes to `auth/refresh` — intercepts
   * before any real network I/O and returns a canned response, so tests exercise the real
   * `client.newCall(request).execute()` path without a live server. */
  private class FakeRefreshInterceptor : okhttp3.Interceptor {
    var refreshCallCount = 0
    val requestBodiesSeen = mutableListOf<String>()
    var responseCode = 200
    var responseBodyJson = "{}"
    var thrown: IOException? = null

    override fun intercept(chain: okhttp3.Interceptor.Chain): Response {
      refreshCallCount++
      val buffer = okio.Buffer()
      chain.request().body!!.writeTo(buffer)
      requestBodiesSeen.add(buffer.readUtf8())
      thrown?.let { throw it }
      return Response.Builder()
        .request(chain.request())
        .protocol(Protocol.HTTP_1_1)
        .code(responseCode)
        .message(if (responseCode in 200..299) "OK" else "Error")
        .body(responseBodyJson.toResponseBody("application/json".toMediaType()))
        .build()
    }
  }

  private fun successBody(accessToken: String, refreshToken: String = "refresh-token-2") = """
    {"success":true,"message":"OK","data":{"accessToken":"$accessToken","refreshToken":"$refreshToken",
    "expiresIn":900,"roles":["SUPERVISOR"],"projectId":"project-1","geographyUnitId":"geo-1"}}
  """.trimIndent()

  private fun request(bearerToken: String?) = Request.Builder()
    .url("https://api.armman.org/api/v1/projects")
    .apply { if (bearerToken != null) header("Authorization", "Bearer $bearerToken") }
    .build()

  private fun unauthorizedResponse(req: Request, priorResponse: Response? = null) = Response.Builder()
    .request(req)
    .protocol(Protocol.HTTP_1_1)
    .code(401)
    .message("Unauthorized")
    .apply { if (priorResponse != null) priorResponse(priorResponse) }
    .build()

  private lateinit var refreshInterceptor: FakeRefreshInterceptor
  private lateinit var sessionStore: SessionStore
  private lateinit var authenticator: TokenAuthenticator

  @Before
  fun setUp() {
    refreshInterceptor = FakeRefreshInterceptor()
    val client = OkHttpClient.Builder().addInterceptor(refreshInterceptor).build()
    sessionStore = SessionStore(FakeSecureKeyValueStore())
    authenticator = TokenAuthenticator(client, JwtClaimsDecoder(), sessionStore)
  }

  @Test
  fun `refreshes the token and retries with the new access token on a 401`() {
    val newToken = fakeAccessToken()
    sessionStore.saveSession(fakeSession(accessToken = "expired-token"))
    refreshInterceptor.responseBodyJson = successBody(accessToken = newToken)
    val failedRequest = request(bearerToken = "expired-token")

    val retried = authenticator.authenticate(null, unauthorizedResponse(failedRequest))

    assertEquals("Bearer $newToken", retried?.header("Authorization"))
    assertEquals(newToken, sessionStore.readSession()?.accessToken)
    assertEquals(true, refreshInterceptor.requestBodiesSeen.single().contains("refresh-token-1"))
  }

  @Test
  fun `refresh token itself invalid clears the session and gives up`() {
    sessionStore.saveSession(fakeSession(accessToken = "expired-token"))
    refreshInterceptor.responseCode = 401
    refreshInterceptor.responseBodyJson = """{"success":false,"message":"invalid refresh token"}"""
    val failedRequest = request(bearerToken = "expired-token")

    val retried = authenticator.authenticate(null, unauthorizedResponse(failedRequest))

    assertNull(retried)
    assertNull(sessionStore.readSession())
  }

  @Test
  fun `no stored session gives up without calling refresh`() {
    val failedRequest = request(bearerToken = "expired-token")

    val retried = authenticator.authenticate(null, unauthorizedResponse(failedRequest))

    assertNull(retried)
    assertEquals(0, refreshInterceptor.refreshCallCount)
  }

  @Test
  fun `does not retry a second time for the same request chain`() {
    sessionStore.saveSession(fakeSession(accessToken = "expired-token"))
    refreshInterceptor.responseBodyJson = successBody(accessToken = fakeAccessToken())
    val original = request(bearerToken = "expired-token")
    val firstFailure = unauthorizedResponse(original)
    val secondFailure = unauthorizedResponse(original, priorResponse = firstFailure)

    val retried = authenticator.authenticate(null, secondFailure)

    assertNull(retried)
    assertEquals(0, refreshInterceptor.refreshCallCount)
  }

  @Test
  fun `a request that already carries a session-refreshed token is retried without calling refresh again`() {
    val newToken = fakeAccessToken()
    sessionStore.saveSession(fakeSession(accessToken = newToken))
    val failedRequest = request(bearerToken = "expired-token")

    val retried = authenticator.authenticate(null, unauthorizedResponse(failedRequest))

    assertEquals("Bearer $newToken", retried?.header("Authorization"))
    assertEquals(0, refreshInterceptor.refreshCallCount)
  }

  @Test
  fun `concurrent 401s trigger only one refresh call`() {
    sessionStore.saveSession(fakeSession(accessToken = "expired-token"))
    refreshInterceptor.responseBodyJson = successBody(accessToken = fakeAccessToken())
    val startLatch = CountDownLatch(1)
    val results = java.util.concurrent.ConcurrentLinkedQueue<Request?>()

    val threads = (1..5).map {
      Thread {
        startLatch.await()
        val failedRequest = request(bearerToken = "expired-token")
        results.add(authenticator.authenticate(null, unauthorizedResponse(failedRequest)))
      }
    }
    threads.forEach { it.start() }
    startLatch.countDown()
    threads.forEach { it.join(TimeUnit.SECONDS.toMillis(5)) }

    assertEquals(1, refreshInterceptor.refreshCallCount)
    assertEquals(5, results.size)
    assertEquals(0, results.count { it == null })
  }
}
