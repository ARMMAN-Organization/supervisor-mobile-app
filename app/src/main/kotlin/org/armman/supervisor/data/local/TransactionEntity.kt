package org.armman.supervisor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local cache/persisted row for one inventory transaction — mirrors the server
 * `inventory_transactions` row (see [org.armman.supervisor.data.inventory.InventoryTransactionDto]).
 * [id] is the server-issued transaction id once a submission round-trips successfully; there is
 * no offline-write queue, so a row only exists here after a successful API call.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
  @PrimaryKey val id: String,
  val sakhiId: String,
  val projectId: String,
  val supervisorId: String,
  val date: String,
  val transactionType: String,
  val remarks: String?,
  val createdAt: String,
  val updatedAt: String,
)

/** One item + quantity line belonging to a [TransactionEntity]. Deleted when its parent is deleted.
 * [itemName] is denormalized at write time (from the inventory item cache) so display doesn't
 * need a join; [itemId] is kept alongside it for edit flows that need the original item id. */
@Entity(
  tableName = "transaction_items",
  foreignKeys = [
    ForeignKey(
      entity = TransactionEntity::class,
      parentColumns = ["id"],
      childColumns = ["transactionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("transactionId")],
)
data class TransactionItemEntity(
  @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
  val transactionId: String,
  val itemId: String,
  val itemName: String,
  val quantity: Int,
)
