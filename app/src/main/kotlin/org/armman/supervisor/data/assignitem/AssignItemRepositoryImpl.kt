package org.armman.supervisor.data.assignitem

import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import org.armman.supervisor.data.local.TransactionWithItems
import org.armman.supervisor.data.projects.ProjectsRepository
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
 * Concrete [AssignItemRepository]. Projects/Sakhis are delegated to [projectsRepository] — the
 * same source Dashboard uses, so both features stay in sync once that repository gets real data.
 * Today [projectsRepository] itself is still local sample data (auth-service has no
 * projects/Sakhi-roster endpoint yet), same as the inventory item catalog below (no items
 * master-data endpoint exists either — confirmed absent from the API Gateway). Transactions the
 * Supervisor actually creates/edits/deletes are persisted in the local encrypted database
 * ([TransactionDao]) so they survive process death — the app has no real inventory-transactions
 * API yet either. When these are ready, only [getInventoryItems], the transaction methods below,
 * and [ProjectsRepository]'s implementation change to HTTP calls returning/accepting the same
 * models — the interface, its Hilt binding in `di/AssignItemModule.kt`, and every caller
 * (ViewModels, screens) stay unchanged.
 */
class AssignItemRepositoryImpl @Inject constructor(
  private val transactionDao: TransactionDao,
  private val projectsRepository: ProjectsRepository,
) : AssignItemRepository {

  // Inventory master — sample items across both categories (SRS FR-SV-1.2 examples); no items
  // master-data endpoint exists yet.
  private val inventoryItems = listOf(
    InventoryItem("item-1", "Sugar strips", ItemCategory.CONSUMABLE),
    InventoryItem("item-2", "HB strip", ItemCategory.CONSUMABLE),
    InventoryItem("item-3", "Pencil", ItemCategory.CONSUMABLE),
    InventoryItem("item-4", "Cells", ItemCategory.CONSUMABLE),
    InventoryItem("item-5", "Doppler Test Kit", ItemCategory.INSTRUMENT),
    InventoryItem("item-6", "BP Monitor", ItemCategory.INSTRUMENT),
    InventoryItem("item-7", "Weighing Scale", ItemCategory.INSTRUMENT),
  )

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhis(locationId: String?): List<SakhiOption> =
    locationId?.let { projectsRepository.getSakhis(it) }.orEmpty()

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = projectsRepository.getSakhiDetail(sakhiId)

  override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> =
    transactionDao.getBySakhi(sakhiId).map { it.toEntry() }

  override suspend fun getPrograms(): List<LocationOption> = projectsRepository.getProjects()

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
