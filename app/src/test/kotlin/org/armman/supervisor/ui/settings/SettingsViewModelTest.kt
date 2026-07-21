package org.armman.supervisor.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.auth.AuthRepository
import org.armman.supervisor.data.auth.LoginRequest
import org.armman.supervisor.data.auth.LoginResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  private class FakeAuthRepository : AuthRepository {
    var logoutCallCount = 0

    override suspend fun login(request: LoginRequest): LoginResult =
      throw UnsupportedOperationException("not used by these tests")

    override suspend fun logout() {
      logoutCallCount++
    }
  }

  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: SettingsViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = FakeAuthRepository()
    viewModel = SettingsViewModel(repository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `exposed version matches BuildConfig`() {
    assertEquals(BuildConfig.VERSION_NAME, viewModel.versionName)
  }

  @Test
  fun `onActionTapped does not throw for any action`() {
    SettingsAction.entries.forEach { action -> viewModel.onActionTapped(action) }
  }

  @Test
  fun `repeated onActionTapped calls do not throw or accumulate broken state`() {
    repeat(5) { viewModel.onActionTapped(SettingsAction.CHECK_UPDATE) }
    assertEquals(BuildConfig.VERSION_NAME, viewModel.versionName)
  }

  @Test
  fun `onLogOutClicked shows confirmation dialog without logging out yet`() {
    viewModel.onLogOutClicked()

    assertTrue(viewModel.uiState.value.showLogoutConfirmation)
    assertEquals(0, repository.logoutCallCount)
  }

  @Test
  fun `onLogoutConfirmDismissed hides dialog without logging out`() {
    viewModel.onLogOutClicked()
    viewModel.onLogoutConfirmDismissed()

    assertFalse(viewModel.uiState.value.showLogoutConfirmation)
    assertEquals(0, repository.logoutCallCount)
  }

  @Test
  fun `onLogoutConfirmed calls repository logout then the callback and hides dialog`() =
    runTest(dispatcher) {
      var callbackInvoked = false
      viewModel.onLogOutClicked()
      viewModel.onLogoutConfirmed { callbackInvoked = true }
      dispatcher.scheduler.advanceUntilIdle()

      assertEquals(1, repository.logoutCallCount)
      assertTrue(callbackInvoked)
      assertFalse(viewModel.uiState.value.showLogoutConfirmation)
    }
}
