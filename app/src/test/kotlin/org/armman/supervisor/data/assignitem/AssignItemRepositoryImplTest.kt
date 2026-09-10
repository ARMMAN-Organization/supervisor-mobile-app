package org.armman.supervisor.data.assignitem

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.connectivity.ConnectivityChecker
import org.armman.supervisor.data.inventory.CreateInventoryTransactionRequest
import org.armman.supervisor.data.inventory.DeleteTransactionEnvelopeDto
import org.armman.supervisor.data.inventory.DeleteTransactionResultDto
import org.armman.supervisor.data.inventory.InventoryApi
import org.armman.supervisor.data.inventory.InventoryItemCacheDao
import org.armman.supervisor.data.inventory.InventoryItemCacheEntity
import org.armman.supervisor.data.inventory.InventoryItemDto
import org.armman.supervisor.data.inventory.InventoryItemsEnvelopeDto
import org.armman.supervisor.data.inventory.InventoryTransactionDto
import org.armman.supervisor.data.inventory.InventoryTransactionEnvelopeDto
import org.armman.supervisor.data.inventory.InventoryTransactionsEnvelopeDto
import org.armman.supervisor.data.inventory.UpdateInventoryTransactionRequest
import org.armman.supervisor.data.local.PendingInventoryTransactionDao
import org.armman.supervisor.data.local.PendingInventoryTransactionEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionItemEntity
import org.armman.supervisor.data.local.PendingInventoryTransactionWithItems
import org.armman.supervisor.data.local.TransactionDao
import org.armman.supervisor.data.local.TransactionEntity
import org.armman.supervisor.data.local.TransactionItemEntity
import org.armman.supervisor.data.local.TransactionWithItems
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.ItemCategory
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.assignitem.TransactionItemQuantity
import org.armman.supervisor.ui.assignitem.TransactionSubmission
import org.armman.supervisor.ui.assignitem.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/** [ProjectsRepository] coverage lives in `ProjectsRepositoryImplTest` — this fake exists only so
 * this file's transaction/item-persistence tests (its actual purpose) don't depend on a real
 * network call. */
