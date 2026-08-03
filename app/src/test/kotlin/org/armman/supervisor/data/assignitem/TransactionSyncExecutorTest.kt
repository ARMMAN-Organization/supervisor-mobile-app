package org.armman.supervisor.data.assignitem

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.inventory.CreateInventoryTransactionRequest
import org.armman.supervisor.data.inventory.DeleteTransactionEnvelopeDto
import org.armman.supervisor.data.inventory.DeleteTransactionResultDto
import org.armman.supervisor.data.inventory.InventoryApi
import org.armman.supervisor.data.inventory.InventoryItemsEnvelopeDto
import org.armman.supervisor.data.inventory.InventoryTransactionDto
import org.armman.supervisor.data.inventory.InventoryTransactionEnvelopeDto
import org.armman.supervisor.data.inventory.InventoryTransactionsEnvelopeDto
import org.armman.supervisor.data.inventory.UpdateInventoryTransactionRequest
import org.armman.supervisor.data.local.InventoryTransactionOperation
import org.armman.supervisor.data.local.InventoryTransactionSyncStatus
import org.armman.supervisor.data.local.PendingInventoryTransactionDao
import org.armman.supervisor.data.local.PendingInventoryTransactionEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionItemEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionWithItems
import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import org.armman.supervisor.data.local.TransactionWithItems
import org.armman.supervisor.data.inventory.InventoryItemCacheDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** Mirrors [org.armman.supervisor.data.local.PendingInventoryTransactionDao]'s private retry cap. */
private const val MAX_SYNC_RETRIES = 5

private class ExecutorFakePendingDao : PendingInventoryTransactionDao {
  val entities = mutableMapOf<String, PendingInventoryTransactionEntity>()
  private val itemsByPendingId = mutableMapOf<String, MutableList<PendingInventoryTransactionItemEntity>>()

  override suspend fun upsert(entity: PendingInventoryTransactionEntity) {
    entities[entity.id] = entity
  }

  override suspend fun insertItems(items: List<PendingInventoryTransactionItemEntity>) {
    items.forEach { itemsByPendingId.getOrPut(it.pendingTransactionId) { mutableListOf() }.add(it) }
  }

  override suspend fun deleteItems(id: String) {
    itemsByPendingId.remove(id)
  }

