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
class DueVisitViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val sampleItem = DueVisitItem(
    beneficiaryId = "b-1",
    beneficiaryName = "Sushma T Test",
    villageName = "SushilTest",
    uniqueId = "unique-1",
    registrationType = RegistrationType.WOMEN,
    visit = "ANC2.1",
    scheduledDate = "19-08-2026",
    balancedDays = 10,
    risk = "Hypertension",
  )

  private class TestRepository(
    private var shouldFail: Boolean = false,
    private val dueItems: List<DueVisitItem> = emptyList(),
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")

    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> = error("not used")

    override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> = error("not used")

    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")

    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> {
      if (shouldFail) error("due visits failed")
      return dueItems
    }

    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> {
      if (shouldFail) error("expiring soon failed")
      return dueItems
    }

    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> {
      if (shouldFail) error("missed visits failed")
      return dueItems
    }

    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> = error("not used")

    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")

    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")

    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")

    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle(kind: DueVisitKind = DueVisitKind.DUE) = SavedStateHandle(
    mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1", Routes.DUE_VISIT_KIND_ARG to kind.name),
  )

  @Test
  fun `initial state is Loading`() {
    val viewModel = DueVisitViewModel(savedStateHandle(), TestRepository())
    assertEquals(DueVisitUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `DUE kind loads via getDueVisits and reaches Success`() = runTest(dispatcher) {
    val viewModel = DueVisitViewModel(savedStateHandle(DueVisitKind.DUE), TestRepository(dueItems = listOf(sampleItem)))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as DueVisitUiState.Success
    assertEquals(1, state.items.size)
  }

  @Test
  fun `EXPIRING_SOON kind loads via getVisitsExpiringSoon`() = runTest(dispatcher) {
    val viewModel = DueVisitViewModel(savedStateHandle(DueVisitKind.EXPIRING_SOON), TestRepository(dueItems = listOf(sampleItem)))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as DueVisitUiState.Success).items.isNotEmpty())
  }

  @Test
  fun `MISSED kind loads via getMissedVisits`() = runTest(dispatcher) {
    val viewModel = DueVisitViewModel(savedStateHandle(DueVisitKind.MISSED), TestRepository(dueItems = listOf(sampleItem)))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as DueVisitUiState.Success).items.isNotEmpty())
  }

  @Test
  fun `empty list renders as Success not Error`() = runTest(dispatcher) {
    val viewModel = DueVisitViewModel(savedStateHandle(), TestRepository(dueItems = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()
    val state = viewModel.uiState.value as DueVisitUiState.Success
    assertTrue(state.items.isEmpty())
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, dueItems = listOf(sampleItem))
    val viewModel = DueVisitViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is DueVisitUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is DueVisitUiState.Success)
  }
}
