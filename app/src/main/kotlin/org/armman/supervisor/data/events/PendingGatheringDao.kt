package org.armman.supervisor.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Same retry cap as [PendingSupervisorEventDao] — a genuinely-rejected gathering stops being
 * retried by the background sync rather than spamming the API forever. */
private const val MAX_SYNC_RETRIES = 5

@Dao
interface PendingGatheringDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PendingGatheringEntity)

  @Query("SELECT * FROM pending_gatherings WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): PendingGatheringEntity?

  @Query("DELETE FROM pending_gatherings WHERE id = :id")
  suspend fun deleteById(id: String)

  /** Rows the sync worker should attempt — same PENDING-or-retryable-FAILED shape as
   * [PendingSupervisorEventDao.getPendingSync]. */
  @Query(
    "SELECT * FROM pending_gatherings WHERE " +
      "(syncStatus = 'PENDING' OR (syncStatus = 'FAILED' AND retryCount < $MAX_SYNC_RETRIES)) " +
      "ORDER BY createdAtEpochMillis ASC",
  )
  suspend fun getPendingSync(): List<PendingGatheringEntity>

  @Query("SELECT * FROM pending_gatherings ORDER BY createdAtEpochMillis DESC")
  suspend fun getAll(): List<PendingGatheringEntity>
}
