package org.armman.supervisor.ui.callsheet

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FollowupPendingViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val sampleItem = FollowupPendingItem(
    callLogId = "call-1",
    callDate = "04-08-2026",
    notes = "Discussed referral",
  )

  private class TestRepository(
    private var shouldFail: Boolean = false,
    private var items: List<FollowupPendingItem> = emptyList(),
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    fun setItems(newItems: List<FollowupPendingItem>) {
      items = newItems
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")
    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> = error("not used")
    override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")
    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = error("not used")
    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")
    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> {
      if (shouldFail) error("followup pending failed")
      return items
    }

    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")
    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")
    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")
    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle() = SavedStateHandle(mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1"))

  @Test
  fun `initial state is Loading`() {
    val viewModel = FollowupPendingViewModel(savedStateHandle(), TestRepository())
    assertEquals(FollowupPendingUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `successful fetch reaches Success with items`() = runTest(dispatcher) {
    val viewModel = FollowupPendingViewModel(savedStateHandle(), TestRepository(items = listOf(sampleItem)))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as FollowupPendingUiState.Success
    assertEquals(1, state.items.size)
  }

  @Test
  fun `empty list renders as Success not Error`() = runTest(dispatcher) {
    val viewModel = FollowupPendingViewModel(savedStateHandle(), TestRepository(items = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as FollowupPendingUiState.Success).items.isEmpty())
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, items = listOf(sampleItem))
    val viewModel = FollowupPendingViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is FollowupPendingUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is FollowupPendingUiState.Success)
  }

  @Test
  fun `onResumed re-fetches after a successful load, dropping an item actioned elsewhere`() = runTest(dispatcher) {
    val repo = TestRepository(items = listOf(sampleItem))
    val viewModel = FollowupPendingViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, (viewModel.uiState.value as FollowupPendingUiState.Success).items.size)

    // Simulate the item disappearing server-side after Add Reason was submitted on another screen.
    repo.setItems(emptyList())
    viewModel.onResumed()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue((viewModel.uiState.value as FollowupPendingUiState.Success).items.isEmpty())
  }

  @Test
  fun `onResumed before the initial load completes does not trigger a second fetch`() = runTest(dispatcher) {
    val viewModel = FollowupPendingViewModel(savedStateHandle(), TestRepository(items = listOf(sampleItem)))
    // Still Loading — onResumed should be a no-op rather than racing the in-flight initial load.
    viewModel.onResumed()
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(1, (viewModel.uiState.value as FollowupPendingUiState.Success).items.size)
  }
}
