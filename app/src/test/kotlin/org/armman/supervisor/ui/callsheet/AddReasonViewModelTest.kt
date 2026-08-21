package org.armman.supervisor.ui.callsheet

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.R
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddReasonViewModelTest {
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
    private val delayMs: Long = 0,
  ) : CallSheetRepository {
    var lastSubmission: ReasonSubmission? = null
      private set
    var submitCount: Int = 0
      private set

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
    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")
    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")

    override suspend fun submitReason(submission: ReasonSubmission) {
      if (delayMs > 0) delay(delayMs)
      submitCount++
      if (shouldFail) error("submit failed")
      lastSubmission = submission
    }
  }

  private fun savedStateHandle(
    context: ReasonContext = ReasonContext.FOLLOWUP_PENDING,
    sakhiId: String? = null,
    itemId: String? = "item-1",
  ) = SavedStateHandle(
    buildMap {
      put(Routes.REASON_CONTEXT_ARG, context.name)
      sakhiId?.let { put(Routes.CALL_SHEET_SAKHI_ID_ARG, it) }
      itemId?.let { put(Routes.REASON_ITEM_ID_ARG, it) }
    },
  )

  @Test
  fun `initial state has no reason selected and is not submitted`() {
    val viewModel = AddReasonViewModel(savedStateHandle(), TestRepository())
    val state = viewModel.formState.value
    assertNull(state.selectedReason)
    assertEquals("", state.remark)
    assertFalse(state.submitted)
  }

  @Test
  fun `submit without selecting a reason shows validation error and does not call repository`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()
    assertNotNull(viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `selecting a reason clears validation error and submit succeeds`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onReasonSelected(ReasonChoice("HOSPITALIZE", R.string.followup_pending_reason_hospitalize))
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    assertEquals("HOSPITALIZE", repo.lastSubmission?.reasonCode)
    assertEquals("item-1", repo.lastSubmission?.itemId)
  }

  @Test
  fun `submit with blank remark succeeds`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onReasonSelected(ReasonChoice("COUNSELLING", R.string.followup_pending_reason_counselling))
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    assertNull(repo.lastSubmission?.remark)
  }

  @Test
  fun `itemId and sakhiId are URL-decoded, matching how callSheetAddReason encodes them`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(
      savedStateHandle(sakhiId = "sakhi%2F1", itemId = "item%261"),
      repo,
    )
    viewModel.onReasonSelected(ReasonChoice("HOSPITALIZE", R.string.followup_pending_reason_hospitalize))
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("sakhi/1", repo.lastSubmission?.sakhiId)
    assertEquals("item&1", repo.lastSubmission?.itemId)
  }

  @Test
  fun `LastSync context submits keyed by sakhiId not itemId`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(
      savedStateHandle(context = ReasonContext.LAST_SYNC, sakhiId = "sakhi-1", itemId = null),
      repo,
    )
    viewModel.onReasonSelected(ReasonChoice("FORGOT_TO_SYNC", R.string.last_sync_reason_forgot_to_sync))
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("sakhi-1", repo.lastSubmission?.sakhiId)
    assertNull(repo.lastSubmission?.itemId)
  }

  @Test
  fun `submit failure surfaces error and keeps the selected reason`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    val choice = ReasonChoice("HOSPITALIZE", R.string.followup_pending_reason_hospitalize)
    viewModel.onReasonSelected(choice)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.formState.value
    assertFalse(state.submitted)
    assertNotNull(state.submitErrorMessage)
    assertEquals(choice, state.selectedReason)
  }

  @Test
  fun `rapid double submit does not fire two submissions`() = runTest(dispatcher) {
    val repo = TestRepository(delayMs = 100)
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onReasonSelected(ReasonChoice("HOSPITALIZE", R.string.followup_pending_reason_hospitalize))

    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.submitCount)
  }
}
