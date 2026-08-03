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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddItemTransactionViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun savedStateHandle(sakhiId: String = "sakhi-1", editTransactionId: String? = null) =
    SavedStateHandle(
      buildMap {
        put(Routes.ASSIGN_ITEM_DETAIL_SAKHI_ID_ARG, sakhiId)
        if (editTransactionId != null) put(Routes.ADD_ITEM_TRANSACTION_EDIT_ID_ARG, editTransactionId)
      },
    )

  private class TestRepository(
    private val programs: List<LocationOption> = listOf(LocationOption("loc-1", "Unrestricted Armman")),
    private val items: List<InventoryItem> = listOf(
      InventoryItem("item-1", "Sugar strips", ItemCategory.CONSUMABLE),
      InventoryItem("item-2", "BP Monitor", ItemCategory.INSTRUMENT),
    ),
    private val transactions: Map<String, List<TransactionEntry>> = emptyMap(),
    private var shouldFailSubmit: Boolean = false,
  ) : AssignItemRepository {
    var lastSubmission: TransactionSubmission? = null
      private set
    var submitCallCount = 0
      private set
    var lastUpdatedId: String? = null
      private set
    var updateCallCount = 0
      private set

    fun failSubmit(fail: Boolean) {
      shouldFailSubmit = fail
    }

    override suspend fun getLocations(): List<LocationOption> = error("not used")

    override suspend fun getSakhis(locationId: String?): List<SakhiOption> = error("not used")

    override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail =
      SakhiDetail("Sushil", "Unrestricted Armman", "Mumbai")

    override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> = transactions[sakhiId].orEmpty()

    override suspend fun getPrograms(): List<LocationOption> = programs

    override suspend fun getInventoryItems(): List<InventoryItem> = items

    override suspend fun submitTransaction(submission: TransactionSubmission): TransactionSubmitResult {
      submitCallCount++
      if (shouldFailSubmit) error("submit failed")
      lastSubmission = submission
      val ids = submission.items.mapIndexed { index, _ -> "txn-new-$index" }
      val entry = TransactionEntry(
        ids,
        submission.transactionDate,
        submission.transactionType,
        submission.items.mapIndexed { index, item -> TransactionItemEntry(ids[index], item.itemId, item.quantity) },
      )
      return TransactionSubmitResult.Synced(entry)
    }

    override suspend fun updateTransaction(submission: TransactionSubmission): TransactionUpdateResult {
      updateCallCount++
      if (shouldFailSubmit) error("update failed")
      val ids = submission.items.mapNotNull { it.existingRowId }
      lastUpdatedId = ids.firstOrNull()
      lastSubmission = submission
      val entry = TransactionEntry(ids, submission.transactionDate, submission.transactionType, emptyList())
      return TransactionUpdateResult.Synced(entry)
    }

    override suspend fun deleteTransaction(sakhiId: String, transactionIds: List<String>): TransactionDeleteResult = error("not used")
  }

  // --- Positive ---

  @Test
  fun `initial state is Loading`() {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    assertEquals(AddItemTransactionUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `loads programs and items grouped by category`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals("Sushil", state.sakhiName)
    assertEquals("loc-1", state.selectedProgramId)
    assertTrue(state.items.any { it.category == ItemCategory.CONSUMABLE })
    assertTrue(state.items.any { it.category == ItemCategory.INSTRUMENT })
  }

  @Test
  fun `selecting program, type, date and quantity updates state`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onRemarksChanged("test remark")

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(TransactionType.CONSUMED, state.selectedType)
    assertEquals("10 Oct 2025", state.transactionDate)
    assertEquals(20, state.quantities["item-1"])
    assertEquals("test remark", state.remarks)
  }

  @Test
  fun `submit with valid form calls repository with correct payload`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val submission = requireNotNull(repo.lastSubmission)
    assertEquals("sakhi-1", submission.sakhiId)
    assertEquals("loc-1", submission.projectId)
    assertEquals(TransactionType.CONSUMED, submission.transactionType)
    assertEquals("10 Oct 2025", submission.transactionDate)
    assertEquals(listOf(TransactionItemQuantity("item-1", 20)), submission.items)

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(state.submitted)
  }

  @Test
  fun `submit with blank remarks succeeds and sends null remarks`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.HANDOVER)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 5)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(repo.lastSubmission?.remarks)
  }

  // --- Negative / validation ---

  @Test
  fun `submit without date shows date required error and does not call repository`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(TransactionFormError.DATE_REQUIRED, state.formError)
    assertEquals(0, repo.submitCallCount)
  }

  @Test
  fun `submit without type shows type required error`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(TransactionFormError.TYPE_REQUIRED, state.formError)
  }

  @Test
  fun `submit without any item quantity shows no items error`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onSubmit()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(TransactionFormError.NO_ITEMS, state.formError)
  }

  @Test
  fun `zero or negative quantity is not treated as a selected item`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onQuantityChanged("item-1", 5)
    viewModel.onQuantityChanged("item-1", 0)

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(state.quantities.isEmpty())
  }

  @Test
  fun `submit failure moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(shouldFailSubmit = true)
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AddItemTransactionUiState.Error)
  }

  @Test
  fun `load failure moves to Error`() = runTest(dispatcher) {
    val repo = object : AssignItemRepository {
      override suspend fun getLocations(): List<LocationOption> = error("not used")

      override suspend fun getSakhis(locationId: String?): List<SakhiOption> = error("not used")

      override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("boom")

      override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> = error("not used")

      override suspend fun getPrograms(): List<LocationOption> = error("not used")

      override suspend fun getInventoryItems(): List<InventoryItem> = error("not used")

      override suspend fun submitTransaction(submission: TransactionSubmission): TransactionSubmitResult = error("not used")

      override suspend fun updateTransaction(submission: TransactionSubmission): TransactionUpdateResult = error("not used")

      override suspend fun deleteTransaction(sakhiId: String, transactionIds: List<String>): TransactionDeleteResult = error("not used")
    }
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AddItemTransactionUiState.Error)
  }

  // --- Edge cases ---

  @Test
  fun `empty inventory item list still reaches Success with empty items`() = runTest(dispatcher) {
    val viewModel = AddItemTransactionViewModel(TestRepository(items = emptyList()), savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(state.items.isEmpty())
  }

  @Test
  fun `double submit while in flight only calls repository once`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.submitCallCount)
  }

  // --- Edit mode ---

  @Test
  fun `add mode with no edit id calls submitTransaction on submit`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onTypeSelected(TransactionType.CONSUMED)
    viewModel.onDateSelected("10 Oct 2025")
    viewModel.onQuantityChanged("item-1", 20)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.submitCallCount)
    assertEquals(0, repo.updateCallCount)
    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(!state.isEditing)
  }

  @Test
  fun `edit mode pre-fills form from the existing transaction`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(state.isEditing)
    assertEquals(TransactionType.HANDOVER, state.selectedType)
    assertEquals("10 Oct 2025", state.transactionDate)
    assertEquals(15, state.quantities["item-1"])
  }

  @Test
  fun `submitting in edit mode calls updateTransaction not submitTransaction`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onQuantityChanged("item-1", 25)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.updateCallCount)
    assertEquals(0, repo.submitCallCount)
    assertEquals("txn-1", repo.lastUpdatedId)
    assertEquals(25, repo.lastSubmission?.items?.first { it.itemId == "item-1" }?.quantity)
    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertTrue(state.submitted)
  }

  @Test
  fun `edit mode with unknown transaction id moves to Error`() = runTest(dispatcher) {
    val repo = TestRepository(transactions = mapOf("sakhi-1" to emptyList()))
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "unknown-txn"))
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is AddItemTransactionUiState.Error)
  }

  @Test
  fun `edit mode initializes quantities for every item line in the transaction group`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1", "txn-2"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15), TransactionItemEntry("txn-2", "BP Monitor", 4)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(mapOf("item-1" to 15, "item-2" to 4), state.quantities)
  }

  @Test
  fun `edit mode changing one item's quantity leaves the group's other item lines untouched`() =
    runTest(dispatcher) {
      val repo = TestRepository(
        transactions = mapOf(
          "sakhi-1" to listOf(
            TransactionEntry(
              listOf("txn-1", "txn-2"),
              "10 Oct 2025",
              TransactionType.HANDOVER,
              listOf(TransactionItemEntry("txn-1", "Sugar strips", 15), TransactionItemEntry("txn-2", "BP Monitor", 4)),
            ),
          ),
        ),
      )
      val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
      dispatcher.scheduler.advanceUntilIdle()

      viewModel.onQuantityChanged("item-1", 25)

      val state = viewModel.uiState.value as AddItemTransactionUiState.Success
      assertEquals(mapOf("item-1" to 25, "item-2" to 4), state.quantities)
    }

  @Test
  fun `edit mode ignores an attempt to add a brand-new item line not in the group`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    // item-2 was never part of this transaction's item lines, so it's not editable here.
    viewModel.onQuantityChanged("item-2", 3)

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(mapOf("item-1" to 15), state.quantities)
  }

  @Test
  fun `edit mode submission sends every item line in the group, each with its own row id`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1", "txn-2"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15), TransactionItemEntry("txn-2", "BP Monitor", 4)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onQuantityChanged("item-1", 25)
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    val submission = requireNotNull(repo.lastSubmission)
    assertEquals(2, submission.items.size)
    assertEquals("txn-1", submission.items.first { it.itemId == "item-1" }.existingRowId)
    assertEquals("txn-2", submission.items.first { it.itemId == "item-2" }.existingRowId)
    assertEquals(25, submission.items.first { it.itemId == "item-1" }.quantity)
    assertEquals(4, submission.items.first { it.itemId == "item-2" }.quantity)
  }

  @Test
  fun `create mode is unaffected by edit-mode narrowing and still allows multiple items`() = runTest(dispatcher) {
    val repo = TestRepository()
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle())
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onQuantityChanged("item-1", 5)
    viewModel.onQuantityChanged("item-2", 3)

    val state = viewModel.uiState.value as AddItemTransactionUiState.Success
    assertEquals(mapOf("item-1" to 5, "item-2" to 3), state.quantities)
  }

  @Test
  fun `double submit while in flight in edit mode only calls updateTransaction once`() = runTest(dispatcher) {
    val repo = TestRepository(
      transactions = mapOf(
        "sakhi-1" to listOf(
          TransactionEntry(
            listOf("txn-1"),
            "10 Oct 2025",
            TransactionType.HANDOVER,
            listOf(TransactionItemEntry("txn-1", "Sugar strips", 15)),
          ),
        ),
      ),
    )
    val viewModel = AddItemTransactionViewModel(repo, savedStateHandle(editTransactionId = "txn-1"))
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onSubmit()
    viewModel.onSubmit()
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals(1, repo.updateCallCount)
  }
}
