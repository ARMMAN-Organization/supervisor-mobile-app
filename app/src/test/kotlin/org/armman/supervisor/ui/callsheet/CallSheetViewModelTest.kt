package org.armman.supervisor.ui.callsheet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallSheetViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val sampleStats = CallSheetStats(
    rows = listOf(CallSheetStatValue(CallSheetStatKind.VISIT_DUE, updated = 0, count = 0)),
    lastDataSyncDate = "23-07-2026",
  )

  private class TestRepository(
    private val locations: List<LocationOption> = listOf(LocationOption("loc-1", "Zone A"), LocationOption("loc-2", "Zone B")),
    private val summariesByLocation: Map<String, List<SakhiCallSummary>>,
    private var shouldFail: Boolean = false,
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> {
      if (shouldFail) error("locations failed")
      return locations
    }

    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> {
      if (shouldFail) error("summaries failed")
      return summariesByLocation[locationId].orEmpty()
    }

    override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = error("not used")

    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")

    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> = error("not used")

    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")

    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")

    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")

    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun summary(sakhiId: String, name: String, lastCalledAt: Long? = null) =
    SakhiCallSummary(SakhiOption(sakhiId, name), sampleStats, lastCalledAt)

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = CallSheetViewModel(TestRepository(summariesByLocation = emptyMap()))
    assertEquals(CallSheetUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with first location selected`() = runTest(dispatcher) {
    val repo = TestRepository(
      summariesByLocation = mapOf("loc-1" to listOf(summary("sakhi-1", "Sushil"))),
    )
    val viewModel = CallSheetViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallSheetUiState.Success
    assertEquals("loc-1", state.selectedLocationId)
    assertEquals("Sushil", state.sakhiSummaries.first().sakhi.name)
  }

  @Test
  fun `selecting a location updates selection and sakhi summaries`() = runTest(dispatcher) {
    val repo = TestRepository(
      summariesByLocation = mapOf(
        "loc-1" to listOf(summary("sakhi-1", "Sushil")),
        "loc-2" to listOf(summary("sakhi-2", "Asha")),
      ),
    )
    val viewModel = CallSheetViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onLocationSelected("loc-2")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallSheetUiState.Success
    assertEquals("loc-2", state.selectedLocationId)
    assertEquals("Asha", state.sakhiSummaries.first().sakhi.name)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(summariesByLocation = mapOf("loc-1" to listOf(summary("sakhi-1", "Sushil"))), shouldFail = true)
    val viewModel = CallSheetViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is CallSheetUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is CallSheetUiState.Success)
  }

  @Test
  fun `onResumed reloads the currently selected location`() = runTest(dispatcher) {
    val repo = TestRepository(
      summariesByLocation = mapOf("loc-1" to listOf(summary("sakhi-1", "Sushil", lastCalledAt = 100L))),
    )
    val viewModel = CallSheetViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onResumed()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallSheetUiState.Success
    assertEquals(100L, state.sakhiSummaries.first().lastCalledAtEpochMillis)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = CallSheetViewModel(TestRepository(summariesByLocation = emptyMap(), shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is CallSheetUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `selected location with no sakhis reaches Success with empty list, not Error`() = runTest(dispatcher) {
    val viewModel = CallSheetViewModel(TestRepository(summariesByLocation = emptyMap()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallSheetUiState.Success
    assertTrue(state.sakhiSummaries.isEmpty())
  }

  @Test
  fun `empty locations list still reaches Success with null selection`() = runTest(dispatcher) {
    val repo = TestRepository(locations = emptyList(), summariesByLocation = emptyMap())
    val viewModel = CallSheetViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallSheetUiState.Success
    assertTrue(state.locations.isEmpty())
    assertNull(state.selectedLocationId)
  }

  @Test
  fun `onResumed before Success is reached is a no-op`() = runTest(dispatcher) {
    val viewModel = CallSheetViewModel(TestRepository(summariesByLocation = emptyMap()))
    viewModel.onResumed()
    assertEquals(CallSheetUiState.Loading, viewModel.uiState.value)
  }
}
