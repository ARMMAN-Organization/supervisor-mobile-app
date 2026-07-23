package org.armman.supervisor.ui.assignitem

import org.armman.supervisor.model.LocationOption

/** Data source for the Assign Item flow. Bound to `AssignItemRepositoryImpl`. */
interface AssignItemRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getSakhis(locationId: String?): List<SakhiOption>

  suspend fun getSakhiDetail(sakhiId: String): SakhiDetail

  suspend fun getTransactions(sakhiId: String): List<TransactionEntry>

  /** Programs/projects selectable when recording a transaction (SRS FR-SV-1.1). */
  suspend fun getPrograms(): List<LocationOption>

  /** Active inventory items the supervisor can transact, across all categories. */
  suspend fun getInventoryItems(): List<InventoryItem>

  /** Creates a new transaction for the Sakhi; returns the created entry (with its assigned id). */
  suspend fun submitTransaction(submission: TransactionSubmission): TransactionEntry

  /** Replaces the transaction identified by [transactionId] with the given [submission]. */
  suspend fun updateTransaction(transactionId: String, submission: TransactionSubmission): TransactionEntry

  suspend fun deleteTransaction(sakhiId: String, transactionId: String)
}
