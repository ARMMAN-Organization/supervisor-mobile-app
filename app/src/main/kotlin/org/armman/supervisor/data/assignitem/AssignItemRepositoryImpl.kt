package org.armman.supervisor.data.assignitem

import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.inventory.InventoryApi
import org.armman.supervisor.data.inventory.InventoryItemCacheDao
import org.armman.supervisor.data.inventory.InventoryItemCacheEntity
import org.armman.supervisor.data.inventory.InventoryItemDto
import org.armman.supervisor.data.inventory.InventoryTransactionDto
import org.armman.supervisor.data.local.InventoryTransactionOperation
import org.armman.supervisor.data.local.InventoryTransactionSyncStatus
import org.armman.supervisor.data.local.PendingInventoryTransactionDao
import org.armman.supervisor.data.local.PendingInventoryTransactionEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionItemEntity
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
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Concrete [AssignItemRepository]. Projects/Sakhis are delegated to [projectsRepository] — the
 * same source Dashboard uses. Inventory items and transactions are backed by the real
 * supervisor-operations-service API ([InventoryApi]), with the local encrypted database used as
 * an offline-read cache/fallback for reads. Writes (submit/update/delete) follow
 * sakhi-mobile-app's offline write-queue pattern: saved locally first (as a
 * [PendingInventoryTransactionEntity]), then either synced immediately via [syncExecutor] when
 * online, or left queued for [syncScheduler]'s background WorkManager job when offline — never
 * throwing purely because the device is offline.
 *
 * The backend has no multi-item transaction row shape — `POST inventory-transactions` creates one
 * independent row per submitted item, each with its own id, and there is no field that durably
 * groups rows from the same submission once persisted. This repository regroups those flat rows
 * back into one [TransactionEntry] per submission by matching
 * (sakhiId, projectId, transactionType, date, createdAt) exactly — reliable because the backend
 * inserts every row of one submission inside a single Postgres transaction, so `createdAt` is
 * identical to the millisecond across the group. This relies on that implicit backend behavior
 * continuing to hold; if the create path ever stops batching inserts in one DB transaction, rows
 * from the same submission could get distinct timestamps and stop grouping.
 */
