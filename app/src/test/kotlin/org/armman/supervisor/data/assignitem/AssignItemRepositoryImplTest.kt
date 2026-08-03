package org.armman.supervisor.data.assignitem

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import org.armman.supervisor.data.local.TransactionWithItems
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.ItemCategory
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.assignitem.TransactionItemQuantity
import org.armman.supervisor.ui.assignitem.TransactionSubmission
import org.armman.supervisor.ui.assignitem.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ProjectsRepository] coverage lives in `ProjectsRepositoryImplTest` — this fake exists only so
 * this file's transaction-persistence tests (its actual purpose) don't depend on a real network
 * call. */
private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> =
    listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = when (projectId) {
    "loc-1" -> listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil"))
    "loc-2" -> listOf(SakhiOption("sakhi-3", "Kavita Sharma"))
    else -> emptyList()
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = when (sakhiId) {
    "sakhi-1" -> SakhiDetail("Sushil", "Unrestricted Armman", "Mumbai")
    "sakhi-2" -> SakhiDetail("Asha Patil", "Unrestricted Armman", "Mumbai")
    "sakhi-3" -> SakhiDetail("Kavita Sharma", "Wardha - Zone A", "Wardha")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = when (sakhiId) {
    "sakhi-1" -> SakhiOption("sakhi-1", "Sushil")
    "sakhi-2" -> SakhiOption("sakhi-2", "Asha Patil")
    "sakhi-3" -> SakhiOption("sakhi-3", "Kavita Sharma")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override fun clearCache() = Unit
}

/**
 * [TransactionDao] has no JVM-testable implementation — Room requires an Android [android.content.Context]
 * even for its in-memory database, and this repo has no Robolectric/instrumented test setup (see
 * `SecureKeyValueStore`'s Keystore-backed implementation for the same, established, pattern:
 * verify real Android-dependent storage manually on-device; keep the collaborator behind an
 * interface so the code that USES it — here, AssignItemRepositoryImpl — stays unit-testable).
 */
private class FakeTransactionDao : TransactionDao {
  private val transactions = mutableMapOf<String, TransactionEntity>()
  private val itemsByTransaction = mutableMapOf<String, MutableList<TransactionItemEntity>>()

  override suspend fun getBySakhi(sakhiId: String): List<TransactionWithItems> =
    transactions.values
      .filter { it.sakhiId == sakhiId }
      .map { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun getById(transactionId: String): TransactionWithItems? =
    transactions[transactionId]?.let { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun insertTransaction(entity: TransactionEntity) {
    check(!transactions.containsKey(entity.id)) { "duplicate id: ${entity.id}" }
    transactions[entity.id] = entity
  }

  override suspend fun updateTransaction(entity: TransactionEntity) {
    check(transactions.containsKey(entity.id)) { "Unknown transaction id: ${entity.id}" }
    transactions[entity.id] = entity
  }

  override suspend fun insertItems(items: List<TransactionItemEntity>) {
    items.forEach { itemsByTransaction.getOrPut(it.transactionId) { mutableListOf() }.add(it) }
  }

  override suspend fun deleteItemsForTransaction(transactionId: String) {
    itemsByTransaction.remove(transactionId)
  }

  override suspend fun deleteTransaction(entity: TransactionEntity) {
    transactions.remove(entity.id)
    itemsByTransaction.remove(entity.id)
  }

  override suspend fun countForSakhi(sakhiId: String): Int = transactions.values.count { it.sakhiId == sakhiId }

  override suspend fun insertWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    insertTransaction(entity)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun replaceWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    val existing = transactions[entity.id] ?: error("Unknown transaction id: ${entity.id}")
    check(existing.sakhiId == entity.sakhiId) {
      "Transaction ${entity.id} does not belong to sakhi ${entity.sakhiId}"
    }
    transactions[entity.id] = entity
    deleteItemsForTransaction(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun deleteById(sakhiId: String, transactionId: String) {
    val existing = transactions[transactionId] ?: error("Unknown transaction id: $transactionId")
    check(existing.sakhiId == sakhiId) { "Transaction $transactionId does not belong to sakhi $sakhiId" }
    deleteTransaction(existing)
  }
}

class AssignItemRepositoryImplTest {
  private val repository = AssignItemRepositoryImpl(FakeTransactionDao(), FakeProjectsRepository())

  @Test
  fun `getTransactions returns a submitted transaction with multiple item rows`() = runTest {
    repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "10 Oct 2025",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 20), TransactionItemQuantity("item-2", 20)),
      ),
    )

    val transactions = repository.getTransactions("sakhi-1")

    assertEquals(1, transactions.size)
    assertEquals(2, transactions.first().items.size)
    assertEquals("Sugar strips", transactions.first().items[0].itemName)
    assertEquals(20, transactions.first().items[0].quantity)
  }

  @Test
  fun `getTransactions falls back to empty for unknown sakhi`() = runTest {
    assertTrue(repository.getTransactions("unknown-id").isEmpty())
  }

  @Test
  fun `getPrograms returns the expected stub list`() = runTest {
    assertTrue(repository.getPrograms().isNotEmpty())
  }

  @Test
  fun `getInventoryItems returns items across both categories`() = runTest {
    val items = repository.getInventoryItems()

    assertTrue(items.any { it.category == ItemCategory.CONSUMABLE })
    assertTrue(items.any { it.category == ItemCategory.INSTRUMENT })
  }

  @Test
  fun `submitTransaction for a sakhi with no prior transactions appends the first entry`() = runTest {
    val entry = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-2",
        projectId = "loc-1",
        transactionType = TransactionType.HANDOVER,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 5)),
      ),
    )

    val transactions = repository.getTransactions("sakhi-2")
    assertEquals(1, transactions.size)
    assertEquals(entry.id, transactions.first().id)
    assertEquals(TransactionType.HANDOVER, transactions.first().transactionType)
    assertEquals("Sugar strips", transactions.first().items.first().itemName)
  }

  @Test
  fun `submitTransaction for a sakhi with existing transactions appends without removing others`() = runTest {
    val beforeCount = repository.getTransactions("sakhi-1").size

    repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.RETURNED,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-2", 3)),
      ),
    )

    val after = repository.getTransactions("sakhi-1")
    assertEquals(beforeCount + 1, after.size)
    val ids = after.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
  }

  @Test
  fun `submitTransaction resolves item names from the inventory master list`() = runTest {
    val entry = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-2",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-5", 1)),
      ),
    )

    assertEquals("Doppler Test Kit", entry.items.first().itemName)
  }

  @Test
  fun `updateTransaction replaces the matching entry in place`() = runTest {
    val original = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "10 Oct 2025",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 20)),
      ),
    )

    val updated = repository.updateTransaction(
      original.id,
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.MISPLACED,
        transactionDate = "23 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-3", 2)),
      ),
    )

    val transactions = repository.getTransactions("sakhi-1")
    assertEquals(1, transactions.size)
    assertEquals(original.id, updated.id)
    assertEquals(TransactionType.MISPLACED, transactions.first().transactionType)
    assertEquals("23 Jul 2026", transactions.first().date)
    assertEquals("Pencil", transactions.first().items.first().itemName)
  }

  @Test(expected = IllegalStateException::class)
  fun `updateTransaction with a transaction id belonging to a different sakhi throws`() = runTest {
    val sakhi1Txn = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "10 Oct 2025",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 20)),
      ),
    )

    // Submission claims sakhi-2, but the transaction id actually belongs to sakhi-1 — must not
    // silently reassign/overwrite another sakhi's transaction.
    repository.updateTransaction(
      sakhi1Txn.id,
      TransactionSubmission(
        sakhiId = "sakhi-2",
        projectId = "loc-1",
        transactionType = TransactionType.MISPLACED,
        transactionDate = "23 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-3", 2)),
      ),
    )
  }

  @Test(expected = IllegalStateException::class)
  fun `updateTransaction with unknown id throws`() = runTest {
    repository.updateTransaction(
      "unknown-txn",
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 1)),
      ),
    )
  }

  @Test
  fun `deleteTransaction removes only the matching entry`() = runTest {
    val seeded = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 1)),
      ),
    )
    val beforeCount = repository.getTransactions("sakhi-1").size

    repository.deleteTransaction("sakhi-1", seeded.id)

    val after = repository.getTransactions("sakhi-1")
    assertEquals(beforeCount - 1, after.size)
    assertTrue(after.none { it.id == seeded.id })
  }

  @Test(expected = IllegalStateException::class)
  fun `deleteTransaction with unknown id throws`() = runTest {
    repository.deleteTransaction("sakhi-1", "unknown-txn")
  }

  @Test(expected = IllegalStateException::class)
  fun `submitTransaction with unknown item id throws`() = runTest {
    repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("unknown-item", 1)),
      ),
    )
  }

  @Test
  fun `transaction survives repository re-creation over the same underlying store`() = runTest {
    val sharedDao = FakeTransactionDao()
    val firstRepository = AssignItemRepositoryImpl(sharedDao, FakeProjectsRepository())

    val entry = firstRepository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-2",
        projectId = "loc-1",
        transactionType = TransactionType.HANDOVER,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 5)),
      ),
    )

    // Simulates the app process dying and restarting: a brand new repository instance, same
    // underlying persisted store — this is the scenario the in-memory-map stub could not survive.
    val secondRepository = AssignItemRepositoryImpl(sharedDao, FakeProjectsRepository())
    val transactions = secondRepository.getTransactions("sakhi-2")

    assertEquals(1, transactions.size)
    assertEquals(entry.id, transactions.first().id)
  }

  @Test
  fun `deleting a transaction leaves no orphaned item rows behind`() = runTest {
    val dao = FakeTransactionDao()
    val repo = AssignItemRepositoryImpl(dao, FakeProjectsRepository())
    val entry = repo.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-2",
        projectId = "loc-1",
        transactionType = TransactionType.HANDOVER,
        transactionDate = "22 Jul 2026",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 5), TransactionItemQuantity("item-2", 2)),
      ),
    )

    repo.deleteTransaction("sakhi-2", entry.id)

    assertTrue(dao.getById(entry.id) == null)
  }

  @Test
  fun `two transactions submitted for the same sakhi on the same date are both retained`() = runTest {
    val submission = TransactionSubmission(
      sakhiId = "sakhi-2",
      projectId = "loc-1",
      transactionType = TransactionType.CONSUMED,
      transactionDate = "22 Jul 2026",
      remarks = null,
      items = listOf(TransactionItemQuantity("item-1", 1)),
    )

    val first = repository.submitTransaction(submission)
    val second = repository.submitTransaction(submission)

    assertTrue(first.id != second.id)
    assertEquals(2, repository.getTransactions("sakhi-2").size)
  }
}
