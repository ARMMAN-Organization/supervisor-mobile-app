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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallOutcomeViewModelTest {
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
    var lastSubmission: CallLogSubmission? = null
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

    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry {
      if (delayMs > 0) delay(delayMs)
      submitCount++
      if (shouldFail) error("submit failed")
      lastSubmission = submission
      return CallLogEntry(
        id = "call-1",
        timestampEpochMillis = 1_000L,
        connected = submission.connected,
        successOutcome = submission.successOutcome,
        failureReason = submission.failureReason,
        responder = submission.responder,
        durationMinutes = submission.durationMinutes,
        notes = submission.notes,
        followUpAction = submission.followUpAction,
      )
    }

    override suspend fun getDueVisits(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem> = error("not used")

    override suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem> = error("not used")

    override suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem> = error("not used")

    override suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem> = error("not used")

    override suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem? = error("not used")

    override suspend fun submitReason(submission: ReasonSubmission) = error("not used")
  }

  private fun savedStateHandle() = SavedStateHandle(mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to "sakhi-1"))

  // --- Positive: Yes/connected path ---

  @Test
  fun `selecting Yes shows no responder field until an outcome is chosen`() {
    val viewModel = CallOutcomeViewModel(savedStateHandle(), TestRepository())
    viewModel.onConnectedChanged(CallConnected.YES)
    assertFalse(viewModel.formState.value.showResponder)
  }

  @Test
  fun `selecting Picked up the call and talked shows the responder field`() {
    val viewModel = CallOutcomeViewModel(savedStateHandle(), TestRepository())
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.PICKED_UP_TALKED)
    assertTrue(viewModel.formState.value.showResponder)
  }

  @Test
  fun `selecting a non-talked success outcome hides the responder field`() {
    val viewModel = CallOutcomeViewModel(savedStateHandle(), TestRepository())
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.CALL_BACK)
    assertFalse(viewModel.formState.value.showResponder)
  }

  @Test
  fun `submitting a valid Yes plus talked plus responder call succeeds`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.PICKED_UP_TALKED)
    viewModel.onResponderChanged(CallResponder.RELATIVE)
    viewModel.onDurationChanged("5")
    viewModel.onNotesChanged("Discussed follow-up")
    viewModel.onFollowUpActionChanged("Schedule visit")

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    val submission = requireNotNull(repo.lastSubmission)
    assertEquals(CallConnected.YES, submission.connected)
    assertEquals(SuccessOutcome.PICKED_UP_TALKED, submission.successOutcome)
    assertEquals(CallResponder.RELATIVE, submission.responder)
    assertEquals(5, submission.durationMinutes)
    assertEquals("Discussed follow-up", submission.notes)
    assertEquals("Schedule visit", submission.followUpAction)
  }

  @Test
  fun `submitting Yes with a non-talked outcome succeeds without responder or optional fields`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.CALL_BACK)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    val submission = requireNotNull(repo.lastSubmission)
    assertEquals(SuccessOutcome.CALL_BACK, submission.successOutcome)
    assertNull(submission.responder)
    assertNull(submission.notes)
    assertNull(submission.followUpAction)
  }

  @Test
  fun `blank optional notes and follow-up submit as null`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.CALL_BACK)
    viewModel.onNotesChanged("   ")
    viewModel.onFollowUpActionChanged("")

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val submission = requireNotNull(repo.lastSubmission)
    assertNull(submission.notes)
    assertNull(submission.followUpAction)
  }

  // --- Positive: No/not-connected path ---

  @Test
  fun `selecting No shows no duration notes or follow-up fields`() {
    val viewModel = CallOutcomeViewModel(savedStateHandle(), TestRepository())
    viewModel.onConnectedChanged(CallConnected.NO)
    assertFalse(viewModel.formState.value.showResponder)
  }

  @Test
  fun `submitting a valid No call succeeds with only failure reason set`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.NO)
    viewModel.onFailureReasonChanged(FailureReason.OUT_OF_NETWORK)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    val submission = requireNotNull(repo.lastSubmission)
    assertEquals(CallConnected.NO, submission.connected)
    assertEquals(FailureReason.OUT_OF_NETWORK, submission.failureReason)
    assertNull(submission.successOutcome)
    assertNull(submission.responder)
    assertNull(submission.durationMinutes)
  }

  // --- Validation / negative ---

  @Test
  fun `submit with nothing selected shows connected-required error and does not call repository`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.call_outcome_error_connected_required, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `submit with Yes but no success outcome is blocked`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.call_outcome_error_success_outcome_required, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `submit with talked outcome but no responder is blocked`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.PICKED_UP_TALKED)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.call_outcome_error_responder_required, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `submit with No but no failure reason is blocked`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.NO)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.call_outcome_error_failure_reason_required, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `non-numeric duration is rejected and does not submit`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.CALL_BACK)
    viewModel.onDurationChanged("abc")

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.call_outcome_error_duration_invalid, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `rapid double submit only calls the repository once`() = runTest(dispatcher) {
    val repo = TestRepository(delayMs = 1_000)
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.NO)
    viewModel.onFailureReasonChanged(FailureReason.RINGING)

    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.submitCount)
  }

  @Test
  fun `repository failure on submit surfaces an error and keeps the form filled`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = CallOutcomeViewModel(savedStateHandle(), repo)
    viewModel.onConnectedChanged(CallConnected.NO)
    viewModel.onFailureReasonChanged(FailureReason.RINGING)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.formState.value
    assertFalse(state.submitted)
    assertFalse(state.isSubmitting)
    assertEquals(FailureReason.RINGING, state.failureReason)
    assertTrue(state.submitErrorMessage != null)
  }

  @Test
  fun `toggling from Yes back to No clears Yes-path fields`() {
    val viewModel = CallOutcomeViewModel(savedStateHandle(), TestRepository())
    viewModel.onConnectedChanged(CallConnected.YES)
    viewModel.onSuccessOutcomeChanged(SuccessOutcome.PICKED_UP_TALKED)
    viewModel.onResponderChanged(CallResponder.RELATIVE)
    viewModel.onDurationChanged("5")

    viewModel.onConnectedChanged(CallConnected.NO)

    val state = viewModel.formState.value
    assertNull(state.successOutcome)
    assertNull(state.responder)
    assertEquals("", state.durationMinutesText)
  }
}
