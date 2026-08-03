package org.armman.supervisor.data.assignitem

import org.armman.supervisor.ui.assignitem.TransactionEntry

/** Result of [org.armman.supervisor.ui.assignitem.AssignItemRepository.submitTransaction]. */
sealed interface TransactionSubmitResult {
  /** Synced immediately — one grouped entry covering every row actually created server-side. */
  data class Synced(val entry: TransactionEntry) : TransactionSubmitResult

  /** Saved locally, queued for background sync (offline, or the API call failed to reach the
   * server). Not a failure from the supervisor's perspective — they can keep working. */
  data object QueuedOffline : TransactionSubmitResult
}

/** Result of [org.armman.supervisor.ui.assignitem.AssignItemRepository.updateTransaction]. */
sealed interface TransactionUpdateResult {
  data class Synced(val entry: TransactionEntry) : TransactionUpdateResult
  data object QueuedOffline : TransactionUpdateResult
}

/** Result of [org.armman.supervisor.ui.assignitem.AssignItemRepository.deleteTransaction]. */
sealed interface TransactionDeleteResult {
  data object Synced : TransactionDeleteResult
  data object QueuedOffline : TransactionDeleteResult
}
