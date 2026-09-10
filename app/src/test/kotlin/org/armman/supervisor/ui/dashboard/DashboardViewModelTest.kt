package org.armman.supervisor.ui.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.notifications.AppNotification
import org.armman.supervisor.ui.notifications.NotificationStatus
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
    unreadNotificationCount = 0,
    newlyDetectedNotifications = emptyList(),
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

  private fun notification(id: String) = AppNotification(
    id = id,
    title = "Title",
    body = null,
    createdAtEpochMillis = 0L,
    status = NotificationStatus.UNREAD,
    notificationType = "MISSED_VISIT_ESCALATION",
    linkedEntityType = null,
    linkedEntityId = null,
  )

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
  fun `location selection sets isRefreshing true while in flight then false on success`() = runTest(dispatcher) {
    val repo = TestRepository(delaysMs = mapOf("loc-2" to 1_000L), dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(!(viewModel.uiState.value as DashboardUiState.Success).isRefreshing)

    viewModel.onLocationSelected("loc-2")
    val midFlight = viewModel.uiState.value as DashboardUiState.Success
    assertTrue(midFlight.isRefreshing)
    assertEquals("loc-2", midFlight.selectedLocationId)

    dispatcher.scheduler.advanceUntilIdle()
    val settled = viewModel.uiState.value as DashboardUiState.Success
    assertTrue(!settled.isRefreshing)
  }

  @Test
  fun `location selection failure does not leave a stale isRefreshing Success behind`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    repo.failNextCalls(true)
    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is DashboardUiState.Error)
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

  // --- Polling ---

  @Test
  fun `startPolling emits one newNotificationEvent per newlyDetectedNotification found on a poll tick`() = runTest(dispatcher) {
    var pollResult = sampleData("loc-1")
    val repo = TestRepository(dataFactory = { pollResult })
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val events = mutableListOf<Unit>()
    val collectJob = launch { viewModel.newNotificationEvents.toList(events) }

    pollResult = sampleData("loc-1").copy(
      newlyDetectedNotifications = listOf(notification("n-1"), notification("n-2")),
    )
    viewModel.startPolling()
    dispatcher.scheduler.advanceTimeBy(16_000L)
    dispatcher.scheduler.runCurrent()

    assertEquals(2, events.size)
    viewModel.stopPolling()
    collectJob.cancel()
  }

  @Test
  fun `stopPolling stops further ticks from firing`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.startPolling()
    val countAfterStart = repo.getDashboardCallCount
    viewModel.stopPolling()
    dispatcher.scheduler.advanceTimeBy(60_000L)
    dispatcher.scheduler.runCurrent()

    assertEquals(countAfterStart, repo.getDashboardCallCount)
  }

  @Test
  fun `a failed poll tick leaves existing dashboard state untouched`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    val stateBeforePoll = viewModel.uiState.value as DashboardUiState.Success

    repo.failNextCalls(true)
    viewModel.startPolling()
    dispatcher.scheduler.advanceTimeBy(16_000L)
    dispatcher.scheduler.runCurrent()

    assertEquals(stateBeforePoll, viewModel.uiState.value)
    viewModel.stopPolling()
  }

  @Test
  fun `a poll tick for a since-abandoned location does not overwrite a location switch that completed first`() =
    runTest(dispatcher) {
      val repo = TestRepository(delaysMs = mapOf("loc-1" to 20_000L), dataFactory = ::sampleData)
      val viewModel = DashboardViewModel(repo)
      dispatcher.scheduler.advanceUntilIdle()

      // Poll tick fires for "loc-1" and starts its slow (20s) getDashboard call.
      viewModel.startPolling()
      dispatcher.scheduler.advanceTimeBy(15_000L)
      dispatcher.scheduler.runCurrent()

      // Before that poll resolves, the user switches to "loc-2" (fast, no delay) and it completes.
      // A bounded advance, not advanceUntilIdle() — the polling job's infinite while(true) loop
      // always has a next tick scheduled, so advanceUntilIdle() would never return while it's active.
      viewModel.onLocationSelected("loc-2")
      dispatcher.scheduler.advanceTimeBy(1_000L)
      dispatcher.scheduler.runCurrent()
      val afterSwitch = viewModel.uiState.value as DashboardUiState.Success
      assertEquals("loc-2", afterSwitch.selectedLocationId)

      // The stale "loc-1" poll tick now resolves — it must not clobber the "loc-2" state.
      dispatcher.scheduler.advanceTimeBy(5_000L)
      dispatcher.scheduler.runCurrent()

      val finalState = viewModel.uiState.value as DashboardUiState.Success
      assertEquals("loc-2", finalState.selectedLocationId)
      assertEquals(sampleData("loc-2"), finalState.data)
      viewModel.stopPolling()
    }

  @Test
  fun `calling startPolling twice does not start a second overlapping loop`() = runTest(dispatcher) {
    val repo = TestRepository(dataFactory = ::sampleData)
    val viewModel = DashboardViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.startPolling()
    viewModel.startPolling()
    val countAfterStart = repo.getDashboardCallCount
    dispatcher.scheduler.advanceTimeBy(16_000L)
    dispatcher.scheduler.runCurrent()

    // A second overlapping loop would have produced 2 ticks in this window instead of 1.
    assertEquals(countAfterStart + 1, repo.getDashboardCallCount)
    viewModel.stopPolling()
  }
}
