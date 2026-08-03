package org.armman.supervisor.data.local

/** Lifecycle of a locally-queued inventory-transaction write as it moves toward the server. */
enum class InventoryTransactionSyncStatus {
  /** Saved on-device, not yet attempted. */
  PENDING,

  /** A sync attempt is currently in flight — set just before the network call. Advisory only (no
   * DB-level lock); actual concurrency safety comes from [org.armman.supervisor.data.assignitem.TransactionSyncExecutor]
   * serializing all its entry points behind an in-process mutex, not from this status value. */
  SYNCING,

  /** Synced successfully. Terminal. */
  SYNCED,

  /** A sync attempt failed. Not terminal — picked up again alongside PENDING rows. */
  FAILED,
}

/** Which write this pending row represents. */
enum class InventoryTransactionOperation { CREATE, UPDATE, DELETE }
