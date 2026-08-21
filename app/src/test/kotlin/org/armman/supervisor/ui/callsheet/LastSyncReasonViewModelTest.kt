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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LastSyncReasonViewModelTest {
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
    private var shouldFail: Boolean = false,
    private val sakhi: SakhiOption = SakhiOption("sakhi-1", "SakhiMeera"),
    private val reason: SyncReasonItem? = null,
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")
    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> = error("not used")

    override suspend fun getSakhiOption(sakhiId: String): SakhiOption {
      if (shouldFail) error("sakhi lookup failed")
      return sakhi
    }

    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = error("not used")
    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")
    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> = error("not used")
    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")
    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")

    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? {
      if (shouldFail) error("sync reason failed")
      return reason
    }

    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle() = SavedStateHandle(mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1"))

  @Test
  fun `initial state is Loading`() {
    val viewModel = LastSyncReasonViewModel(savedStateHandle(), TestRepository())
    assertEquals(LastSyncReasonUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `successful fetch with a recorded reason reaches Success`() = runTest(dispatcher) {
    val viewModel = LastSyncReasonViewModel(
      savedStateHandle(),
      TestRepository(reason = SyncReasonItem(syncDate = "10-08-2026", reason = "Forgot to sync")),
    )
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as LastSyncReasonUiState.Success
    assertEquals("SakhiMeera", state.sakhiName)
    assertEquals("Forgot to sync", state.reason?.reason)
  }

  @Test
  fun `no reason recorded yet renders as Success with null reason`() = runTest(dispatcher) {
    val viewModel = LastSyncReasonViewModel(savedStateHandle(), TestRepository(reason = null))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as LastSyncReasonUiState.Success
    assertNull(state.reason)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = LastSyncReasonViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is LastSyncReasonUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is LastSyncReasonUiState.Success)
  }
}
