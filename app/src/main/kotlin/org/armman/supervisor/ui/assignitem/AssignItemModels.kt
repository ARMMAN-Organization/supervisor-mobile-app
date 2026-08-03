package org.armman.supervisor.ui.assignitem

/** One Sakhi selectable in the Assign Item list. */
data class SakhiOption(val id: String, val name: String)

/** One item line within a [TransactionEntry] (e.g. "Sugar strips", quantity 20). [id] is the
 * server-side row id backing this specific item line, needed to target it for edit/delete. */
data class TransactionItemEntry(val id: String, val itemName: String, val quantity: Int)

/**
 * One transaction card shown on the Assign Item to Sakhi detail screen. The backend creates one
 * independent row per submitted item (no multi-item transaction row shape), so a single "assign
 * item" submission with several items becomes several server rows. This entry groups those rows
 * back into one card: [ids] holds every underlying server row id in the group (same order as
 * [items]), so card-level actions (edit/delete) can operate on all of them together.
 */
data class TransactionEntry(
  val ids: List<String>,
  val date: String,
  val transactionType: TransactionType,
  val items: List<TransactionItemEntry>,
)

/** Sakhi identity/context shown at the top of the Assign Item to Sakhi detail screen. */
data class SakhiDetail(val sakhiName: String, val projectName: String, val address: String)
