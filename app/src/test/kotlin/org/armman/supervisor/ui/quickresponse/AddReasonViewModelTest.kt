package org.armman.supervisor.ui.quickresponse

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.R
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  ) : QuickResponseRepository {
    var lastRequestId: String? = null
      private set
    var lastReason: ReasonOption? = null
      private set
    var submitCount: Int = 0
      private set

    override suspend fun getRequests(): List<QuickResponseRequest> = error("not used")

    override suspend fun submitReason(requestId: String, reason: ReasonOption) {
      if (delayMs > 0) delay(delayMs)
      submitCount++
      if (shouldFail) error("submit failed")
      lastRequestId = requestId
      lastReason = reason
    }
  }

  private fun savedStateHandle(requestId: String = "qr-1") =
    SavedStateHandle(mapOf(Routes.QUICK_RESPONSE_REQUEST_ID_ARG to requestId))

  // --- Positive ---

  @Test
  fun `submitting a selected reason succeeds`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle("qr-1"), repo)
    viewModel.onReasonSelected(ReasonOption.APPROVE)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.formState.value.submitted)
    assertEquals("qr-1", repo.lastRequestId)
    assertEquals(ReasonOption.APPROVE, repo.lastReason)
  }

  @Test
  fun `selecting a reason after a validation error clears it`() {
    val viewModel = AddReasonViewModel(savedStateHandle(), TestRepository())
    viewModel.onSubmit()
    assertEquals(R.string.quick_response_error_reason_required, viewModel.formState.value.validationErrorRes)

    viewModel.onReasonSelected(ReasonOption.PENDING)

    assertNull(viewModel.formState.value.validationErrorRes)
  }

  // --- Negative / validation ---

  @Test
  fun `submit with nothing selected shows reason-required error and does not call repository`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(R.string.quick_response_error_reason_required, viewModel.formState.value.validationErrorRes)
    assertEquals(0, repo.submitCount)
  }

  @Test
  fun `repository failure on submit surfaces an error and keeps the form filled`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFail = true)
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onReasonSelected(ReasonOption.REJECT)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.formState.value
    assertFalse(state.submitted)
    assertFalse(state.isSubmitting)
    assertEquals(ReasonOption.REJECT, state.selectedReason)
    assertTrue(state.submitErrorMessage != null)
  }

  // --- Edge cases ---

  @Test
  fun `rapid double submit only calls the repository once`() = runTest(dispatcher) {
    val repo = TestRepository(delayMs = 1_000)
    val viewModel = AddReasonViewModel(savedStateHandle(), repo)
    viewModel.onReasonSelected(ReasonOption.RESTORED)

    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.submitCount)
  }

  @Test
  fun `uses the requestId from SavedStateHandle when submitting`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddReasonViewModel(savedStateHandle("qr-42"), repo)
    viewModel.onReasonSelected(ReasonOption.PENDING)

    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("qr-42", repo.lastRequestId)
  }
}
