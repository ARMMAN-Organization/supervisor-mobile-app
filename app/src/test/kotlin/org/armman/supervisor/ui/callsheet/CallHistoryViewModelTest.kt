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
class CallHistoryViewModelTest {
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
    private val sakhi: SakhiOption = SakhiOption("sakhi-1", "Sushil"),
    private var entries: List<CallLogEntry> = emptyList(),
    private var shouldFail: Boolean = false,
  ) : CallSheetRepository {
    fun failNextCalls(fail: Boolean) {
      shouldFail = fail
    }

    fun setEntries(newEntries: List<CallLogEntry>) {
      entries = newEntries
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")

    override suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary> = error("not used")

    override suspend fun getSakhiOption(sakhiId: String): SakhiOption {
      if (shouldFail) error("sakhi lookup failed")
      return sakhi
    }

    override suspend fun getCallHistory(sakhiId: String): List<CallLogEntry> {
      if (shouldFail) error("history failed")
      return entries
    }

    override suspend fun logCall(submission: CallLogSubmission): CallLogEntry = error("not used")
  }

  private fun savedStateHandle(sakhiId: String = "sakhi-1") =
    SavedStateHandle(mapOf(Routes.CALL_SHEET_SAKHI_ID_ARG to sakhiId))

  private fun sampleEntry(id: String, timestamp: Long) = CallLogEntry(
    id = id,
    timestampEpochMillis = timestamp,
    connected = CallConnected.NO,
    successOutcome = null,
    failureReason = FailureReason.RINGING,
    responder = null,
    durationMinutes = null,
    notes = null,
    followUpAction = null,
  )

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = CallHistoryViewModel(savedStateHandle(), TestRepository())
    assertEquals(CallHistoryUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `initial load reaches Success with sakhi and entries`() = runTest(dispatcher) {
    val repo = TestRepository(entries = listOf(sampleEntry("call-1", 1_000L)))
    val viewModel = CallHistoryViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallHistoryUiState.Success
    assertEquals("Sushil", state.sakhi.name)
    assertEquals(1, state.entries.size)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = CallHistoryViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is CallHistoryUiState.Error)

    repo.failNextCalls(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is CallHistoryUiState.Success)
  }

  @Test
  fun `onResumed reloads history, picking up newly logged calls`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = CallHistoryViewModel(savedStateHandle(), repo)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue((viewModel.uiState.value as CallHistoryUiState.Success).entries.isEmpty())

    repo.setEntries(listOf(sampleEntry("call-1", 1_000L)))
    viewModel.onResumed()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallHistoryUiState.Success
    assertEquals(1, state.entries.size)
  }

  // --- Negative ---

  @Test
  fun `initial load failure moves to Error`() = runTest(dispatcher) {
    val viewModel = CallHistoryViewModel(savedStateHandle(), TestRepository(shouldFail = true))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is CallHistoryUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `sakhi with zero call logs reaches Success with empty list, not Error`() = runTest(dispatcher) {
    val viewModel = CallHistoryViewModel(savedStateHandle(), TestRepository(entries = emptyList()))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as CallHistoryUiState.Success
    assertTrue(state.entries.isEmpty())
  }

  @Test
  fun `missing sakhiId argument throws immediately`() {
    try {
      CallHistoryViewModel(SavedStateHandle(), TestRepository())
      throw AssertionError("expected an exception for a missing sakhiId arg")
    } catch (e: IllegalStateException) {
      // expected: checkNotNull on the missing SavedStateHandle argument
    }
  }
}
