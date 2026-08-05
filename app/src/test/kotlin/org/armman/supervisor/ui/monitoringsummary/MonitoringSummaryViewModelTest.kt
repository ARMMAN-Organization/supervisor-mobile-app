package org.armman.supervisor.ui.monitoringsummary

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
class MonitoringSummaryViewModelTest {
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
    private val sakhisByLocation: Map<String, List<SakhiMonitoringSummary>> = mapOf(
      "loc-1" to listOf(
        SakhiMonitoringSummary("SakhiKomal", listOf(VillageMonitoringRow("SushilTest", 1, 0))),
        SakhiMonitoringSummary("SakhiMeera", listOf(VillageMonitoringRow("SushilTest1", 1, 0))),
      ),
      "loc-2" to listOf(SakhiMonitoringSummary("SakhiAsha", listOf(VillageMonitoringRow("Village2", 2, 1)))),
    ),
    private var shouldFail: Boolean = false,
  ) : MonitoringSummaryRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getMonitoringSummary(locationId: String?): List<SakhiMonitoringSummary> {
      if (shouldFail) error("monitoring summary failed")
      return sakhisByLocation[locationId].orEmpty()
    }
  }

  @Test
  fun `initial state is Loading`() {
    val viewModel = MonitoringSummaryViewModel(TestRepository())
    assertEquals(MonitoringSummaryUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with first location and multiple sakhis`() = runTest(dispatcher) {
    val viewModel = MonitoringSummaryViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MonitoringSummaryUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals(2, state.sakhis.size)
  }

  @Test
  fun `single sakhi group renders as a single-item list`() = runTest(dispatcher) {
    val viewModel = MonitoringSummaryViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MonitoringSummaryUiState.Success
    assertEquals(1, state.sakhis.size)
  }

  @Test
  fun `zero sakhi groups reaches Success with empty list`() = runTest(dispatcher) {
    val viewModel = MonitoringSummaryViewModel(TestRepository(sakhisByLocation = emptyMap()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MonitoringSummaryUiState.Success
    assertTrue(state.sakhis.isEmpty())
  }

  @Test
  fun `large number of sakhi groups is not truncated`() = runTest(dispatcher) {
    val many = (1..20).map { SakhiMonitoringSummary("Sakhi$it", listOf(VillageMonitoringRow("Village$it", it, 0))) }
    val viewModel = MonitoringSummaryViewModel(TestRepository(sakhisByLocation = mapOf("loc-1" to many)))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as MonitoringSummaryUiState.Success
    assertEquals(20, state.sakhis.size)
  }

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = MonitoringSummaryViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MonitoringSummaryUiState.Error)
  }

  @Test
  fun `failure during location change moves to Error without keeping stale data`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = MonitoringSummaryViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MonitoringSummaryUiState.Error)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = MonitoringSummaryViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is MonitoringSummaryUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is MonitoringSummaryUiState.Success)
  }
}
