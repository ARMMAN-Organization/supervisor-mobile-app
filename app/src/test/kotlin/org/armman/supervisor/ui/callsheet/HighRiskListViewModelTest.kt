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
class HighRiskListViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val ancItem = HighRiskItem(beneficiaryId = "b-1", beneficiaryName = "Sushma T Test", villageName = "SushilTest", uniqueId = "u-1", riskName = "Hypertension")
  private val pncItem = HighRiskItem(beneficiaryId = "b-2", beneficiaryName = "Other Test", villageName = "SushilTest", uniqueId = "u-2", riskName = "Age")

  private class TestRepository(
    private var shouldFail: Boolean = false,
    private val ancItems: List<HighRiskItem> = emptyList(),
    private val pncItems: List<HighRiskItem> = emptyList(),
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
    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")

    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> {
      if (shouldFail) error("high risk failed")
      return when (type) {
        HighRiskType.ANC -> ancItems
        HighRiskType.PNC -> pncItems
      }
    }

    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")
    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle(type: HighRiskType = HighRiskType.ANC) = SavedStateHandle(
    mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1", Routes.HIGH_RISK_TYPE_ARG to type.name),
  )

  @Test
  fun `initial state is Loading`() {
    val viewModel = HighRiskListViewModel(savedStateHandle(), TestRepository())
    assertEquals(HighRiskListUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `ANC and PNC types return distinct lists`() = runTest(dispatcher) {
    val repo = TestRepository(ancItems = listOf(ancItem), pncItems = listOf(pncItem))

    val ancViewModel = HighRiskListViewModel(savedStateHandle(HighRiskType.ANC), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf(ancItem), (ancViewModel.uiState.value as HighRiskListUiState.Success).items)

    val pncViewModel = HighRiskListViewModel(savedStateHandle(HighRiskType.PNC), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertEquals(listOf(pncItem), (pncViewModel.uiState.value as HighRiskListUiState.Success).items)
  }

  @Test
  fun `empty list renders as Success not Error`() = runTest(dispatcher) {
    val viewModel = HighRiskListViewModel(savedStateHandle(), TestRepository())
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as HighRiskListUiState.Success).items.isEmpty())
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true, ancItems = listOf(ancItem))
    val viewModel = HighRiskListViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is HighRiskListUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is HighRiskListUiState.Success)
  }
}
