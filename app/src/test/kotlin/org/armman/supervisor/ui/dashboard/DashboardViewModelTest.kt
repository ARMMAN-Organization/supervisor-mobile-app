package org.armman.supervisor.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun sampleData(locationId: String?) = DashboardData(
    supervisorName = "Niharika Supervisor",
    roleLabel = "Field Supervisor",
    date = "Wed, 1 April 2026",
    unsyncedCount = 0,
    kpi = KpiSummary(0, 0, 0, 0),
    visitSummary = listOf(SummaryRow(SummaryRowLabel.TOTAL, 0, 0)),
    registrationSummary = emptyList(),
    riskSummary = emptyList(),
    monitoringSummary = emptyList(),
    staleSakhis = if (locationId == "loc-with-stale") {
      listOf(StaleSakhiEntry("Sakhi A", "10 days ago", 10))
    } else {
      emptyList()
    },
  )

  private class TestRepository(
    private val locations: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A"), LocationOption("loc-2", "Zone B")),
    private var shouldFail: Boolean = false,
    private val delaysMs: Map<String, Long> = emptyMap(),
    private val dataFactory: (String?) -> DashboardData,
  ) : DashboardRepository {
    var getDashboardCallCount = 0
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getDashboard(locationId: String?): DashboardData {
      getDashboardCallCount++
      delaysMs[locationId]?.let { delay(it) }
      if (shouldFail) error("dashboard fetch failed")
      return dataFactory(locationId)
    }
  }

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    assertEquals(DashboardUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with repository data`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals(2, state.locations.size)
    assertEquals(sampleData("loc-1"), state.data)
    assertTrue(!state.isRefreshing)
  }

  @Test
  fun `selecting a location updates selection and refetches`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertEquals("loc-2", state.selectedLocationId)
    assertEquals(sampleData("loc-2"), state.data)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is DashboardUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is DashboardUiState.Success)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is DashboardUiState.Error)
  }

  @Test
  fun `location selection failure moves to Error not stale Success`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is DashboardUiState.Error)
  }

  @Test
  fun `retry while repository keeps failing stays in Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is DashboardUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `empty locations list still reaches Success with null selection`() = runTest(dispatcher) {
    val repo = TestRepository(locations = emptyList(), dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertTrue(state.locations.isEmpty())
    assertNull(state.selectedLocationId)
  }

  @Test
  fun `empty stale Sakhi list is passed through as empty`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertTrue(state.data.staleSakhis.isEmpty())
  }

  @Test
  fun `all-zero kpi and summary values pass through unchanged`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertEquals(KpiSummary(0, 0, 0, 0), state.data.kpi)
  }

  @Test
  fun `rapid double location selection resolves to the last one requested`() = runTest(dispatcher) {
    val repo = TestRepository(
      delaysMs = mapOf("loc-1" to 1_000L),
      dataFactory = ::sampleData,
    )
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    // "loc-1" (slow) selected first, "loc-2" (fast) immediately after — the ViewModel cancels the
    // in-flight "loc-1" fetch, so the final state must reflect "loc-2", not whichever completes first.
    viewModel.onLocationSelected("loc-1")
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as DashboardUiState.Success
    assertEquals("loc-2", state.selectedLocationId)
    assertEquals(sampleData("loc-2"), state.data)
  }
}
