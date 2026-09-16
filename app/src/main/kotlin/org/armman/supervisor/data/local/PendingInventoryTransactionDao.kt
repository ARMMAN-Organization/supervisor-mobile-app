package org.armman.supervisor.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

/** A FAILED row is retried up to this many times (see [PendingInventoryTransactionEntity.retryCount])
 * before the background sync stops picking it up — prevents a genuinely-rejected transaction from
 * being re-attempted forever by the periodic WorkManager job. */
private const val MAX_SYNC_RETRIES = 5

/** A [PendingInventoryTransactionEntity] with its item rows (CREATE only), as one query result. */
data class PendingInventoryTransactionWithItems(
  @Embedded val transaction: PendingInventoryTransactionEntity,
  @Relation(parentColumn = "id", entityColumn = "pendingTransactionId")
  val items: List<PendingInventoryTransactionItemEntity>,
)

@Dao
interface PendingInventoryTransactionDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PendingInventoryTransactionEntity)

  @Insert
  suspend fun insertItems(items: List<PendingInventoryTransactionItemEntity>)

  @Query("DELETE FROM pending_inventory_transaction_items WHERE pendingTransactionId = :id")
  suspend fun deleteItems(id: String)

  @Transaction
  suspend fun upsertWithItems(entity: PendingInventoryTransactionEntity, items: List<PendingInventoryTransactionItemEntity>) {
    upsert(entity)
    deleteItems(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  @Transaction
  @Query("SELECT * FROM pending_inventory_transactions WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): PendingInventoryTransactionWithItems?

  @Query("DELETE FROM pending_inventory_transactions WHERE id = :id")
  suspend fun deleteById(id: String)

  /** Rows the sync worker should attempt: never-synced, previously-failed (but not yet past
   * [MAX_SYNC_RETRIES] attempts — see [PendingInventoryTransactionEntity.retryCount]), or stuck
   * mid-sync on an idempotent operation, oldest first. SYNCING UPDATE/DELETE rows are included
   * because [TransactionSyncExecutor] only ever clears that status once its network call returns —
   * a process death or crash between marking a row SYNCING and that call completing would
   * otherwise orphan it permanently, with no path back to PENDING/FAILED/SYNCED; retrying
   * UPDATE/DELETE is safe since re-sending either is a no-op on the server if it already applied.
   * SYNCING CREATE rows are deliberately excluded: `createTransaction` has no idempotency key (see
   * the "KNOWN LIMITATION" comment on [org.armman.supervisor.data.assignitem.TransactionSyncExecutor.syncCreate]),
   * so auto-retrying a create that may have already reached the server would risk posting a
   * duplicate set of transaction rows — the same class of bug this table exists to prevent. A
   * SYNCING CREATE orphaned by process death stays stuck until the next full sync outcome is
   * known through other means. Rows that exhaust their retries are left in the FAILED state
   * permanently rather than retried forever, so a genuine server-side rejection doesn't spam the
   * API indefinitely. */
  @Transaction
  @Query(
    "SELECT * FROM pending_inventory_transactions WHERE " +
      "(syncStatus = 'PENDING' OR (syncStatus = 'SYNCING' AND operation != 'CREATE') " +
      "OR (syncStatus = 'FAILED' AND retryCount < $MAX_SYNC_RETRIES)) " +
      "ORDER BY createdAtEpochMillis ASC",
  )
  suspend fun getPendingSync(): List<PendingInventoryTransactionWithItems>

  @Transaction
  @Query("SELECT * FROM pending_inventory_transactions ORDER BY createdAtEpochMillis DESC")
  suspend fun getAll(): List<PendingInventoryTransactionWithItems>

  /** True if a CREATE for [existingTransactionId] (server id not yet assigned) is still queued
   * and unsynced — used by delete-before-sync to cancel the pending create instead of queuing a
   * pointless DELETE against a transaction the server has never seen. */
  @Query(
    "SELECT * FROM pending_inventory_transactions WHERE id = :localId " +
      "AND operation = 'CREATE' AND syncStatus != 'SYNCED' LIMIT 1",
  )
  suspend fun findUnsyncedCreate(localId: String): PendingInventoryTransactionEntity?
}
