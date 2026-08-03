package org.armman.supervisor.ui.assignitem

/** Item category — maps to the `inventory_items.item_category` DB enum. */
enum class ItemCategory { CONSUMABLE, INSTRUMENT }

/** One inventory item selectable in a transaction — maps to the `inventory_items` table. */
data class InventoryItem(val id: String, val name: String, val category: ItemCategory)

/** Inventory transaction type — maps to the `inventory_transactions.transaction_type` DB enum. */
enum class TransactionType { HANDOVER, RETURNED, PERMANENT_DAMAGED, MISPLACED, CONSUMED }

/** One item + quantity line the supervisor is submitting. [existingRowId] is set only when
 * editing a pre-existing item line (identifies which server row to update); null for a brand-new
 * line in a create submission. */
data class TransactionItemQuantity(val itemId: String, val quantity: Int, val existingRowId: String? = null)

/**
 * A pending inventory transaction the supervisor is submitting for a Sakhi. A single submission
 * with multiple items maps to multiple `inventory_transactions` rows (one per item) in the schema.
 */
data class TransactionSubmission(
  val sakhiId: String,
  val projectId: String,
  val transactionType: TransactionType,
  val transactionDate: String,
  val remarks: String?,
  val items: List<TransactionItemQuantity>,
)
