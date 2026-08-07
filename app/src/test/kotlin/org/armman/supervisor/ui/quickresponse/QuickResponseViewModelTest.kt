package org.armman.supervisor.ui.quickresponse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
  ) : QuickResponseRepository {
    var submitCount: Int = 0
      private set
    var getRequestsCallCount: Int = 0
      private set

    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    override suspend fun getRequests(): List<QuickResponseRequest> {
      getRequestsCallCount++
      if (shouldFail) error("load failed")
      return requests
    }

    override suspend fun submitReason(requestId: String, reason: ReasonOption) {
      submitCount++
    }
  }

  private fun request(id: String, sakhiName: String) = QuickResponseRequest(
    id = id,
    requestedAtEpochMillis = 1_000L,
    requestType = QuickResponseRequestType.DATA_RESTORE,
    projectName = "Test-4",
    sakhiName = sakhiName,
    status = QuickResponseRequestStatus.PENDING,
  )

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = QuickResponseViewModel(TestRepository())
    assertEquals(QuickResponseUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial fetch reaches Success with the repository's requests in order`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1", "SakhiKomal"), request("qr-2", "SakhiMeera")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as QuickResponseUiState.Success
    assertEquals(listOf("SakhiKomal", "SakhiMeera"), state.requests.map { it.sakhiName })
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1", "SakhiKomal")), shouldFail = true)
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is QuickResponseUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is QuickResponseUiState.Success)
  }

  // --- Negative ---

  @Test
  fun `initial fetch failure moves to Error`() = runTest(dispatcher) {
    val viewModel = QuickResponseViewModel(TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is QuickResponseUiState.Error)
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
    val repo = TestRepository(requests = listOf(request("qr-1", "SakhiKomal")))

    QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()

    // Documents the contract QuickResponseScreen's first-resume guard relies on: init already
    // fetches once, so a naive resume-triggered onRetry() on first composition would double it
    // and flicker Loading -> Success -> Loading -> Success. The screen must skip that first call.
    assertEquals(1, repo.getRequestsCallCount)
  }

  @Test
  fun `onRetry triggers a distinct additional fetch beyond the initial load`() = runTest(dispatcher) {
    val repo = TestRepository(requests = listOf(request("qr-1", "SakhiKomal")))
    val viewModel = QuickResponseViewModel(repo)
    dispatcher.scheduler.advanceUntilIdle()
    val callsAfterInit = repo.getRequestsCallCount

    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(callsAfterInit + 1, repo.getRequestsCallCount)
  }
}
