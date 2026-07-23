package org.armman.supervisor.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local persisted row for one inventory transaction — stands in for the future
 * `inventory_transactions` backend table (see [org.armman.supervisor.ui.assignitem.TransactionEntry]).
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
  @PrimaryKey val id: String,
  val sakhiId: String,
  val date: String,
  val transactionType: String,
)

/** One item + quantity line belonging to a [TransactionEntity]. Deleted when its parent is deleted. */
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
  val itemName: String,
  val quantity: Int,
)