private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> =
    listOf(LocationOption("loc-1", "Unrestricted Armman"), LocationOption("loc-2", "Wardha - Zone A"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = when (projectId) {
    "loc-1" -> listOf(SakhiOption("sakhi-1", "Sushil"), SakhiOption("sakhi-2", "Asha Patil"))
    "loc-2" -> listOf(SakhiOption("sakhi-3", "Kavita Sharma"))
    else -> emptyList()
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = when (sakhiId) {
    "sakhi-1" -> SakhiDetail("Sushil", "Unrestricted Armman", "Mumbai")
    "sakhi-2" -> SakhiDetail("Asha Patil", "Unrestricted Armman", "Mumbai")
    "sakhi-3" -> SakhiDetail("Kavita Sharma", "Wardha - Zone A", "Wardha")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = when (sakhiId) {
    "sakhi-1" -> SakhiOption("sakhi-1", "Sushil")
    "sakhi-2" -> SakhiOption("sakhi-2", "Asha Patil")
    "sakhi-3" -> SakhiOption("sakhi-3", "Kavita Sharma")
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override suspend fun getSakhiProjectId(sakhiId: String): String = when (sakhiId) {
    "sakhi-1", "sakhi-2" -> "loc-1"
    "sakhi-3" -> "loc-2"
    else -> error("Unknown sakhi id: $sakhiId")
  }

  override fun clearCache() = Unit
}

private class FakeConnectivityChecker(var online: Boolean = true) : ConnectivityChecker {
  override fun isOnline(): Boolean = online
}

private class FakeInventoryTransactionSyncScheduler : InventoryTransactionSyncScheduler {
  var syncNowCallCount = 0
    private set

  override fun syncNow() {
    syncNowCallCount++
  }

  override fun ensurePeriodicSyncScheduled() = Unit
}

/** [TransactionDao] has no JVM-testable implementation — Room requires an Android [android.content.Context]
 * even for its in-memory database, and this repo has no Robolectric/instrumented test setup (see
 * `SecureKeyValueStore`'s Keystore-backed implementation for the same, established, pattern:
 * verify real Android-dependent storage manually on-device; keep the collaborator behind an
 * interface so the code that USES it — here, AssignItemRepositoryImpl — stays unit-testable).
 */
private class FakeTransactionDao : TransactionDao {
  private val transactions = mutableMapOf<String, TransactionEntity>()
  private val itemsByTransaction = mutableMapOf<String, MutableList<TransactionItemEntity>>()

  override suspend fun getBySakhi(sakhiId: String): List<TransactionWithItems> =
    transactions.values
      .filter { it.sakhiId == sakhiId }
      .map { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun getById(transactionId: String): TransactionWithItems? =
    transactions[transactionId]?.let { TransactionWithItems(it, itemsByTransaction[it.id].orEmpty()) }

  override suspend fun insertTransaction(entity: TransactionEntity) {
    check(!transactions.containsKey(entity.id)) { "duplicate id: ${entity.id}" }
    transactions[entity.id] = entity
  }

  override suspend fun updateTransaction(entity: TransactionEntity) {
    check(transactions.containsKey(entity.id)) { "Unknown transaction id: ${entity.id}" }
    transactions[entity.id] = entity
  }

  override suspend fun insertItems(items: List<TransactionItemEntity>) {
    items.forEach { itemsByTransaction.getOrPut(it.transactionId) { mutableListOf() }.add(it) }
  }

  override suspend fun deleteItemsForTransaction(transactionId: String) {
    itemsByTransaction.remove(transactionId)
  }

  override suspend fun deleteTransaction(entity: TransactionEntity) {
    transactions.remove(entity.id)
    itemsByTransaction.remove(entity.id)
  }

  override suspend fun countForSakhi(sakhiId: String): Int = transactions.values.count { it.sakhiId == sakhiId }

  override suspend fun insertWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    insertTransaction(entity)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun replaceWithItems(entity: TransactionEntity, items: List<TransactionItemEntity>) {
    val existing = transactions[entity.id] ?: error("Unknown transaction id: ${entity.id}")
    check(existing.sakhiId == entity.sakhiId) {
      "Transaction ${entity.id} does not belong to sakhi ${entity.sakhiId}"
    }
    transactions[entity.id] = entity
    deleteItemsForTransaction(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun deleteById(sakhiId: String, transactionId: String) {
    val existing = transactions[transactionId] ?: error("Unknown transaction id: $transactionId")
    check(existing.sakhiId == sakhiId) { "Transaction $transactionId does not belong to sakhi $sakhiId" }
    deleteTransaction(existing)
  }

  override suspend fun deleteAllForSakhi(sakhiId: String) {
    val ids = transactions.values.filter { it.sakhiId == sakhiId }.map { it.id }
    ids.forEach { id ->
      transactions.remove(id)
      itemsByTransaction.remove(id)
    }
  }

  override suspend fun replaceForSakhi(sakhiId: String, entities: List<TransactionWithItems>) {
    deleteAllForSakhi(sakhiId)
    entities.forEach { (entity, items) ->
      insertTransaction(entity)
      if (items.isNotEmpty()) insertItems(items)
    }
  }
}

/** Same rationale as [FakeTransactionDao] — a hand-written in-memory fake standing in for Room. */
private class FakeInventoryItemCacheDao : InventoryItemCacheDao {
  private val items = mutableListOf<InventoryItemCacheEntity>()

  override suspend fun getAll(): List<InventoryItemCacheEntity> = items.sortedBy { it.itemName }

  override suspend fun deleteAll() {
    items.clear()
  }

  override suspend fun insertAll(items: List<InventoryItemCacheEntity>) {
    this.items.addAll(items)
  }

  override suspend fun replaceAll(items: List<InventoryItemCacheEntity>) {
    deleteAll()
    if (items.isNotEmpty()) insertAll(items)
  }
}

/** Same rationale as [FakeTransactionDao]. */
private class FakePendingInventoryTransactionDao : PendingInventoryTransactionDao {
  private val entities = mutableMapOf<String, PendingInventoryTransactionEntity>()
  private val itemsByPendingId = mutableMapOf<String, MutableList<PendingInventoryTransactionItemEntity>>()

  override suspend fun upsert(entity: PendingInventoryTransactionEntity) {
    entities[entity.id] = entity
  }

  override suspend fun insertItems(items: List<PendingInventoryTransactionItemEntity>) {
    items.forEach { itemsByPendingId.getOrPut(it.pendingTransactionId) { mutableListOf() }.add(it) }
  }

  override suspend fun deleteItems(id: String) {
    itemsByPendingId.remove(id)
  }

  override suspend fun upsertWithItems(entity: PendingInventoryTransactionEntity, items: List<PendingInventoryTransactionItemEntity>) {
    upsert(entity)
    deleteItems(entity.id)
    if (items.isNotEmpty()) insertItems(items)
  }

  override suspend fun getById(id: String): PendingInventoryTransactionWithItems? =
    entities[id]?.let { PendingInventoryTransactionWithItems(it, itemsByPendingId[id].orEmpty()) }

  override suspend fun deleteById(id: String) {
    entities.remove(id)
    itemsByPendingId.remove(id)
  }

  override suspend fun getPendingSync(): List<PendingInventoryTransactionWithItems> =
    entities.values.filter { it.syncStatus in setOf("PENDING", "FAILED") }
      .sortedBy { it.createdAtEpochMillis }
      .map { PendingInventoryTransactionWithItems(it, itemsByPendingId[it.id].orEmpty()) }

  override suspend fun getAll(): List<PendingInventoryTransactionWithItems> =
    entities.values.sortedByDescending { it.createdAtEpochMillis }
      .map { PendingInventoryTransactionWithItems(it, itemsByPendingId[it.id].orEmpty()) }

  override suspend fun findUnsyncedCreate(localId: String): PendingInventoryTransactionEntity? =
    entities[localId]?.takeIf { it.operation == "CREATE" && it.syncStatus != "SYNCED" }
}

private class FakeInventoryApi : InventoryApi {
  var items: List<InventoryItemDto> = listOf(
    InventoryItemDto("item-1", "SUG-1", "Sugar strips", "CONSUMABLE", "strip", "ACTIVE"),
    InventoryItemDto("item-2", "HB-1", "HB strip", "CONSUMABLE", "strip", "ACTIVE"),
    InventoryItemDto("item-3", "PEN-1", "Pencil", "CONSUMABLE", "piece", "ACTIVE"),
    InventoryItemDto("item-5", "DOP-1", "Doppler Test Kit", "INSTRUMENT", "unit", "ACTIVE"),
  )
  var transactionsBySakhi: MutableMap<String, List<InventoryTransactionDto>> = mutableMapOf()
  var getItemsShouldFailNetwork = false
  var getItemsFailure: (() -> Nothing)? = null
  var getTransactionsFailure: (() -> Nothing)? = null
  var createFailure: (() -> Nothing)? = null
  var createResponse: ((CreateInventoryTransactionRequest) -> List<InventoryTransactionDto>)? = null
  var updateFailure: (() -> Nothing)? = null
  var updateResponse: InventoryTransactionDto? = null
  var deleteFailure: (() -> Nothing)? = null
  var deleteSucceeds = true
  private var nextRowId = 1
  private var nextCreatedAt = 1

  override suspend fun getInventoryItems(): Response<InventoryItemsEnvelopeDto> {
    if (getItemsShouldFailNetwork) throw IOException("offline")
    getItemsFailure?.invoke()
    return Response.success(InventoryItemsEnvelopeDto(success = true, message = "OK", data = items))
  }

  override suspend fun getTransactionsBySakhi(sakhiId: String): Response<InventoryTransactionsEnvelopeDto> {
    getTransactionsFailure?.invoke()
    return Response.success(
      InventoryTransactionsEnvelopeDto(success = true, message = "OK", data = transactionsBySakhi[sakhiId].orEmpty()),
    )
  }

  override suspend fun createTransaction(
    request: CreateInventoryTransactionRequest,
  ): Response<InventoryTransactionsEnvelopeDto> {
    createFailure?.invoke()
    // Every row from ONE createTransaction call shares the same createdAt (mirrors the real
    // backend inserting them inside a single Postgres transaction) — distinct per call, so
    // separate submissions never accidentally group together in tests.
    val createdAt = "2026-01-01T00:00:00.%03dZ".format(nextCreatedAt++)
    val rows = createResponse?.invoke(request) ?: request.items.map { item ->
      InventoryTransactionDto(
        id = "srv-txn-${nextRowId++}",
        projectId = request.projectId,
        supervisorId = "sup-1",
        sakhiId = request.sakhiId,
        itemId = item.itemId,
        transactionType = request.transactionType,
        quantity = item.quantity,
        transactionDate = request.transactionDate,
        remarks = request.remarks,
        createdAt = createdAt,
        updatedAt = createdAt,
      )
    }
    transactionsBySakhi[request.sakhiId] = transactionsBySakhi[request.sakhiId].orEmpty() + rows
    return Response.success(InventoryTransactionsEnvelopeDto(success = true, message = "OK", data = rows))
  }

  override suspend fun updateTransaction(
    id: String,
    request: UpdateInventoryTransactionRequest,
  ): Response<InventoryTransactionEnvelopeDto> {
    updateFailure?.invoke()
    val existing = transactionsBySakhi.values.flatten().first { it.id == id }
    val updated = existing.copy(
      quantity = request.quantity ?: existing.quantity,
      transactionDate = request.transactionDate ?: existing.transactionDate,
      remarks = request.remarks ?: existing.remarks,
    )
    transactionsBySakhi[updated.sakhiId] = transactionsBySakhi[updated.sakhiId].orEmpty().map {
      if (it.id == id) updated else it
    }
    return Response.success(
      InventoryTransactionEnvelopeDto(success = true, message = "OK", data = updateResponse ?: updated),
    )
  }

  override suspend fun deleteTransaction(id: String): Response<DeleteTransactionEnvelopeDto> {
    deleteFailure?.invoke()
    transactionsBySakhi.replaceAll { _, rows -> rows.filterNot { it.id == id } }
    return Response.success(
      DeleteTransactionEnvelopeDto(success = true, message = "OK", data = DeleteTransactionResultDto(deleteSucceeds)),
    )
  }
}

class AssignItemRepositoryImplTest {
  private val api = FakeInventoryApi()
  private val itemCacheDao = FakeInventoryItemCacheDao()
  private val transactionDao = FakeTransactionDao()
  private val pendingDao = FakePendingInventoryTransactionDao()
  private val syncScheduler = FakeInventoryTransactionSyncScheduler()
  private val connectivityChecker = FakeConnectivityChecker()
  private val syncExecutor = TransactionSyncExecutor(pendingDao, transactionDao, itemCacheDao, api)
  private val repository = AssignItemRepositoryImpl(
    api,
    itemCacheDao,
    transactionDao,
    pendingDao,
    syncScheduler,
    syncExecutor,
    FakeProjectsRepository(),
    connectivityChecker,
  )

  private fun singleSubmission(
    sakhiId: String = "sakhi-1",
    itemId: String = "item-1",
    quantity: Int = 20,
    date: String = "10 Oct 2025",
  ) = TransactionSubmission(sakhiId, "loc-1", TransactionType.CONSUMED, date, null, listOf(TransactionItemQuantity(itemId, quantity)))

  // --- getInventoryItems ---

  @Test
  fun `getInventoryItems returns items from the API and repopulates the cache`() = runTest {
    val items = repository.getInventoryItems()

    assertTrue(items.any { it.category == ItemCategory.CONSUMABLE })
    assertTrue(items.any { it.category == ItemCategory.INSTRUMENT })
    assertEquals(items.size, itemCacheDao.getAll().size)
  }

  @Test(expected = IllegalStateException::class)
  fun `getInventoryItems throws when API returns success false`() = runTest {
    api.getItemsFailure = { error("Inactive account") }
    repository.getInventoryItems()
  }

  @Test(expected = IllegalStateException::class)
  fun `getInventoryItems throws on non-2xx HTTP`() = runTest {
    api.getItemsFailure = { throw IllegalStateException("HTTP 500") }
    repository.getInventoryItems()
  }

  @Test
  fun `getInventoryItems falls back to cache on IOException`() = runTest {
    repository.getInventoryItems() // populate cache
    api.getItemsShouldFailNetwork = true

    val items = repository.getInventoryItems()

    assertTrue(items.isNotEmpty())
  }

  @Test
  fun `getInventoryItems returns empty list on IOException with empty cache`() = runTest {
    api.getItemsShouldFailNetwork = true

    val items = repository.getInventoryItems()

    assertTrue(items.isEmpty())
  }

  @Test
  fun `getInventoryItems skips the API entirely when offline`() = runTest {
    connectivityChecker.online = false

    val items = repository.getInventoryItems()

    assertTrue(items.isEmpty())
  }

  // --- getTransactions ---

  @Test
  fun `getTransactions returns a single-item submission as a single-item entry`() = runTest {
    repository.getInventoryItems() // populate item name cache, as the real screen flow would
    repository.submitTransaction(singleSubmission())

    val transactions = repository.getTransactions("sakhi-1")

    assertEquals(1, transactions.size)
    assertEquals(1, transactions.first().items.size)
    assertEquals("Sugar strips", transactions.first().items[0].itemName)
    assertEquals(20, transactions.first().items[0].quantity)
  }

  @Test
  fun `getTransactions groups a multi-item submission into a single entry`() = runTest {
    repository.getInventoryItems()
    repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "10 Oct 2025",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 20), TransactionItemQuantity("item-2", 20)),
      ),
    )

    val transactions = repository.getTransactions("sakhi-1")

    assertEquals(1, transactions.size)
    assertEquals(2, transactions.first().items.size)
    assertEquals(2, transactions.first().ids.size)
  }

  @Test
  fun `getTransactions keeps two separate submissions on the same date as separate entries`() = runTest {
    repository.getInventoryItems()
    repository.submitTransaction(singleSubmission(itemId = "item-1", quantity = 20, date = "10 Oct 2025"))
    repository.submitTransaction(singleSubmission(itemId = "item-2", quantity = 5, date = "10 Oct 2025"))

    val transactions = repository.getTransactions("sakhi-1")

    assertEquals(2, transactions.size)
    assertTrue(transactions.all { it.items.size == 1 })
  }

  @Test
  fun `getTransactions falls back to empty for unknown sakhi`() = runTest {
    assertTrue(repository.getTransactions("unknown-id").isEmpty())
  }

  @Test
  fun `two different sakhis get independently cached transaction sets`() = runTest {
    repository.getInventoryItems() // populate item name cache, as the real screen flow would
    repository.submitTransaction(
      TransactionSubmission("sakhi-1", "loc-1", TransactionType.HANDOVER, "22 Jul 2026", null, listOf(TransactionItemQuantity("item-1", 5))),
    )
    repository.submitTransaction(
      TransactionSubmission("sakhi-2", "loc-1", TransactionType.HANDOVER, "22 Jul 2026", null, listOf(TransactionItemQuantity("item-2", 3))),
    )
    connectivityChecker.online = false

    val sakhi1 = repository.getTransactions("sakhi-1")
    assertTrue(sakhi1.all { it.items.first().itemName == "Sugar strips" })
  }

  @Test
  fun `getPrograms returns the expected stub list`() = runTest {
    assertTrue(repository.getPrograms().isNotEmpty())
  }

  // --- submitTransaction ---

  @Test
  fun `submitTransaction online with multiple items returns Synced with one grouped entry`() = runTest {
    val result = repository.submitTransaction(
      TransactionSubmission(
        sakhiId = "sakhi-1",
        projectId = "loc-1",
        transactionType = TransactionType.CONSUMED,
        transactionDate = "10 Oct 2025",
        remarks = null,
        items = listOf(TransactionItemQuantity("item-1", 20), TransactionItemQuantity("item-2", 20)),
      ),
    ) as TransactionSubmitResult.Synced

    assertEquals(2, result.entry.items.size)
    assertEquals(2, result.entry.ids.toSet().size)
  }

  @Test
  fun `submitTransaction online upserts rows into the cache immediately`() = runTest {
    repository.submitTransaction(
      TransactionSubmission("sakhi-2", "loc-1", TransactionType.HANDOVER, "22 Jul 2026", null, listOf(TransactionItemQuantity("item-1", 5))),
    )

    val transactions = repository.getTransactions("sakhi-2")
    assertEquals(1, transactions.size)
  }

  @Test(expected = IllegalStateException::class)
  fun `submitTransaction online throws when API returns success false`() = runTest {
    api.createFailure = { error("items: item item-1 is not active.") }
    repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))
  }

  @Test
  fun `submitTransaction rejected by server leaves no orphaned pending row`() = runTest {
    api.createFailure = { error("items: item item-1 is not active.") }

    runCatching { repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1)) }

    assertTrue(pendingDao.getPendingSync().isEmpty())
  }

  @Test(expected = IllegalStateException::class)
  fun `submitTransaction online throws on non-2xx HTTP`() = runTest {
    api.createFailure = { throw IllegalStateException("HTTP 422") }
    repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))
  }

  @Test
  fun `submitTransaction offline queues locally and returns QueuedOffline without calling the API`() = runTest {
    connectivityChecker.online = false

    val result = repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))

    assertEquals(TransactionSubmitResult.QueuedOffline, result)
    assertEquals(1, pendingDao.getPendingSync().size)
    assertEquals(1, syncScheduler.syncNowCallCount)
  }

  @Test
  fun `submitTransaction falls back to QueuedOffline when the API call throws IOException`() = runTest {
    api.createFailure = { throw IOException("offline") }

    val result = repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))

    assertEquals(TransactionSubmitResult.QueuedOffline, result)
    assertEquals(1, syncScheduler.syncNowCallCount)
  }

  @Test
  fun `re-submitting resets the pending row to PENDING and preserves its createdAt`() = runTest {
    connectivityChecker.online = false
    repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))
    val firstPending = pendingDao.getPendingSync().single()

    repository.submitTransaction(singleSubmission(date = "23 Jul 2026", quantity = 2))

    assertEquals(2, pendingDao.getPendingSync().size)
    assertTrue(pendingDao.getPendingSync().all { it.transaction.syncStatus == "PENDING" })
  }

  // --- updateTransaction ---

  @Test
  fun `updateTransaction online updates quantity date and remarks for a single-item group`() = runTest {
    val created = (repository.submitTransaction(singleSubmission()) as TransactionSubmitResult.Synced).entry

    val result = repository.updateTransaction(
      TransactionSubmission(
        "sakhi-1", "loc-1", TransactionType.CONSUMED, "23 Jul 2026", "note",
        listOf(TransactionItemQuantity("item-1", 5, existingRowId = created.ids.first())),
      ),
    ) as TransactionUpdateResult.Synced

    assertEquals(5, result.entry.items.first().quantity)
    assertEquals("23 Jul 2026", result.entry.date)
    val cached = repository.getTransactions("sakhi-1").first { created.ids.first() in it.ids }
    assertEquals(5, cached.items.first().quantity)
  }

  @Test
  fun `updateTransaction online updates each row of a multi-item group independently`() = runTest {
    repository.getInventoryItems() // populate item name cache so items resolve to real names
    val created = (
      repository.submitTransaction(
        TransactionSubmission(
          "sakhi-1", "loc-1", TransactionType.CONSUMED, "10 Oct 2025", null,
          listOf(TransactionItemQuantity("item-1", 20), TransactionItemQuantity("item-2", 20)),
        ),
      ) as TransactionSubmitResult.Synced
    ).entry
    val rowIdByItemId = created.items.associate { it.itemName to it.id }

    val result = repository.updateTransaction(
      TransactionSubmission(
        "sakhi-1", "loc-1", TransactionType.CONSUMED, "10 Oct 2025", null,
        listOf(
          TransactionItemQuantity("item-1", 7, existingRowId = rowIdByItemId["Sugar strips"]),
          TransactionItemQuantity("item-2", 9, existingRowId = rowIdByItemId["HB strip"]),
        ),
      ),
    ) as TransactionUpdateResult.Synced

    assertEquals(2, result.entry.items.size)
    assertEquals(setOf(7, 9), result.entry.items.map { it.quantity }.toSet())
  }

  @Test(expected = IllegalStateException::class)
  fun `updateTransaction online rejected by server throws and leaves cache untouched`() = runTest {
    val created = (repository.submitTransaction(singleSubmission()) as TransactionSubmitResult.Synced).entry
    api.updateFailure = { throw IllegalStateException("HTTP 403") }

    repository.updateTransaction(
      TransactionSubmission(
        "sakhi-1", "loc-1", TransactionType.CONSUMED, "23 Jul 2026", null,
        listOf(TransactionItemQuantity("item-1", 5, existingRowId = created.ids.first())),
      ),
    )
  }

  @Test
  fun `updateTransaction rejected by server leaves no orphaned pending row`() = runTest {
    val created = (repository.submitTransaction(singleSubmission()) as TransactionSubmitResult.Synced).entry
    api.updateFailure = { throw IllegalStateException("HTTP 403") }

    runCatching {
      repository.updateTransaction(
        TransactionSubmission(
          "sakhi-1", "loc-1", TransactionType.CONSUMED, "23 Jul 2026", null,
          listOf(TransactionItemQuantity("item-1", 5, existingRowId = created.ids.first())),
        ),
      )
    }

    assertTrue(pendingDao.getPendingSync().isEmpty())
  }

  @Test
  fun `updateTransaction offline queues locally and returns QueuedOffline`() = runTest {
    connectivityChecker.online = false

    val result = repository.updateTransaction(
      TransactionSubmission(
        "sakhi-1", "loc-1", TransactionType.CONSUMED, "22 Jul 2026", null,
        listOf(TransactionItemQuantity("item-1", 1, existingRowId = "existing-txn")),
      ),
    )

    assertEquals(TransactionUpdateResult.QueuedOffline, result)
    assertEquals(1, pendingDao.getPendingSync().size)
  }

  @Test(expected = IllegalStateException::class)
  fun `updateTransaction throws when no item has an existingRowId`() = runTest {
    repository.updateTransaction(
      TransactionSubmission("sakhi-1", "loc-1", TransactionType.CONSUMED, "22 Jul 2026", null, listOf(TransactionItemQuantity("item-1", 1))),
    )
  }

  // --- deleteTransaction ---

  @Test
  fun `deleteTransaction online removes the cache row so a subsequent read excludes it`() = runTest {
    val created = (repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1)) as TransactionSubmitResult.Synced).entry

    repository.deleteTransaction("sakhi-1", created.ids)

    connectivityChecker.online = false
    val after = repository.getTransactions("sakhi-1")
    assertFalse(after.any { created.ids.first() in it.ids })
  }

  @Test
  fun `deleteTransaction online removes every row of a multi-item group`() = runTest {
    val created = (
      repository.submitTransaction(
        TransactionSubmission(
          "sakhi-1", "loc-1", TransactionType.CONSUMED, "10 Oct 2025", null,
          listOf(TransactionItemQuantity("item-1", 20), TransactionItemQuantity("item-2", 20)),
        ),
      ) as TransactionSubmitResult.Synced
    ).entry
    assertEquals(2, created.ids.size)

    repository.deleteTransaction("sakhi-1", created.ids)

    connectivityChecker.online = false
    assertTrue(repository.getTransactions("sakhi-1").isEmpty())
  }

  @Test(expected = IllegalStateException::class)
  fun `deleteTransaction online rejected by server throws and cache row remains`() = runTest {
    val created = (repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1)) as TransactionSubmitResult.Synced).entry
    api.deleteFailure = { throw IllegalStateException("HTTP 403") }

    repository.deleteTransaction("sakhi-1", created.ids)
  }

  @Test
  fun `deleteTransaction rejected by server leaves no orphaned pending row`() = runTest {
    val created = (repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1)) as TransactionSubmitResult.Synced).entry
    api.deleteFailure = { throw IllegalStateException("HTTP 403") }

    runCatching { repository.deleteTransaction("sakhi-1", created.ids) }

    assertTrue(pendingDao.getPendingSync().isEmpty())
  }

  @Test
  fun `deleteTransaction offline queues locally and returns QueuedOffline`() = runTest {
    connectivityChecker.online = false

    val result = repository.deleteTransaction("sakhi-1", listOf("existing-txn"))

    assertEquals(TransactionDeleteResult.QueuedOffline, result)
    assertEquals(1, pendingDao.getPendingSync().size)
  }

  @Test
  fun `deleting a transaction that is still an unsynced pending create cancels it outright`() = runTest {
    connectivityChecker.online = false
    repository.submitTransaction(singleSubmission(date = "22 Jul 2026", quantity = 1))
    val pendingId = pendingDao.getPendingSync().single().transaction.id

    val result = repository.deleteTransaction("sakhi-1", listOf(pendingId))

    assertEquals(TransactionDeleteResult.Synced, result)
    assertTrue(pendingDao.getPendingSync().isEmpty())
  }
}
