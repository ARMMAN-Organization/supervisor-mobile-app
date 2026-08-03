package org.armman.supervisor.ui.assignitem

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.armman.supervisor.data.assignitem.TransactionDeleteResult
import org.armman.supervisor.data.assignitem.TransactionSubmitResult
import org.armman.supervisor.data.assignitem.TransactionUpdateResult
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
        TransactionEntry(listOf("txn-1"), "10 Oct 2025", TransactionType.CONSUMED, listOf(TransactionItemEntry("txn-1", "Sugar strips", 20))),
      ),
    ),
    private var shouldFailDelete: Boolean = false,
  ) : AssignItemRepository {
    var deleteCallCount = 0
      private set
    var lastDeletedIds: List<String>? = null
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

    override suspend fun submitTransaction(submission: TransactionSubmission): TransactionSubmitResult = error("not used")

    override suspend fun updateTransaction(submission: TransactionSubmission): TransactionUpdateResult = error("not used")

    override suspend fun deleteTransaction(sakhiId: String, transactionIds: List<String>): TransactionDeleteResult {
      deleteCallCount++
      lastDeletedIds = transactionIds
      if (shouldFailDelete) error("delete failed")
      return TransactionDeleteResult.Synced
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
  fun `transactions for different submissions pass through as separate cards`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(listOf("txn-1"), "10 Oct 2025", TransactionType.CONSUMED, listOf(TransactionItemEntry("txn-1", "Sugar strips", 20))),
          TransactionEntry(listOf("txn-2"), "10 Oct 2025", TransactionType.CONSUMED, listOf(TransactionItemEntry("txn-2", "HB strip", 20))),
        ),
      ),
    )
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemDetailUiState.Success
    assertEquals(2, state.transactions.size)
    assertTrue(state.transactions.all { it.items.size == 1 })
  }

  @Test
  fun `a grouped multi-item submission passes through as one card`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1", "txn-2"),
            "10 Oct 2025",
            TransactionType.CONSUMED,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 20), TransactionItemEntry("txn-2", "HB strip", 20)),
          ),
        ),
      ),
    )
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AssignItemDetailUiState.Success
    assertEquals(1, state.transactions.size)
    assertEquals(2, state.transactions.first().items.size)
    assertEquals(listOf("txn-1", "txn-2"), state.transactions.first().ids)
  }

  // --- Delete ---

  @Test
  fun `deleting a transaction calls repository with all ids in the group and reloads`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDeleteTransactions(listOf("txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.deleteCallCount)
    assertEquals(listOf("txn-1"), repo.lastDeletedIds)
    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Success)
  }

  @Test
  fun `deleting a multi-item group passes every id to the repository in one call`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDeleteTransactions(listOf("txn-1", "txn-2"))
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.deleteCallCount)
    assertEquals(listOf("txn-1", "txn-2"), repo.lastDeletedIds)
  }

  @Test
  fun `delete failure moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFailDelete = true)
    val viewModel = AssignItemDetailViewModel(repo, savedStateHandle("sakhi-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDeleteTransactions(listOf("txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AssignItemDetailUiState.Error)
  }
}
