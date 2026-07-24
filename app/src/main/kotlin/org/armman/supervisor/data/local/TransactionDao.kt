package org.armman.supervisor.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update

/** A [TransactionEntity] with its [TransactionItemEntity] rows, as one query result. */
data class TransactionWithItems(
  @Embedded val transaction: TransactionEntity,
  @Relation(parentColumn = "id", entityColumn = "transactionId")
  val items: List<TransactionItemEntity>,
)

@Dao
interface TransactionDao {
  @Transaction
  @Query("SELECT * FROM transactions WHERE sakhiId = :sakhiId ORDER BY rowid ASC")
  suspend fun getBySakhi(sakhiId: String): List<TransactionWithItems>

  @Transaction
  @Query("SELECT * FROM transactions WHERE id = :transactionId LIMIT 1")
  suspend fun getById(transactionId: String): TransactionWithItems?

  @Insert
  suspend fun insertTransaction(entity: TransactionEntity)

  @Update
  suspend fun updateTransaction(entity: TransactionEntity)

  @Insert
  suspend fun insertItems(items: List<TransactionItemEntity>)

  @Query("DELETE FROM transaction_items WHERE transactionId = :transactionId")
  suspend fun deleteItemsForTransaction(transactionId: String)

  @Delete
  suspend fun deleteTransaction(entity: TransactionEntity)

  @Query("SELECT COUNT(*) FROM transactions WHERE sakhiId = :sakhiId")
  suspend fun countForSakhi(sakhiId: String): Int

  @Transaction
  suspend fun insertWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    insertTransaction(entity)
    if (items.isNotEmpty()) insertItems(items)
  }

  @Transaction
  suspend fun replaceWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    val existing = getById(entity.id)?.transaction ?: error("Unknown transaction id: ${entity.id}")
    check(existing.sakhiId == entity.sakhiId) {
      "Transaction ${entity.id} does not belong to sakhi ${entity.sakhiId}"
    }
    updateTransaction(entity)
    deleteItemsForTransaction(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  @Transaction
  suspend fun deleteById(sakhiId: String, transactionId: String) {
    val existing = getById(transactionId)?.transaction ?: error("Unknown transaction id: $transactionId")
    check(existing.sakhiId == sakhiId) { "Transaction $transactionId does not belong to sakhi $sakhiId" }
    deleteTransaction(existing)
  }
}
