package org.armman.supervisor.data.assignitem

import org.armman.supervisor.data.inventory.CreateInventoryTransactionRequest
import org.armman.supervisor.data.inventory.InventoryApi
import org.armman.supervisor.data.inventory.InventoryItemCacheDao
import org.armman.supervisor.data.inventory.InventoryTransactionItemRequest
import org.armman.supervisor.data.inventory.UpdateInventoryTransactionRequest
import org.armman.supervisor.data.local.InventoryTransactionOperation
import org.armman.supervisor.data.local.InventoryTransactionSyncStatus
import org.armman.supervisor.data.local.PendingInventoryTransactionDao
import org.armman.supervisor.data.local.PendingInventoryTransactionEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionWithItems
import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of a full [TransactionSyncExecutor.run] batch, for the Worker to map to a WorkManager
 * [androidx.work.ListenableWorker.Result]. */
enum class TransactionSyncOutcome { COMPLETED, RETRYABLE_FAILURE }

/** Outcome of syncing a single pending row — used by the interactive online path. */
sealed interface TransactionSyncItemResult {
  /** [insertedIds] are the local [org.armman.supervisor.data.local.TransactionEntity] ids just
   * written for this sync — empty for operations that don't insert new rows (update/delete). */
  data class Synced(val insertedIds: List<String> = emptyList()) : TransactionSyncItemResult
  data class Failed(val message: String?) : TransactionSyncItemResult
  data class Retryable(val message: String?) : TransactionSyncItemResult
}

/**
 * Drains queued inventory-transaction writes (create/update/delete) to the real API. Mirrors
 * sakhi-mobile-app's `EnrollmentSyncExecutor` control flow: a plain injectable class (no
 * `Context`), so it's JVM-unit-testable without Robolectric; the Worker that wraps it stays a
 * trivial adapter.
 */
