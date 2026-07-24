package org.armman.supervisor.ui.assignitem

import org.armman.supervisor.R
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTypeLabelsTest {
  @Test
  fun `each transaction type maps to its expected string resource`() {
    assertEquals(R.string.transaction_type_handover, TransactionType.HANDOVER.labelRes())
    assertEquals(R.string.transaction_type_returned, TransactionType.RETURNED.labelRes())
    assertEquals(R.string.transaction_type_permanent_damaged, TransactionType.PERMANENT_DAMAGED.labelRes())
    assertEquals(R.string.transaction_type_misplaced, TransactionType.MISPLACED.labelRes())
    assertEquals(R.string.transaction_type_consumed, TransactionType.CONSUMED.labelRes())
  }

  @Test
  fun `each item category maps to its expected string resource`() {
    assertEquals(R.string.item_category_consumables, ItemCategory.CONSUMABLE.labelRes())
    assertEquals(R.string.item_category_instruments, ItemCategory.INSTRUMENT.labelRes())
  }
}
