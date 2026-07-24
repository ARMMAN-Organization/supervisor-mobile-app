package org.armman.supervisor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local database for data not yet backed by a real API (e.g. inventory transactions — see
 * `AssignItemRepositoryImpl`). On-device this is opened with a SQLCipher `SupportFactory` keyed by
 * a Keystore-backed passphrase (see `di/DatabaseModule.kt`); nothing here depends on that, so unit
 * tests can open it as a plain, unencrypted Room in-memory database instead.
 */
@Database(
  entities = [
    TransactionEntity::class,
    TransactionItemEntity::class,
    SupervisorEventEntity::class,
    EventAttendanceEntity::class,
    EventPhotoEntity::class,
  ],
  version = 2,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun transactionDao(): TransactionDao
  abstract fun supervisorEventDao(): SupervisorEventDao
}
