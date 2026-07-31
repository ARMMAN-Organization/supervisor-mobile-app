package org.armman.supervisor.ui.assignitem

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssignItemDetailViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun savedStateHandle(sakhiId: String) =
    SavedStateHandle(mapOf(Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG to sakhiId))

  private class TestRepository(
    private var details: Map<String, SakhiDetail> = mapOf(
      "sakhi-1" to SakhiDetail("Sushil", "Unrestricted Armman", "Mumbai"),
    ),
    private val transactions: Map<String, List<TransactionEntry>> = mapOf(
      "sakhi-1" to listOf(
        TransactionEntry("txn-1", "10 Oct 2025", TransactionType.CONSUMED, listOf(TransactionItemEntry("Sugar strips", 20))),
      ),
    ),
    private var shouldFailDelete: Boolean = false,
  ) : AssignItemRepository {
    var deleteCallCount = 0
      private set
    var lastDeletedId: String? = null
      private set

    fun setDetails(newDetails: Map<String, SakhiDetail>) {
      details = newDetails
    }

    fun failDelete(fail: Boolean) {
      shouldFailDelete = fail
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")

    override suspend fun getSakhis(locationId: String?): List<SakhiOption> = error("not used")

    override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail =
      details[sakhiId] ?: error("Unknown sakhi id: $sakhiId")

    override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> = transactions[sakhiId].orEmpty()

    override suspend fun getPrograms(): List<LocationOption> = error("not used")

    override suspend fun getInventoryItems(): List<InventoryItem> = error("not used")

    override suspend fun submitTransaction(submission: TransactionSubmission): TransactionEntry = error("not used")

    override suspend fun updateTransaction(transactionId: String, submission: TransactionSubmission): TransactionEntry =
      error("not used")

    override suspend fun deleteTransaction(sakhiId: String, transactionId: String) {
      deleteCallCount++
      lastDeletedId = transactionId
      if (shouldFailDelete) error("delete failed")
    }
  }

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = AssignItemDetailViewModel(TestRepository(), savedStateHandle("sakhi-1"))
    assertEquals(AssignItemDetailUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `loads detail and transactions for known sakhi`() = runTest(dispatcher) {
    val viewModel = AssignItemDetailViewModel(TestRepository(), savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemDetailUiState.Success
    assertEquals("Sushil", state.detail.sakhiName)
    assertEquals(1, state.transactions.size)
    assertEquals("Sugar strips", state.transactions.first().items.first().itemName)
  }

  @Test
  fun `refresh re-fetches without transiently emitting Loading over existing content`() = runTest(dispatcher) {
    val viewModel = AssignItemDetailViewModel(TestRepository(), savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)

    viewModel.refresh()

    // Unlike the initial load, refresh() must not flash Loading over the screen that's already
    // showing valid content — this is what caused the visible flicker on screen entry/resume.
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)
  }

  @Test
  fun `retry after error re-fetches and can reach Success`() = runTest(dispatcher) {
    val repo = TestRepository(details = emptyMap())
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Error)

    repo.setDetails(mapOf("sakhi-1" to SakhiDetail("Sushil", "Unrestricted Armman", "Mumbai")))
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)
  }

  // --- Negative ---

  @Test
  fun `unknown sakhi id moves to Error`() = runTest(dispatcher) {
    val viewModel = AssignItemDetailViewModel(TestRepository(), savedStateHandle("unknown-id"))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `empty transaction list for known sakhi is passed through as empty`() = runTest(dispatcher) {
    val repo = TestRepository(transactions = emptyMap())
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemDetailUiState.Success
    assertTrue(state.transactions.isEmpty())
  }

  @Test
  fun `multiple items within one transaction all pass through`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            "txn-1",
            "10 Oct 2025",
            TransactionType.CONSUMED,
            listOf(TransactionItemEntry("Sugar strips", 20), TransactionItemEntry("HB strip", 20)),
          ),
        ),
      ),
    )
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemDetailUiState.Success
    assertEquals(2, state.transactions.first().items.size)
  }

  // --- Delete ---

  @Test
  fun `deleting a transaction calls repository and reloads`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDeleteTransaction("txn-1")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.deleteCallCount)
    assertEquals("txn-1", repo.lastDeletedId)
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)
  }

  @Test
  fun `delete failure moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFailDelete = true)
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDeleteTransaction("txn-1")
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Error)
  }
}
