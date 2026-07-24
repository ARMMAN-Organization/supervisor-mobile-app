package org.armman.supervisor.ui.assignitem

import androidx.annotation.StringRes
import org.armman.supervisor.R

/** Display-label string resource for a [TransactionType]. Resolved to EN/MR text at display time. */
@StringRes
fun TransactionType.labelRes(): Int = when (this) {
  TransactionType.HANDOVER -> R.string.transaction_type_handover
  TransactionType.RETURNED -> R.string.transaction_type_returned
  TransactionType.PERMANENT_DAMAGED -> R.string.transaction_type_permanent_damaged
  TransactionType.MISPLACED -> R.string.transaction_type_misplaced
  TransactionType.CONSUMED -> R.string.transaction_type_consumed
}

/** Display-label string resource for an [ItemCategory] section header. */
@StringRes
fun ItemCategory.labelRes(): Int = when (this) {
  ItemCategory.CONSUMABLE -> R.string.item_category_consumables
  ItemCategory.INSTRUMENT -> R.string.item_category_instruments
}
