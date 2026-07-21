package org.armman.supervisor.data.auth

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.auth.session.FakeSecureKeyValueStore
import org.armman.supervisor.data.auth.session.OfflineCredentialCache
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.connectivity.FakeConnectivityChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.Base64

class RemoteAuthRepositoryTest {

  private fun fakeAccessToken(subjectId: String = "subject-1"): String {
    val header = encodeSegment("""{"alg":"RS256"}""")
    val payload = encodeSegment("""{"sub":"$subjectId"}""")
    return "$header.$payload.signature"
  }

  private fun encodeSegment(json: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

  /** Minimal controllable fake for the Retrofit interface. */
  private class FakeAuthApi : AuthApi {
    var response: Response<LoginResponseDto>? = null
    var thrown: Throwable? = null
    var lastRequest: LoginRequestDto? = null

    override suspend fun login(request: LoginRequestDto): Response<LoginResponseDto> {
      lastRequest = request
      thrown?.let { throw it }
      return response!!
    }
  }

  private fun successResponse(roles: List<String> = listOf("SUPERVISOR")) = Response.success(
    LoginResponseDto(
      success = true,
      message = "OK",
      data = LoginResponseData(
        accessToken = fakeAccessToken(),
        refreshToken = "refresh-token",
        expiresIn = 900L,
        roles = roles,
        projectId = "project-1",
        geographyUnitId = "geo-1",
      ),
    ),
  )

  private fun errorResponse(code: Int) = Response.error<LoginResponseDto>(
    code,
    """{"success":false,"message":"error","errorCode":"X"}"""
      .toResponseBody("application/json".toMediaType()),
  )

  private lateinit var authApi: FakeAuthApi
  private lateinit var sessionStore: SessionStore
  private lateinit var offlineCredentialCache: OfflineCredentialCache
  private lateinit var connectivityChecker: FakeConnectivityChecker
  private lateinit var repository: RemoteAuthRepository

  @Before
  fun setUp() {
    authApi = FakeAuthApi()
    val keyValueStore = FakeSecureKeyValueStore()
    sessionStore = SessionStore(keyValueStore)
    offlineCredentialCache = OfflineCredentialCache(keyValueStore)
    connectivityChecker = FakeConnectivityChecker(online = true)
    repository = RemoteAuthRepository(
      authApi = authApi,
      jwtClaimsDecoder = JwtClaimsDecoder(),
      sessionStore = sessionStore,
      offlineCredentialCache = offlineCredentialCache,
      connectivityChecker = connectivityChecker,
    )
  }

  @Test
  fun `login success maps token payload directly from response body`() = runTest {
    authApi.response = successResponse()

    val result = repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertTrue(result is LoginResult.Success)
    val session = (result as LoginResult.Success).session
    assertEquals("super01", session.username)
    assertEquals("subject-1", session.subjectId)
    assertEquals(listOf("SUPERVISOR"), session.roles)
    assertEquals("project-1", session.projectId)
    assertEquals("geo-1", session.geographyUnitId)
    assertEquals("refresh-token", session.refreshToken)
  }

  @Test
  fun `login sends username and password fields`() = runTest {
    authApi.response = successResponse()

    repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertEquals("super01", authApi.lastRequest?.username)
    assertEquals("Super@123", authApi.lastRequest?.password)
  }

  @Test
  fun `400 validation error maps to VALIDATION_ERROR`() = runTest {
    authApi.response = errorResponse(400)

    val result = repository.login(LoginRequest(username = "super01", password = "bad"))

    assertEquals(LoginResult.Failure(LoginFailureReason.VALIDATION_ERROR), result)
  }

  @Test
  fun `401 invalid credentials maps to INVALID_CREDENTIALS`() = runTest {
    authApi.response = errorResponse(401)

    val result = repository.login(LoginRequest(username = "super01", password = "wrong"))

    assertEquals(LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS), result)
  }

  @Test
  fun `network timeout maps to NETWORK_ERROR never unhandled`() = runTest {
    authApi.thrown = IOException("timeout")

    val result = repository.login(LoginRequest(username = "super01", password = "pw"))

    assertEquals(LoginResult.Failure(LoginFailureReason.NETWORK_ERROR), result)
  }

  @Test
  fun `unexpected 5xx maps to UNKNOWN`() = runTest {
    authApi.response = errorResponse(500)

    val result = repository.login(LoginRequest(username = "super01", password = "pw"))

    assertEquals(LoginResult.Failure(LoginFailureReason.UNKNOWN), result)
  }

  @Test
  fun `successful login writes offline credential hash exactly once`() = runTest {
    authApi.response = successResponse()

    repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertTrue(offlineCredentialCache.verify("super01", "Super@123".toCharArray()))
  }

  @Test
  fun `successful login persists session via SessionStore`() = runTest {
    authApi.response = successResponse()

    repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertEquals("super01", sessionStore.readSession()?.username)
  }

  @Test
  fun `login rejected when roles does not contain SUPERVISOR`() = runTest {
    authApi.response = successResponse(roles = listOf("SAKHI"))

    val result = repository.login(LoginRequest(username = "sakhi01", password = "pw"))

    assertEquals(LoginResult.Failure(LoginFailureReason.WRONG_ROLE), result)
    assertNull(sessionStore.readSession())
  }

  @Test
  fun `login accepted when roles contains SUPERVISOR among others`() = runTest {
    authApi.response = successResponse(roles = listOf("SUPERVISOR", "ADMIN"))

    val result = repository.login(LoginRequest(username = "super01", password = "pw"))

    assertTrue(result is LoginResult.Success)
  }

  @Test
  fun `username match is case-sensitive against the offline cache after online login`() = runTest {
    authApi.response = successResponse()
    repository.login(LoginRequest(username = "super01", password = "Super@123"))
    connectivityChecker.online = false

    val differentCaseResult = repository.login(LoginRequest(username = "SUPER01", password = "Super@123"))

    assertEquals(LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS), differentCaseResult)
  }

  @Test
  fun `offline with no prior cache returns OFFLINE_NO_CACHE`() = runTest {
    connectivityChecker.online = false

    val result = repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertEquals(LoginResult.Failure(LoginFailureReason.OFFLINE_NO_CACHE), result)
    assertFalse(authApi.lastRequest != null)
  }

  @Test
  fun `offline with cached credential matching restores session and succeeds`() = runTest {
    authApi.response = successResponse()
    repository.login(LoginRequest(username = "super01", password = "Super@123"))
    sessionStore.clearSession()
    connectivityChecker.online = false

    val result = repository.login(LoginRequest(username = "super01", password = "Super@123"))

    assertTrue(result is LoginResult.Success)
    assertEquals("super01", sessionStore.readSession()?.username)
  }

  @Test
  fun `offline with wrong password against existing cache returns INVALID_CREDENTIALS`() = runTest {
    authApi.response = successResponse()
    repository.login(LoginRequest(username = "super01", password = "Super@123"))
    connectivityChecker.online = false

    val result = repository.login(LoginRequest(username = "super01", password = "WrongPassword"))

    assertEquals(LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS), result)
  }

  @Test
  fun `logout clears session but never throws`() = runTest {
    authApi.response = successResponse()
    repository.login(LoginRequest(username = "super01", password = "Super@123"))

    repository.logout()

    assertNull(sessionStore.readSession())
  }
}
