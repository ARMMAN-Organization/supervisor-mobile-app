package org.armman.supervisor.ui.assignitem

import org.armman.supervisor.data.assignitem.TransactionDeleteResult
import org.armman.supervisor.data.assignitem.TransactionSubmitResult
import org.armman.supervisor.data.assignitem.TransactionUpdateResult
import org.armman.supervisor.model.LocationOption

/** Data source for the Assign Item flow. Bound to `AssignItemRepositoryImpl`. Writes
 * (submit/update/delete) are offline-first: saved locally immediately, then synced now (if
 * online) or queued for background sync (if offline) — never throwing purely because the device
 * is offline. A real server-side rejection while online (validation, auth, etc.) still throws. */
interface AssignItemRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getSakhis(locationId: String?): List<SakhiOption>

  suspend fun getSakhiDetail(sakhiId: String): SakhiDetail

  suspend fun getTransactions(sakhiId: String): List<TransactionEntry>

  /** Programs/projects selectable when recording a transaction (SRS FR-SV-1.1). */
  suspend fun getPrograms(): List<LocationOption>

  /** Active inventory items the supervisor can transact, across all categories. */
  suspend fun getInventoryItems(): List<InventoryItem>

  /**
   * Creates a new transaction for the Sakhi. A submission with multiple items creates one
   * independent server-side row per item (the backend has no multi-item transaction row shape),
   * so a [TransactionSubmitResult.Synced] carries one grouped [TransactionEntry] whose [items]
   * cover every row just created — matching exactly what a subsequent [getTransactions] read
   * will show.
   */
  suspend fun submitTransaction(submission: TransactionSubmission): TransactionSubmitResult

  /**
   * Updates one or more existing rows of a transaction group, one call per row (the backend has
   * no batch-update endpoint). Each of [submission]'s items with a non-null
   * [TransactionItemQuantity.existingRowId] is sent as its own update to that row id; items
   * without one are ignored (adding a brand-new item line to an existing group isn't supported —
   * the backend has no way to add/remove item lines on an existing transaction). Fails if any
   * targeted row doesn't belong to [submission]'s sakhi, so a stale/mismatched id can't overwrite
   * another Sakhi's transaction.
   */
  suspend fun updateTransaction(submission: TransactionSubmission): TransactionUpdateResult

  /** Deletes every row id in [transactionIds] (one call per id — the backend has no batch-delete
   * endpoint). Any id that is still an unsynced pending create is cancelled outright (nothing to
   * ever sync) rather than queuing a pointless delete against a transaction the server has never
   * seen. */
  suspend fun deleteTransaction(sakhiId: String, transactionIds: List<String>): TransactionDeleteResult
}