class AssignItemRepositoryImpl @Inject constructor(
  private val inventoryApi: InventoryApi,
  private val inventoryItemCacheDao: InventoryItemCacheDao,
  private val transactionDao: TransactionDao,
  private val pendingDao: PendingInventoryTransactionDao,
  private val syncScheduler: InventoryTransactionSyncScheduler,
  private val syncExecutor: TransactionSyncExecutor,
  private val projectsRepository: ProjectsRepository,
  private val connectivityChecker: ConnectivityChecker,
) : AssignItemRepository {

  override suspend fun getLocations(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getSakhis(locationId: String?): List<SakhiOption> =
    locationId?.let { projectsRepository.getSakhis(it) }.orEmpty()

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = projectsRepository.getSakhiDetail(sakhiId)

  override suspend fun getPrograms(): List<LocationOption> = projectsRepository.getProjects()

  override suspend fun getInventoryItems(): List<InventoryItem> {
    if (!connectivityChecker.isOnline()) {
      return inventoryItemCacheDao.getAll().map { it.toDomain() }
    }
    return try {
      val response = inventoryApi.getInventoryItems()
      if (!response.isSuccessful) error("Failed to load inventory items: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty inventory items response")
      if (!body.success) error(body.message ?: "Failed to load inventory items")
      val items = body.data.orEmpty()
      inventoryItemCacheDao.replaceAll(items.map { it.toCacheEntity() })
      items.map { it.toDomain() }
    } catch (e: IOException) {
      inventoryItemCacheDao.getAll().map { it.toDomain() }
    }
  }

  // KNOWN LIMITATION (PR #25 review): this only reads transactionDao, the synced cache — a
  // transaction that's still queued in pendingDao (submitted/updated/deleted while offline, or
  // mid-retry) is not merged in here, so it won't appear (or won't disappear, for a queued
  // delete) until the next successful background sync. Accepted for now: a real fix requires
  // showing pending rows in a distinct, non-editable UI state (no server id yet for a queued
  // CREATE), which is a scoped UI change requiring its own plan/approval, not a silent patch here.
  override suspend fun getTransactions(sakhiId: String): List<TransactionEntry> {
    if (!connectivityChecker.isOnline()) {
      return transactionDao.getBySakhi(sakhiId).toGroupedEntries()
    }
    return try {
      val response = inventoryApi.getTransactionsBySakhi(sakhiId)
      if (!response.isSuccessful) error("Failed to load transactions: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty transactions response")
      if (!body.success) error(body.message ?: "Failed to load transactions")
      val itemsById = itemCacheById()
      val withItems = body.data.orEmpty().map { it.toTransactionWithItems(itemsById) }
      transactionDao.replaceForSakhi(sakhiId, withItems)
      withItems.toGroupedEntries()
    } catch (e: IOException) {
      transactionDao.getBySakhi(sakhiId).toGroupedEntries()
    }
  }

  override suspend fun submitTransaction(submission: TransactionSubmission): TransactionSubmitResult {
    val pendingId = "pending-txn-${UUID.randomUUID()}"
    val entity = PendingInventoryTransactionEntity(
      id = pendingId,
      operation = InventoryTransactionOperation.CREATE.name,
      existingTransactionId = null,
      sakhiId = submission.sakhiId,
      projectId = submission.projectId,
      transactionType = submission.transactionType.name,
      date = submission.transactionDate,
      remarks = submission.remarks,
      updateQuantity = null,
      syncStatus = InventoryTransactionSyncStatus.PENDING.name,
      createdAtEpochMillis = Instant.now().toEpochMilli(),
      lastAttemptAtEpochMillis = null,
      retryCount = 0,
      remoteIds = null,
      lastErrorMessage = null,
    )
    val items = submission.items.map { PendingInventoryTransactionItemEntity(pendingTransactionId = pendingId, itemId = it.itemId, quantity = it.quantity) }
    pendingDao.upsertWithItems(entity, items)

    if (!connectivityChecker.isOnline()) {
      syncScheduler.syncNow()
      return TransactionSubmitResult.QueuedOffline
    }
    return when (val result = syncExecutor.runOne(pendingId)) {
      is TransactionSyncItemResult.Synced -> {
        val entry = transactionDao.getBySakhi(submission.sakhiId)
          .filter { it.transaction.id in result.insertedIds }
          .toGroupedEntries()
          .last()
        TransactionSubmitResult.Synced(entry)
      }
      // Real server-side rejection (validation, auth, etc.) reached the API — a genuine error
      // the UI must show, not silently absorbed as "will retry later" (unchanged from before
      // this queue existed; only connectivity failures get queued). The pending row is removed so
      // the background sync never retries a submission the server has already rejected outright.
      is TransactionSyncItemResult.Failed -> {
        pendingDao.deleteById(pendingId)
        error(result.message ?: "Failed to submit transaction")
      }
      is TransactionSyncItemResult.Retryable -> {
        syncScheduler.syncNow()
        TransactionSubmitResult.QueuedOffline
      }
    }
  }

  override suspend fun updateTransaction(submission: TransactionSubmission): TransactionUpdateResult {
    val targets = submission.items.filter { it.existingRowId != null }
    check(targets.isNotEmpty()) { "updateTransaction requires at least one item with an existingRowId" }

    var queuedOffline = false
    val updatedIds = mutableListOf<String>()
    for (item in targets) {
      val transactionId = checkNotNull(item.existingRowId)
      val pendingId = "pending-txn-${UUID.randomUUID()}"
      val entity = PendingInventoryTransactionEntity(
        id = pendingId,
        operation = InventoryTransactionOperation.UPDATE.name,
        existingTransactionId = transactionId,
        sakhiId = submission.sakhiId,
        projectId = submission.projectId,
        transactionType = submission.transactionType.name,
        date = submission.transactionDate,
        remarks = submission.remarks,
        updateQuantity = item.quantity,
        syncStatus = InventoryTransactionSyncStatus.PENDING.name,
        createdAtEpochMillis = Instant.now().toEpochMilli(),
        lastAttemptAtEpochMillis = null,
        retryCount = 0,
        remoteIds = null,
        lastErrorMessage = null,
      )
      pendingDao.upsert(entity)

      if (!connectivityChecker.isOnline()) {
        syncScheduler.syncNow()
        queuedOffline = true
        continue
      }
      when (val result = syncExecutor.runOne(pendingId)) {
        is TransactionSyncItemResult.Synced -> updatedIds += transactionId
        // Real server-side rejection reached the API for this row — surface immediately rather
        // than silently continuing to the next row, since the group is now left partially
        // updated and the supervisor needs to know. Remove the pending row so it's never retried.
        is TransactionSyncItemResult.Failed -> {
          pendingDao.deleteById(pendingId)
          error(result.message ?: "Failed to update transaction")
        }
        is TransactionSyncItemResult.Retryable -> {
          syncScheduler.syncNow()
          queuedOffline = true
        }
      }
    }

    if (queuedOffline) return TransactionUpdateResult.QueuedOffline
    val entry = transactionDao.getBySakhi(submission.sakhiId)
      .filter { it.transaction.id in updatedIds }
      .toGroupedEntries()
      .last()
    return TransactionUpdateResult.Synced(entry)
  }

  override suspend fun deleteTransaction(sakhiId: String, transactionIds: List<String>): TransactionDeleteResult {
    check(transactionIds.isNotEmpty()) { "deleteTransaction requires at least one transaction id" }

    var queuedOffline = false
    for (transactionId in transactionIds) {
      val unsyncedCreate = pendingDao.findUnsyncedCreate(transactionId)
      if (unsyncedCreate != null) {
        // Never reached the server — cancel the queued create outright, no DELETE to sync.
        pendingDao.deleteById(unsyncedCreate.id)
        continue
      }

      val pendingId = "pending-txn-${UUID.randomUUID()}"
      val entity = PendingInventoryTransactionEntity(
        id = pendingId,
        operation = InventoryTransactionOperation.DELETE.name,
        existingTransactionId = transactionId,
        sakhiId = sakhiId,
        projectId = "",
        transactionType = "",
        date = "",
        remarks = null,
        updateQuantity = null,
        syncStatus = InventoryTransactionSyncStatus.PENDING.name,
        createdAtEpochMillis = Instant.now().toEpochMilli(),
        lastAttemptAtEpochMillis = null,
        retryCount = 0,
        remoteIds = null,
        lastErrorMessage = null,
      )
      pendingDao.upsert(entity)

      if (!connectivityChecker.isOnline()) {
        syncScheduler.syncNow()
        queuedOffline = true
        continue
      }
      when (val result = syncExecutor.runOne(pendingId)) {
        is TransactionSyncItemResult.Synced -> Unit
        // Real server-side rejection for this row — surface immediately; the group may now be
        // partially deleted and the supervisor needs to know rather than assume full success.
        // Remove the pending row so it's never retried.
        is TransactionSyncItemResult.Failed -> {
          pendingDao.deleteById(pendingId)
          error(result.message ?: "Failed to delete transaction")
        }
        is TransactionSyncItemResult.Retryable -> {
          syncScheduler.syncNow()
          queuedOffline = true
        }
      }
    }

    return if (queuedOffline) TransactionDeleteResult.QueuedOffline else TransactionDeleteResult.Synced
  }

  private suspend fun itemCacheById(): Map<String, InventoryItemCacheEntity> =
    inventoryItemCacheDao.getAll().associateBy { it.id }

  private fun InventoryItemDto.toDomain(): InventoryItem =
    InventoryItem(id = id, name = itemName, category = ItemCategory.valueOf(itemCategory))

  private fun InventoryItemDto.toCacheEntity(): InventoryItemCacheEntity =
    InventoryItemCacheEntity(
      id = id,
      itemCode = itemCode,
      itemName = itemName,
      itemCategory = itemCategory,
      unit = unit,
      status = status,
    )

  private fun InventoryItemCacheEntity.toDomain(): InventoryItem =
    InventoryItem(id = id, name = itemName, category = ItemCategory.valueOf(itemCategory))

  /** One server transaction row (single item + quantity) as a [TransactionWithItems] with exactly
   * one item line, resolving its item name from the (possibly stale) items cache for display. */
  private fun InventoryTransactionDto.toTransactionWithItems(
    itemsById: Map<String, InventoryItemCacheEntity>,
  ): TransactionWithItems {
    val entity = TransactionEntity(
      id = id,
      sakhiId = sakhiId,
      projectId = projectId,
      supervisorId = supervisorId,
      date = transactionDate,
      transactionType = transactionType,
      remarks = remarks,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
    val item = TransactionItemEntity(
      transactionId = id,
      itemId = itemId,
      itemName = itemsById[itemId]?.itemName ?: itemId,
      quantity = quantity,
    )
    return TransactionWithItems(entity, listOf(item))
  }

  /** Groups flat one-item-per-row [TransactionWithItems] into one [TransactionEntry] per
   * submission — see the class doc comment for why matching on this composite key (including the
   * exact [TransactionEntity.createdAt] millisecond) is safe to rely on. Order is preserved:
   * groups appear in the order their first row was encountered, and rows within a group keep
   * their original relative order. */
  private fun List<TransactionWithItems>.toGroupedEntries(): List<TransactionEntry> =
    groupBy {
      listOf(it.transaction.sakhiId, it.transaction.projectId, it.transaction.transactionType, it.transaction.date, it.transaction.createdAt)
    }.values.map { group ->
      TransactionEntry(
        ids = group.map { it.transaction.id },
        date = group.first().transaction.date,
        transactionType = TransactionType.valueOf(group.first().transaction.transactionType),
        items = group.flatMap { withItems ->
          withItems.items.map { TransactionItemEntry(withItems.transaction.id, it.itemName, it.quantity) }
        },
      )
    }
}
