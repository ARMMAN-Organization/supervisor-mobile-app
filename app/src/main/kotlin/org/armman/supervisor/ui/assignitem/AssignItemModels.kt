package org.armman.supervisor.ui.assignitem

/** One Sakhi selectable in the Assign Item list. */
data class SakhiOption(val id: String, val name: String)

/** One item line within a [TransactionEntry] (e.g. "Sugar strips", quantity 20). */
data class TransactionItemEntry(val itemName: String, val quantity: Int)

/** One transaction card shown on the Assign Item to Sakhi detail screen. */
data class TransactionEntry(
  val id: String,
  val date: String,
  val transactionType: TransactionType,
  val items: List<TransactionItemEntry>,
)

/** Sakhi identity/context shown at the top of the Assign Item to Sakhi detail screen. */
data class SakhiDetail(val sakhiName: String, val projectName: String, val address: String)
