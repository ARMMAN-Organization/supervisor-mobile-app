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
class ClosurePendingViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val sampleItem = ClosurePendingItem(
    beneficiaryId = "b-1",
    beneficiaryName = "Child 1 K Salvi",
    villageName = "SushilTest",
    uniqueId = "unique-1",
    registrationType = RegistrationType.CHILD,
    registrationDate = "06-08-2026",
    dateOfBirth = "11-08-2025",
    risk = "Managed",
    overdueDays = 3,
  )

  private class TestRepository(
    private var shouldFail: Boolean = false,
    private val items: List<ClosurePendingItem> = emptyList(),
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")
    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> = error("not used")
    override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")
    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = error("not used")
    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")
    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = error("not used")
    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> = error("not used")

    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> {
      if (shouldFail) error("closure pending failed")
      return items
    }

    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")
    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")
    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle() = SavedStateHandle(mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1"))

  @Test
  fun `initial state is Loading`() {
    val viewModel = ClosurePendingViewModel(savedStateHandle(), TestRepository())
    assertEquals(ClosurePendingUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `successful fetch reaches Success with non-negative overdue days`() = runTest(dispatcher) {
    val viewModel = ClosurePendingViewModel(savedStateHandle(), TestRepository(items = listOf(sampleItem)))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as ClosurePendingUiState.Success
    assertEquals(1, state.items.size)
    assertTrue(state.items.first().overdueDays >= 0)
  }

  @Test
  fun `empty list renders as Success not Error`() = runTest(dispatcher) {
    val viewModel = ClosurePendingViewModel(savedStateHandle(), TestRepository(items = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as ClosurePendingUiState.Success).items.isEmpty())
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, items = listOf(sampleItem))
    val viewModel = ClosurePendingViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is ClosurePendingUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is ClosurePendingUiState.Success)
  }
}