  override suspend fun upsertWithItems(entity: PendingInventoryTransactionEntity, items: List<PendingInventoryTransactionItemEntity>) {
    upsert(entity)
    deleteItems(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun getById(id: String): PendingInventoryTransactionWithItems? =
    entities[id]?.let { PendingInventoryTransactionWithItems(it, itemsByPendingId[id].orEmpty()) }

  override suspend fun deleteById(id: String) {
    entities.remove(id)
    itemsByPendingId.remove(id)
  }

  override suspend fun getPendingSync(): List<PendingInventoryTransactionWithItems> =
    entities.values.filter { it.syncStatus == "PENDING" || (it.syncStatus == "FAILED" && it.retryCount < MAX_SYNC_RETRIES) }
      .sortedBy { it.createdAtEpochMillis }
      .map { PendingInventoryTransactionWithItems(it, itemsByPendingId[it.id].orEmpty()) }

  override suspend fun getAll(): List<PendingInventoryTransactionWithItems> =
    entities.values.map { PendingInventoryTransactionWithItems(it, itemsByPendingId[it.id].orEmpty()) }

  override suspend fun findUnsyncedCreate(localId: String): PendingInventoryTransactionEntity? =
    entities[localId]?.takeIf { it.operation == "CREATE" && it.syncStatus != "SYNCED" }
}

private class ExecutorFakeCacheDao : InventoryItemCacheDao {
  override suspend fun getAll() = emptyList<org.armman.supervisor.data.inventory.InventoryItemCacheEntity>()
  override suspend fun deleteAll() = Unit
  override suspend fun insertAll(items: List<org.armman.supervisor.data.inventory.InventoryItemCacheEntity>) = Unit
  override suspend fun replaceAll(items: List<org.armman.supervisor.data.inventory.InventoryItemCacheEntity>) = Unit
}

private class ExecutorFakeTransactionDao : TransactionDao {
  val transactions = mutableMapOf<String, TransactionEntity>()
  private val itemsByTransaction = mutableMapOf<String, MutableList<TransactionItemEntity>>()

  override suspend fun getBySakhi(sakhiId: String) =
    transactions.values.filter { it.sakhiId == sakhiId }.map { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun getById(transactionId: String) =
    transactions[transactionId]?.let { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun insertTransaction(entity: TransactionEntity) {
    transactions[entity.id] = entity
  }

  override suspend fun updateTransaction(entity: TransactionEntity) {
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

  override suspend fun countForSakhi(sakhiId: String) = transactions.values.count { it.sakhiId == sakhiId }

  override suspend fun insertWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    insertTransaction(entity)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun replaceWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    transactions[entity.id] = entity
    deleteItemsForTransaction(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun deleteById(sakhiId: String, transactionId: String) {
    transactions.remove(transactionId)
    itemsByTransaction.remove(transactionId)
  }

  override suspend fun deleteAllForSakhi(sakhiId: String) {
    transactions.values.filter { it.sakhiId == sakhiId }.map { it.id }.forEach {
      transactions.remove(it)
      itemsByTransaction.remove(it)
    }
  }

  override suspend fun replaceForSakhi(sakhiId: String, entities: List<TransactionWithItems>) {
    deleteAllForSakhi(sakhiId)
    entities.forEach { (entity, items) ->
      insertTransaction(entity)
      if (items.isNotEmpty()) insertItems(items)
    }
  }
}

private class ExecutorFakeInventoryApi : InventoryApi {
  var createFailure: (() -> Nothing)? = null
  var createResponse: Response<InventoryTransactionsEnvelopeDto>? = null
  var updateFailure: (() -> Nothing)? = null
  var deleteFailure: (() -> Nothing)? = null
  var createCallCount = 0

  override suspend fun getInventoryItems() = error("not used")

  override suspend fun getTransactionsBySakhi(sakhiId: String) = error("not used")

  override suspend fun createTransaction(request: CreateInventoryTransactionRequest): Response<InventoryTransactionsEnvelopeDto> {
    createCallCount++
    createFailure?.invoke()
    return createResponse ?: Response.success(
      InventoryTransactionsEnvelopeDto(
        success = true,
        message = "OK",
        data = request.items.map {
          InventoryTransactionDto(
            id = "srv-${it.itemId}", projectId = request.projectId, supervisorId = "sup-1", sakhiId = request.sakhiId,
            itemId = it.itemId, transactionType = request.transactionType, quantity = it.quantity,
            transactionDate = request.transactionDate, remarks = request.remarks,
            createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "2026-01-01T00:00:00.000Z",
          )
        },
      ),
    )
  }

  override suspend fun updateTransaction(id: String, request: UpdateInventoryTransactionRequest): Response<InventoryTransactionEnvelopeDto> {
    updateFailure?.invoke()
    return Response.success(
      InventoryTransactionEnvelopeDto(
        success = true,
        message = "OK",
        data = InventoryTransactionDto(
          id = id, projectId = "loc-1", supervisorId = "sup-1", sakhiId = "sakhi-1",
          itemId = "item-1", transactionType = "CONSUMED", quantity = request.quantity ?: 1,
          transactionDate = request.transactionDate ?: "22 Jul 2026", remarks = request.remarks,
          createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "2026-01-01T00:00:00.000Z",
        ),
      ),
    )
  }

  override suspend fun deleteTransaction(id: String): Response<DeleteTransactionEnvelopeDto> {
    deleteFailure?.invoke()
    return Response.success(DeleteTransactionEnvelopeDto(success = true, message = "OK", data = DeleteTransactionResultDto(true)))
  }
}

private fun createRow(id: String = "pending-1") = PendingInventoryTransactionEntity(
  id = id,
  operation = InventoryTransactionOperation.CREATE.name,
  existingTransactionId = null,
  sakhiId = "sakhi-1",
  projectId = "loc-1",
  transactionType = "CONSUMED",
  date = "22 Jul 2026",
  remarks = null,
  updateQuantity = null,
  syncStatus = InventoryTransactionSyncStatus.PENDING.name,
  createdAtEpochMillis = 1L,
  lastAttemptAtEpochMillis = null,
  retryCount = 0,
  remoteIds = null,
  lastErrorMessage = null,
)

class TransactionSyncExecutorTest {
  private val pendingDao = ExecutorFakePendingDao()
  private val transactionDao = ExecutorFakeTransactionDao()
  private val cacheDao = ExecutorFakeCacheDao()
  private val api = ExecutorFakeInventoryApi()
  private val executor = TransactionSyncExecutor(pendingDao, transactionDao, cacheDao, api)

  @Test
  fun `run with no pending rows returns COMPLETED without calling the API`() = runTest {
    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.COMPLETED, outcome)
    assertEquals(0, api.createCallCount)
  }

  @Test
  fun `run syncs a PENDING create row to SYNCED`() = runTest {
    val row = createRow()
    pendingDao.upsertWithItems(row, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = row.id, itemId = "item-1", quantity = 5)))

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.COMPLETED, outcome)
    assertEquals("SYNCED", pendingDao.entities[row.id]?.syncStatus)
    assertTrue(transactionDao.transactions.isNotEmpty())
  }

  @Test
  fun `run marks a row FAILED and bumps retryCount on non-2xx`() = runTest {
    val row = createRow()
    pendingDao.upsertWithItems(row, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = row.id, itemId = "item-1", quantity = 5)))
    api.createResponse = Response.error(500, okhttp3.ResponseBody.create(null, ""))

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.RETRYABLE_FAILURE, outcome)
    assertEquals("FAILED", pendingDao.entities[row.id]?.syncStatus)
    assertEquals(1, pendingDao.entities[row.id]?.retryCount)
  }

  @Test
  fun `run resets a row to PENDING without bumping retryCount on IOException`() = runTest {
    val row = createRow()
    pendingDao.upsertWithItems(row, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = row.id, itemId = "item-1", quantity = 5)))
    api.createFailure = { throw IOException("offline") }

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.RETRYABLE_FAILURE, outcome)
    assertEquals("PENDING", pendingDao.entities[row.id]?.syncStatus)
    assertEquals(0, pendingDao.entities[row.id]?.retryCount)
  }

  @Test
  fun `run processes multiple independent rows in one batch, no early exit`() = runTest {
    val first = createRow("pending-1")
    val second = createRow("pending-2")
    pendingDao.upsertWithItems(first, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = first.id, itemId = "item-1", quantity = 1)))
    pendingDao.upsertWithItems(second, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = second.id, itemId = "item-2", quantity = 2)))

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.COMPLETED, outcome)
    assertEquals("SYNCED", pendingDao.entities["pending-1"]?.syncStatus)
    assertEquals("SYNCED", pendingDao.entities["pending-2"]?.syncStatus)
    assertEquals(2, api.createCallCount)
  }

  @Test
  fun `runOne short-circuits to Synced without calling the API when already SYNCED`() = runTest {
    val row = createRow().copy(syncStatus = InventoryTransactionSyncStatus.SYNCED.name)
    pendingDao.upsertWithItems(row, emptyList())

    val result = executor.runOne(row.id)

    assertEquals(TransactionSyncItemResult.Synced(emptyList()), result)
    assertEquals(0, api.createCallCount)
  }

  @Test
  fun `runOne on an UPDATE row syncs successfully`() = runTest {
    val row = createRow().copy(
      operation = InventoryTransactionOperation.UPDATE.name,
      existingTransactionId = "existing-txn",
      updateQuantity = 5,
    )
    pendingDao.upsert(row)

    val result = executor.runOne(row.id)

    assertEquals(TransactionSyncItemResult.Synced(emptyList()), result)
    assertEquals("SYNCED", pendingDao.entities[row.id]?.syncStatus)
  }

  @Test
  fun `runOne on a DELETE row syncs successfully and removes the cached transaction`() = runTest {
    transactionDao.insertTransaction(
      TransactionEntity(
        id = "existing-txn", sakhiId = "sakhi-1", projectId = "loc-1", supervisorId = "sup-1",
        date = "22 Jul 2026", transactionType = "CONSUMED", remarks = null,
        createdAt = "2026-01-01T00:00:00.000Z", updatedAt = "2026-01-01T00:00:00.000Z",
      ),
    )
    val row = createRow().copy(operation = InventoryTransactionOperation.DELETE.name, existingTransactionId = "existing-txn")
    pendingDao.upsert(row)

    val result = executor.runOne(row.id)

    assertEquals(TransactionSyncItemResult.Synced(emptyList()), result)
    assertTrue(transactionDao.transactions["existing-txn"] == null)
  }

  @Test
  fun `runOne returns Failed for an unknown pending id`() = runTest {
    val result = executor.runOne("unknown") as TransactionSyncItemResult.Failed
    assertTrue(result.message?.contains("Unknown") == true)
  }

  @Test
  fun `run stops retrying a row once it exhausts MAX_SYNC_RETRIES`() = runTest {
    val row = createRow().copy(syncStatus = "FAILED", retryCount = MAX_SYNC_RETRIES)
    pendingDao.upsertWithItems(row, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = row.id, itemId = "item-1", quantity = 5)))
    api.createResponse = Response.error(500, okhttp3.ResponseBody.create(null, ""))

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.COMPLETED, outcome)
    assertEquals(0, api.createCallCount)
    assertEquals(MAX_SYNC_RETRIES, pendingDao.entities[row.id]?.retryCount)
  }

  @Test
  fun `run still retries a FAILED row below the retry cap`() = runTest {
    val row = createRow().copy(syncStatus = "FAILED", retryCount = MAX_SYNC_RETRIES - 1)
    pendingDao.upsertWithItems(row, listOf(PendingInventoryTransactionItemEntity(pendingTransactionId = row.id, itemId = "item-1", quantity = 5)))

    val outcome = executor.run()

    assertEquals(TransactionSyncOutcome.COMPLETED, outcome)
    assertEquals(1, api.createCallCount)
    assertEquals("SYNCED", pendingDao.entities[row.id]?.syncStatus)
  }
}
