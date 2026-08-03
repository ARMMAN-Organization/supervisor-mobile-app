package org.armman.supervisor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A queued inventory-transaction write (create/update/delete) not yet confirmed by the server.
 * [id] is a client-generated id, the permanent identity of this queue row — never rewritten.
 * For CREATE, [existingTransactionId] is null; for UPDATE/DELETE it references the transaction
 * being modified (a server-assigned id already present in [TransactionEntity]).
 */
@Entity(tableName = "pending_inventory_transactions")
data class PendingInventoryTransactionEntity(
  @PrimaryKey val id: String,
  val operation: String,
  val existingTransactionId: String?,
  val sakhiId: String,
  val projectId: String,
  val transactionType: String,
  val date: String,
  val remarks: String?,
  /** Only meaningful for UPDATE: the single item's new quantity (edit is quantity/date/remarks
   * only — see AddItemTransactionViewModel's edit-mode narrowing). Null for CREATE (items live in
   * [PendingInventoryTransactionItemEntity]) and DELETE (no payload needed). */
  val updateQuantity: Int?,
  val syncStatus: String,
  val createdAtEpochMillis: Long,
  val lastAttemptAtEpochMillis: Long?,
  val retryCount: Int,
  /** Server-assigned transaction ids once SYNCED — one per submitted item (CREATE only). */
  val remoteIds: String?,
  val lastErrorMessage: String?,
)

/** One item + quantity line belonging to a CREATE [PendingInventoryTransactionEntity]. Deleted
 * when its parent is deleted. Not used for UPDATE (single quantity inlined on the parent) or
 * DELETE (no items). */
@Entity(
  tableName = "pending_inventory_transaction_items",
  foreignKeys = [
    ForeignKey(
      entity = PendingInventoryTransactionEntity::class,
      parentColumns = ["id"],
      childColumns = ["pendingTransactionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("pendingTransactionId")],
)
data class PendingInventoryTransactionItemEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val pendingTransactionId: String,
  val itemId: String,
  val quantity: Int,
)