@Singleton
class TransactionSyncExecutor @Inject constructor(
  private val pendingDao: PendingInventoryTransactionDao,
  private val transactionDao: TransactionDao,
  private val inventoryItemCacheDao: InventoryItemCacheDao,
  private val api: InventoryApi,
) {

  // Serializes every entry point below within this process: the interactive online path
  // (runOne, called directly from AssignItemRepositoryImpl) and the background WorkManager job
  // (run) both read-then-mark-SYNCING the same pending rows, and SYNCING is advisory-only (no
  // DB-level lock) — without this, a periodic tick racing an interactive submit (or racing
  // syncNow(), which enqueues under a different unique-work name and so isn't deduped against the
  // periodic job by WorkManager itself) could both re-POST the same row.
  private val syncMutex = Mutex()

  /** Processes every PENDING/FAILED row — used by the background [InventoryTransactionSyncWorker]. */
  suspend fun run(): TransactionSyncOutcome = syncMutex.withLock {
    val pending = pendingDao.getPendingSync()
    if (pending.isEmpty()) return@withLock TransactionSyncOutcome.COMPLETED

    var anyRetryableFailure = false
    for (row in pending) {
      when (syncRow(row)) {
        is TransactionSyncItemResult.Synced -> Unit
        is TransactionSyncItemResult.Failed, is TransactionSyncItemResult.Retryable -> anyRetryableFailure = true
      }
    }
    if (anyRetryableFailure) TransactionSyncOutcome.RETRYABLE_FAILURE else TransactionSyncOutcome.COMPLETED
  }

  /** Immediate single-item sync — used by [AssignItemRepositoryImpl]'s interactive online path. */
  suspend fun runOne(id: String): TransactionSyncItemResult = syncMutex.withLock {
    val row = pendingDao.getById(id) ?: return@withLock TransactionSyncItemResult.Failed("Unknown pending transaction: $id")
    if (row.transaction.syncStatus == InventoryTransactionSyncStatus.SYNCED.name) {
      return@withLock TransactionSyncItemResult.Synced(row.transaction.remoteIds?.split(",").orEmpty())
    }
    syncRow(row)
  }

  private suspend fun syncRow(row: PendingInventoryTransactionWithItems): TransactionSyncItemResult {
    val entity = row.transaction
    pendingDao.upsert(entity.copy(syncStatus = InventoryTransactionSyncStatus.SYNCING.name))

    return try {
      when (InventoryTransactionOperation.valueOf(entity.operation)) {
        InventoryTransactionOperation.CREATE -> syncCreate(row)
        InventoryTransactionOperation.UPDATE -> syncUpdate(entity)
        InventoryTransactionOperation.DELETE -> syncDelete(entity)
      }
    } catch (e: IOException) {
      pendingDao.upsert(entity.copy(syncStatus = InventoryTransactionSyncStatus.PENDING.name))
      TransactionSyncItemResult.Retryable(e.message)
    }
  }

  private suspend fun syncCreate(row: PendingInventoryTransactionWithItems): TransactionSyncItemResult {
    val entity = row.transaction
    val request = CreateInventoryTransactionRequest(
      projectId = entity.projectId,
      sakhiId = entity.sakhiId,
      transactionType = entity.transactionType,
      transactionDate = entity.date,
      remarks = entity.remarks,
      items = row.items.map { InventoryTransactionItemRequest(it.itemId, it.quantity) },
    )
    val response = api.createTransaction(request)
    if (!response.isSuccessful) return markFailed(entity, "Failed to submit transaction: HTTP ${response.code()}")
    val body = response.body()
    if (body?.success != true) return markFailed(entity, body?.message ?: "Failed to submit transaction")
    val serverRows = body.data.orEmpty()
    if (serverRows.isEmpty()) return markFailed(entity, "Submit transaction returned no rows")

    val itemsById = inventoryItemCacheDao.getAll().associateBy { it.id }
    serverRows.forEach { serverRow ->
      transactionDao.insertWithItems(
        TransactionEntity(
          id = serverRow.id,
          sakhiId = serverRow.sakhiId,
          projectId = serverRow.projectId,
          supervisorId = serverRow.supervisorId,
          date = serverRow.transactionDate,
          transactionType = serverRow.transactionType,
          remarks = serverRow.remarks,
          createdAt = serverRow.createdAt,
          updatedAt = serverRow.updatedAt,
        ),
        listOf(
          TransactionItemEntity(
            transactionId = serverRow.id,
            itemId = serverRow.itemId,
            itemName = itemsById[serverRow.itemId]?.itemName ?: serverRow.itemId,
            quantity = serverRow.quantity,
          ),
        ),
      )
    }
    pendingDao.upsert(
      entity.copy(
        syncStatus = InventoryTransactionSyncStatus.SYNCED.name,
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        remoteIds = serverRows.joinToString(",") { it.id },
        lastErrorMessage = null,
      ),
    )
    return TransactionSyncItemResult.Synced(serverRows.map { it.id })
  }

  private suspend fun syncUpdate(entity: PendingInventoryTransactionEntity): TransactionSyncItemResult {
    val transactionId = checkNotNull(entity.existingTransactionId) { "UPDATE row ${entity.id} has no existingTransactionId" }
    val request = UpdateInventoryTransactionRequest(
      quantity = entity.updateQuantity,
      transactionDate = entity.date,
      remarks = entity.remarks,
    )
    val response = api.updateTransaction(transactionId, request)
    if (!response.isSuccessful) return markFailed(entity, "Failed to update transaction: HTTP ${response.code()}")
    val body = response.body()
    if (body?.success != true) return markFailed(entity, body?.message ?: "Failed to update transaction")
    val row = body.data ?: return markFailed(entity, "Empty update-transaction data")

    val itemsById = inventoryItemCacheDao.getAll().associateBy { it.id }
    transactionDao.replaceWithItems(
      TransactionEntity(
        id = row.id,
        sakhiId = row.sakhiId,
        projectId = row.projectId,
        supervisorId = row.supervisorId,
        date = row.transactionDate,
        transactionType = row.transactionType,
        remarks = row.remarks,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
      ),
      listOf(
        TransactionItemEntity(
          transactionId = row.id,
          itemId = row.itemId,
          itemName = itemsById[row.itemId]?.itemName ?: row.itemId,
          quantity = row.quantity,
        ),
      ),
    )
    pendingDao.upsert(
      entity.copy(
        syncStatus = InventoryTransactionSyncStatus.SYNCED.name,
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        lastErrorMessage = null,
      ),
    )
    return TransactionSyncItemResult.Synced()
  }

  private suspend fun syncDelete(entity: PendingInventoryTransactionEntity): TransactionSyncItemResult {
    val transactionId = checkNotNull(entity.existingTransactionId) { "DELETE row ${entity.id} has no existingTransactionId" }
    val response = api.deleteTransaction(transactionId)
    if (!response.isSuccessful) return markFailed(entity, "Failed to delete transaction: HTTP ${response.code()}")
    val body = response.body()
    if (body?.success != true || body.data?.deleted != true) {
      return markFailed(entity, body?.message ?: "Server did not confirm deletion")
    }
    transactionDao.deleteById(entity.sakhiId, transactionId)
    pendingDao.upsert(
      entity.copy(
        syncStatus = InventoryTransactionSyncStatus.SYNCED.name,
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        lastErrorMessage = null,
      ),
    )
    return TransactionSyncItemResult.Synced()
  }

  private suspend fun markFailed(
    entity: PendingInventoryTransactionEntity,
    message: String?,
  ): TransactionSyncItemResult.Failed {
    pendingDao.upsert(
      entity.copy(
        syncStatus = InventoryTransactionSyncStatus.FAILED.name,
        lastAttemptAtEpochMillis = Instant.now().toEpochMilli(),
        retryCount = entity.retryCount + 1,
        lastErrorMessage = message,
      ),
    )
    return TransactionSyncItemResult.Failed(message)
  }
}
