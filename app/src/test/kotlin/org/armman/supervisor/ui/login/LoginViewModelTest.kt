package org.armman.supervisor.ui.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.R
import org.armman.supervisor.data.auth.AuthRepository
import org.armman.supervisor.data.auth.LoginFailureReason
import org.armman.supervisor.data.auth.LoginRequest
import org.armman.supervisor.data.auth.LoginResult
import org.armman.supervisor.data.auth.UserSession
import org.armman.supervisor.data.auth.session.SecureKeyValueStore
import org.armman.supervisor.data.auth.session.SessionStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  /** Controllable fake so tests never depend on real credential values. */
  private class FakeAuthRepository(
    var result: LoginResult = LoginResult.Success(fakeSession()),
  ) : AuthRepository {
    var loginCallCount = 0
    var lastRequest: LoginRequest? = null

    override suspend fun login(request: LoginRequest): LoginResult {
      loginCallCount++
      lastRequest = request
      return result
    }

    override suspend fun logout() { /* no session state in the fake */ }
  }

  /** In-memory fake so SessionStore can be exercised without EncryptedSharedPreferences. */
  private class FakeSecureKeyValueStore : SecureKeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
  }

  private lateinit var repository: FakeAuthRepository
  private lateinit var sessionStore: SessionStore
  private lateinit var viewModel: LoginViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeAuthRepository()
    sessionStore = SessionStore(FakeSecureKeyValueStore())
    viewModel = LoginViewModel(repository, sessionStore)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `initial state is empty with no errors when no session saved`() {
    val state = viewModel.uiState.value
    assertEquals("", state.username)
    assertEquals("", state.password)
    assertFalse(state.isSubmitting)
    assertNull(state.usernameError)
    assertNull(state.passwordError)
    assertNull(state.loginError)
    assertFalse(state.loginSucceeded)
  }

  @Test
  fun `already-logged-in session on init sets loginSucceeded immediately`() {
    sessionStore.saveSession(fakeSession())
    val vmWithSession = LoginViewModel(repository, sessionStore)

    assertTrue(vmWithSession.uiState.value.loginSucceeded)
    assertEquals(0, repository.loginCallCount)
  }

  @Test
  fun `blank username blocks submit with field error`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("")
    viewModel.onPasswordChanged("secret")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_username_required, viewModel.uiState.value.usernameError)
    assertEquals(0, repository.loginCallCount)
  }

  @Test
  fun `blank password blocks submit with field error`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_password_required, viewModel.uiState.value.passwordError)
    assertEquals(0, repository.loginCallCount)
  }

  @Test
  fun `both fields blank shows both errors`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("")
    viewModel.onPasswordChanged("")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(R.string.login_error_username_required, state.usernameError)
    assertEquals(R.string.login_error_password_required, state.passwordError)
    assertEquals(0, repository.loginCallCount)
  }

  @Test
  fun `whitespace-only username is treated as blank`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("   ")
    viewModel.onPasswordChanged("secret")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_username_required, viewModel.uiState.value.usernameError)
    assertEquals(0, repository.loginCallCount)
  }

  @Test
  fun `leading and trailing spaces are trimmed before submission`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("  super01  ")
    viewModel.onPasswordChanged("Super@123")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("super01", repository.lastRequest?.username)
    assertTrue(viewModel.uiState.value.loginSucceeded)
  }

  @Test
  fun `username case is preserved, not lowercased, since the real lookup is case-sensitive`() =
    runTest(dispatcher) {
      viewModel.onUsernameChanged("Super01")
      viewModel.onPasswordChanged("Super@123")
      viewModel.onLoginClicked()
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals("Super01", repository.lastRequest?.username)
    }

  @Test
  fun `special characters in username are passed through unchanged`() = runTest(dispatcher) {
    val weirdUsername = "user@#\$%.name"
    viewModel.onUsernameChanged(weirdUsername)
    viewModel.onPasswordChanged("secret")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(weirdUsername, repository.lastRequest?.username)
  }

  @Test
  fun `very long username is passed through unchanged without crashing`() = runTest(dispatcher) {
    val longUsername = "u".repeat(200)
    viewModel.onUsernameChanged(longUsername)
    viewModel.onPasswordChanged("secret")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(longUsername, repository.lastRequest?.username)
  }

  @Test
  fun `successful login sets loginSucceeded and stops submitting`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("Super@123")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.loginSucceeded)
    assertFalse(state.isSubmitting)
    assertNull(state.loginError)
  }

  @Test
  fun `invalid credentials shows login error`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("wrong")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(R.string.login_error_invalid_credentials, state.loginError)
    assertFalse(state.loginSucceeded)
    assertFalse(state.isSubmitting)
  }

  @Test
  fun `network error shows distinct message from invalid credentials`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.NETWORK_ERROR)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("pw")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    val errorRes = viewModel.uiState.value.loginError
    assertEquals(R.string.login_error_network, errorRes)
    assertNotEquals(R.string.login_error_invalid_credentials, errorRes)
  }

  @Test
  fun `wrong role shows a distinct not-set-up-as-supervisor message`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.WRONG_ROLE)
    viewModel.onUsernameChanged("sakhi01")
    viewModel.onPasswordChanged("pw")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    val errorRes = viewModel.uiState.value.loginError
    assertEquals(R.string.login_error_wrong_role, errorRes)
    assertNotEquals(R.string.login_error_invalid_credentials, errorRes)
  }

  @Test
  fun `offline with no cache shows a distinct connect-once-first message`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.OFFLINE_NO_CACHE)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("pw")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_offline_no_cache, viewModel.uiState.value.loginError)
  }

  @Test
  fun `offline verification failure shows same error as online invalid credentials`() =
    runTest(dispatcher) {
      repository.result = LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS)
      viewModel.onUsernameChanged("super01")
      viewModel.onPasswordChanged("wrongOffline")
      viewModel.onLoginClicked()
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals(R.string.login_error_invalid_credentials, viewModel.uiState.value.loginError)
    }

  @Test
  fun `unknown failure shows generic error`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.UNKNOWN)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("pw")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_generic, viewModel.uiState.value.loginError)
  }

  @Test
  fun `validation error shows generic error`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.VALIDATION_ERROR)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("pw")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.login_error_generic, viewModel.uiState.value.loginError)
  }

  @Test
  fun `typing clears field and login errors`() = runTest(dispatcher) {
    repository.result = LoginResult.Failure(LoginFailureReason.INVALID_CREDENTIALS)
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("wrong")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onPasswordChanged("wrong2")

    val state = viewModel.uiState.value
    assertNull(state.loginError)
    assertNull(state.passwordError)
  }

  @Test
  fun `onLoginHandled resets the one-shot success flag`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("Super@123")
    viewModel.onLoginClicked()
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLoginHandled()

    assertFalse(viewModel.uiState.value.loginSucceeded)
  }

  @Test
  fun `submit is ignored while already submitting`() = runTest(dispatcher) {
    viewModel.onUsernameChanged("super01")
    viewModel.onPasswordChanged("Super@123")
    viewModel.onLoginClicked() // starts submitting; coroutine not yet run
    viewModel.onLoginClicked() // second click before the scheduler advances

    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repository.loginCallCount)
  }

  private companion object {
    fun fakeSession() = UserSession(
      username = "u1",
      subjectId = "subject-1",
      roles = listOf("SUPERVISOR"),
      projectId = null,
      geographyUnitId = null,
      accessToken = "access-token",
      refreshToken = "refresh-token",
      accessTokenExpiresAtEpochSeconds = 9_999_999_999L,
    )
  }
}
