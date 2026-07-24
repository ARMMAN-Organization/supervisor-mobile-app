package org.armman.supervisor.data.assignitem

import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import org.armman.supervisor.data.local.TransactionWithItems
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.AssignItemRepository
import org.armman.supervisor.ui.assignitem.InventoryItem
import org.armman.supervisor.ui.assignitem.ItemCategory
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.assignitem.TransactionEntry
import org.armman.supervisor.ui.assignitem.TransactionItemEntry
import org.armman.supervisor.ui.assignitem.TransactionSubmission
import org.armman.supervisor.ui.assignitem.TransactionType
import java.util.UUID
import javax.inject.Inject

/**
 * Concrete [AssignItemRepository]. Reference/lookup data (locations, Sakhis, the inventory master
 * list) is local sample data — it stands in for the future read-only GET endpoints and carries no
 * risk of data loss. Transactions the Supervisor actually creates/edits/deletes are persisted in
 * the local encrypted database ([TransactionDao]) so they survive process death, not just an
 * in-memory map — the app has no real inventory-transactions API yet. When that API is ready, only
 * the transaction methods below change to HTTP calls returning/accepting the same models — the
 * interface, its Hilt binding in `di/AssignItemModule.kt`, and every caller (ViewModels, screens)
 * stay unchanged.
 */
class AssignItemRepositoryImpl @Inject constructor(
  private val transactionDao: TransactionDao,
) : AssignItemRepository {

  private val locations = listOf(
    LocationOption("loc-1", "Unrestricted Armman"),
    LocationOption("loc-2", "Wardha - Zone A"),
  )

  private val sakhisByLocation = mapOf(
    "loc-1" to listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil")),
    "loc-2" to listOf(SakhiOption("sakhi-3", "Kavita Sharma")),
  )

  private val sakhiDetails = mapOf(
    "sakhi-1" to SakhiDetail(sakhiName = "Sushil", projectName = "Unrestricted Armman", address = "Mumbai"),
    "sakhi-2" to SakhiDetail(sakhiName = "Asha Patil", projectName = "Unrestricted Armman", address = "Mumbai"),
    "sakhi-3" to SakhiDetail(sakhiName = "Kavita Sharma", projectName = "Wardha - Zone A", address = "Wardha"),
  )

  // Inventory master — sample items across both categories (SRS FR-SV-1.2 examples).
  private val inventoryItems = listOf(
    InventoryItem("item-1", "Sugar strips", ItemCategory.CONSUMABLE),
    InventoryItem("item-2", "HB strip", ItemCategory.CONSUMABLE),
    InventoryItem("item-3", "Pencil", ItemCategory.CONSUMABLE),
    InventoryItem("item-4", "Cells", ItemCategory.CONSUMABLE),
    InventoryItem("item-5", "Doppler Test Kit", ItemCategory.INSTRUMENT),
    InventoryItem("item-6", "BP Monitor", ItemCategory.INSTRUMENT),
    InventoryItem("item-7", "Weighing Scale", ItemCategory.INSTRUMENT),
  )

  override suspend fun getLocations(): List<LocationOption> = locations

  override suspend fun getSakhis(locationId: String?): List<SakhiOption> = sakhisByLocation[locationId].orEmpty()

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail =
    sakhiDetails[sakhiId] ?: error("Unknown sakhi id: $sakhiId")

  override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> =
    transactionDao.getBySakhi(sakhiId).map { it.toEntry() }

  override suspend fun getPrograms(): List<LocationOption> = locations

  override suspend fun getInventoryItems(): List<InventoryItem> = inventoryItems

  override suspend fun submitTransaction(submission: TransactionSubmission): TransactionEntry {
    val id = "txn-${UUID.randomUUID()}"
    val (entity, items) = submission.toEntities(id)
    transactionDao.insertWithItems(entity, items)
    return submission.toEntry(id)
  }

  override suspend fun updateTransaction(transactionId: String, submission: TransactionSubmission): TransactionEntry {
    val (entity, items) = submission.toEntities(transactionId)
    transactionDao.replaceWithItems(entity, items)
    return submission.toEntry(transactionId)
  }

  override suspend fun deleteTransaction(sakhiId: String, transactionId: String) {
    transactionDao.deleteById(sakhiId, transactionId)
  }

  private fun TransactionWithItems.toEntry(): TransactionEntry = TransactionEntry(
    id = transaction.id,
    date = transaction.date,
    transactionType = TransactionType.valueOf(transaction.transactionType),
    items = items.map { TransactionItemEntry(it.itemName, it.quantity) },
  )

  private fun TransactionSubmission.toEntities(id: String): Pair<TransactionEntity, List<TransactionItemEntity>> {
    val itemsById = inventoryItems.associateBy { it.id }
    val entity = TransactionEntity(
      id = id,
      sakhiId = sakhiId,
      date = transactionDate,
      transactionType = transactionType.name,
    )
    val itemEntities = items.map { (itemId, quantity) ->
      val item = itemsById[itemId] ?: error("Unknown item id: $itemId")
      TransactionItemEntity(transactionId = id, itemName = item.name, quantity = quantity)
    }
    return entity to itemEntities
  }

  private fun TransactionSubmission.toEntry(id: String): TransactionEntry {
    val itemsById = inventoryItems.associateBy { it.id }
    return TransactionEntry(
      id = id,
      date = transactionDate,
      transactionType = transactionType,
      items = items.map { (itemId, quantity) ->
        val item = itemsById[itemId] ?: error("Unknown item id: $itemId")
        TransactionItemEntry(item.name, quantity)
      },
    )
  }
}
