package org.armman.supervisor.ui.registrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationsViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class TestRepository(
    private val locations: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A"), LocationOption("loc-2", "Zone B")),
    private val sakhisByLocation: Map<String, List<SakhiRegistrationSummary>> = mapOf(
      "loc-1" to listOf(
        SakhiRegistrationSummary("SakhiKomal", 2, 0, 0, listOf(VillageRegistrationRow("SushilTest", 1, 1))),
        SakhiRegistrationSummary("SakhiMeera", 1, 0, 0, listOf(VillageRegistrationRow("SushilTest1", 1, 0))),
      ),
      "loc-2" to listOf(SakhiRegistrationSummary("SakhiAsha", 3, 1, 1, listOf(VillageRegistrationRow("Village2", 2, 1)))),
    ),
    private var shouldFail: Boolean = false,
  ) : RegistrationsRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getRegistrations(locationId: String?): List<SakhiRegistrationSummary> {
      if (shouldFail) error("registrations failed")
      return sakhisByLocation[locationId].orEmpty()
    }
  }

  @Test
  fun `initial state is Loading`() {
    val viewModel = RegistrationsViewModel(TestRepository())
    assertEquals(RegistrationsUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with first location and multiple sakhis`() = runTest(dispatcher) {
    val viewModel = RegistrationsViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as RegistrationsUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals(2, state.sakhis.size)
    assertEquals(2, state.sakhis.first().badgeCount)
  }

  @Test
  fun `single sakhi group renders as a single-item list`() = runTest(dispatcher) {
    val viewModel = RegistrationsViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as RegistrationsUiState.Success
    assertEquals(1, state.sakhis.size)
    assertEquals(1, state.sakhis.first().motherTarget)
  }

  @Test
  fun `zero sakhi groups reaches Success with empty list`() = runTest(dispatcher) {
    val viewModel = RegistrationsViewModel(TestRepository(sakhisByLocation = emptyMap()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as RegistrationsUiState.Success
    assertTrue(state.sakhis.isEmpty())
  }

  @Test
  fun `large number of sakhi groups is not truncated`() = runTest(dispatcher) {
    val many = (1..20).map {
      SakhiRegistrationSummary("Sakhi$it", it, 0, 0, listOf(VillageRegistrationRow("Village$it", it, 0)))
    }
    val viewModel = RegistrationsViewModel(TestRepository(sakhisByLocation = mapOf("loc-1" to many)))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as RegistrationsUiState.Success
    assertEquals(20, state.sakhis.size)
  }

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = RegistrationsViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is RegistrationsUiState.Error)
  }

  @Test
  fun `failure during location change moves to Error without keeping stale data`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = RegistrationsViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is RegistrationsUiState.Error)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = RegistrationsViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is RegistrationsUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is RegistrationsUiState.Success)
  }
}
