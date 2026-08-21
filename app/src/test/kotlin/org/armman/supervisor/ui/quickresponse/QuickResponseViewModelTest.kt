package org.armman.supervisor.ui.quickresponse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickResponseViewModelTest {
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
    private val requests: List<QuickResponseRequest> = emptyList(),
    private var shouldFail: Boolean = false,
    private var decideException: Exception? = null,
  ) : QuickResponseRepository {
    var decideCount: Int = 0
      private set
    var getRequestsCallCount: Int = 0
      private set
    var lastDecision: Triple<String, QuickResponseDecision, String?>? = null
      private set
    var lastEscalationAction: Pair<String, QuickResponseEscalationAction>? = null
      private set
    var lastAcknowledgedId: String? = null
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    fun failNextDecideWith(exception: Exception?) {
      decideException = exception
    }

    override suspend fun getRequests(): List<QuickResponseRequest> {
      getRequestsCallCount++
      if (shouldFail) error("load failed")
      return requests
    }

    override suspend fun decide(requestId: String, decision: QuickResponseDecision, notes: String?) {
      decideCount++
      lastDecision = Triple(requestId, decision, notes)
      decideException?.let { throw it }
    }

    override suspend fun decideEscalation(requestId: String, action: QuickResponseEscalationAction) {
      decideCount++
      lastEscalationAction = requestId to action
      decideException?.let { throw it }
    }

    override suspend fun acknowledgeEddNearing(requestId: String) {
      decideCount++
      lastAcknowledgedId = requestId
      decideException?.let { throw it }
    }
  }

  private fun request(id: String, beneficiaryName: String = "Test Beneficiary") = QuickResponseRequest(
    id = id,
    requestedAtEpochMillis = 1_000L,
    requestType = QuickResponseRequestType.LMP_CHANGE,
    beneficiaryName = beneficiaryName,
    sakhiName = null,
    sakhiId = null,
    sakhiPhoneNumber = null,
    padaName = null,
    requestStatus = null,
    riskConditions = emptyList(),
    detail = null,
  )

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = QuickResponseViewModel(TestRepository())
    assertEquals(QuickResponseUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with the repository's requests in order`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1", "Beneficiary A"), request("qr-2", "Beneficiary B")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(listOf("Beneficiary A", "Beneficiary B"), state.requests.map { it.beneficiaryName })
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")), shouldFail = true)
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is QuickResponseUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is QuickResponseUiState.Success)
  }

  @Test
  fun `onDecide removes the decided card from the list on success`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1"), request("qr-2")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDecide("qr-1", QuickResponseDecision.APPROVE)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(listOf("qr-2"), state.requests.map { it.id })
    assertEquals(Triple("qr-1", QuickResponseDecision.APPROVE, null), repo.lastDecision)
    assertNull(state.decidingRequestId)
  }

  @Test
  fun `onDecide passes rejection notes through to the repository`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDecide("qr-1", QuickResponseDecision.REJECT, "Beneficiary confirmed by phone.")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(Triple("qr-1", QuickResponseDecision.REJECT, "Beneficiary confirmed by phone."), repo.lastDecision)
  }

  @Test
  fun `onEscalationAction removes the card and forwards the action on success`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onEscalationAction("qr-1", QuickResponseEscalationAction.CLOSE)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertTrue(state.requests.isEmpty())
    assertEquals("qr-1" to QuickResponseEscalationAction.CLOSE, repo.lastEscalationAction)
  }

  @Test
  fun `onAcknowledgeEddNearing removes the card on success`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onAcknowledgeEddNearing("qr-1")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertTrue(state.requests.isEmpty())
    assertEquals("qr-1", repo.lastAcknowledgedId)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = QuickResponseViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is QuickResponseUiState.Error)
  }

  @Test
  fun `onDecide surfaces a conflict-specific message on 409 without discarding the list`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    repo.failNextDecideWith(QuickResponseDecisionException(409, "already decided"))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDecide("qr-1", QuickResponseDecision.APPROVE)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(1, state.requests.size)
    assertEquals(R.string.quick_response_error_decision_conflict, state.decisionErrorMessageRes)
    assertNull(state.decidingRequestId)
  }

  @Test
  fun `onDecide surfaces a generic failure message on non-409 errors`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    repo.failNextDecideWith(IllegalStateException("network error"))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDecide("qr-1", QuickResponseDecision.APPROVE)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(R.string.quick_response_error_decision_failed, state.decisionErrorMessageRes)
  }

  @Test
  fun `onEscalationAction surfaces a transfer-specific message on the known 501`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    repo.failNextDecideWith(QuickResponseDecisionException(501, "not implemented"))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onEscalationAction("qr-1", QuickResponseEscalationAction.TRANSFER)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(1, state.requests.size)
    assertEquals(R.string.quick_response_error_transfer_unavailable, state.decisionErrorMessageRes)
  }

  @Test
  fun `onEscalationAction with CLOSE surfaces the generic failure message on a 501 (no override for Close)`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    repo.failNextDecideWith(QuickResponseDecisionException(501, "not implemented"))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onEscalationAction("qr-1", QuickResponseEscalationAction.CLOSE)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(R.string.quick_response_error_decision_failed, state.decisionErrorMessageRes)
  }

  // --- Edge cases ---

  @Test
  fun `empty requests list reaches Success with empty list, not Error`() = runTest(dispatcher) {
    val viewModel = QuickResponseViewModel(TestRepository(requests = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertTrue(state.requests.isEmpty())
  }

  @Test
  fun `creating the viewModel fetches exactly once, before any onRetry call`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))

    QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    // Documents the contract QuickResponseScreen's first-resume guard relies on: init already
    // fetches once, so a naive resume-triggered onRetry() on first composition would double it
    // and flicker Loading -> Success -> Loading -> Success. The screen must skip that first call.
    assertEquals(1, repo.getRequestsCallCount)
  }

  @Test
  fun `onRetry triggers a distinct additional fetch beyond the initial load`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    val callsAfterInit = repo.getRequestsCallCount

    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(callsAfterInit + 1, repo.getRequestsCallCount)
  }

  @Test
  fun `a second onDecide call is ignored while one is already in flight for the same card`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDecide("qr-1", QuickResponseDecision.APPROVE)
    // Before the coroutine completes, decidingRequestId is already set — a rapid double-tap
    // must not fire a second decision call.
    viewModel.onDecide("qr-1", QuickResponseDecision.APPROVE)
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.decideCount)
  }
}
