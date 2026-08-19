package org.armman.supervisor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import org.armman.supervisor.data.events.PendingGatheringDao
import org.armman.supervisor.data.events.PendingGatheringEntity
import org.armman.supervisor.data.events.PendingSupervisorEventDao
import org.armman.supervisor.data.events.PendingSupervisorEventEntity
import org.armman.supervisor.data.events.SupervisorEventCacheDao
import org.armman.supervisor.data.events.SupervisorEventCacheEntity
import org.armman.supervisor.data.inventory.InventoryItemCacheDao
import org.armman.supervisor.data.inventory.InventoryItemCacheEntity

/**
 * Local database, doubling as both an offline cache for API-backed reads (inventory items/
 * transactions, supervisor-events) and the sole store for data with no backend endpoint yet
 * (meeting/training attendance, marks, photos). On-device this is opened with a SQLCipher
 * `SupportFactory` keyed by a Keystore-backed passphrase (see `di/DatabaseModule.kt`); nothing
 * here depends on that, so unit tests can open it as a plain, unencrypted Room in-memory database
 * instead.
 */
@Database(
  entities = [
    TransactionEntity::class,
    TransactionItemEntity::class,
    SupervisorEventEntity::class,
    EventAttendanceEntity::class,
    EventPhotoEntity::class,
    EventGatheringEntity::class,
    EventTopicEntity::class,
    EventMarksEntity::class,
    EventMarksCompletionEntity::class,
    InventoryItemCacheEntity::class,
    SupervisorEventCacheEntity::class,
    PendingInventoryTransactionEntity::class,
    PendingInventoryTransactionItemEntity::class,
    PendingSupervisorEventEntity::class,
    PendingGatheringEntity::class,
  ],
  version = 10,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun transactionDao(): TransactionDao
  abstract fun supervisorEventDao(): SupervisorEventDao
  abstract fun inventoryItemCacheDao(): InventoryItemCacheDao
  abstract fun supervisorEventCacheDao(): SupervisorEventCacheDao
  abstract fun pendingInventoryTransactionDao(): PendingInventoryTransactionDao
  abstract fun pendingSupervisorEventDao(): PendingSupervisorEventDao
  abstract fun pendingGatheringDao(): PendingGatheringDao
}
