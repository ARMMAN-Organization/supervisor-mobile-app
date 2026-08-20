package org.armman.supervisor.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** A FAILED row is retried up to this many times (see [PendingSupervisorEventEntity.retryCount])
 * before the background sync stops picking it up — prevents a genuinely-rejected event from being
 * re-attempted forever by the periodic WorkManager job. */
private const val MAX_SYNC_RETRIES = 5

@Dao
interface PendingSupervisorEventDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PendingSupervisorEventEntity)

  @Query("SELECT * FROM pending_supervisor_events WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): PendingSupervisorEventEntity?

  /** Finds the local row for a server event by [PendingSupervisorEventEntity.remoteId] rather than
   * [PendingSupervisorEventEntity.id] — a self-created event keeps its original client-generated
   * [PendingSupervisorEventEntity.id] forever once synced, so matching by [id] alone against a
   * server DTO's id never finds it. */
  @Query("SELECT * FROM pending_supervisor_events WHERE remoteId = :remoteId LIMIT 1")
  suspend fun getByRemoteId(remoteId: String): PendingSupervisorEventEntity?

  @Query("DELETE FROM pending_supervisor_events WHERE id = :id")
  suspend fun deleteById(id: String)

  /** Rows the sync worker should attempt: never-synced or previously-failed (but not yet past
   * [MAX_SYNC_RETRIES] attempts — see [PendingSupervisorEventEntity.retryCount]), oldest first.
   * Rows that exhaust their retries are left in the FAILED state permanently rather than retried
   * forever, so a genuine server-side rejection doesn't spam the API indefinitely. */
  @Query(
    "SELECT * FROM pending_supervisor_events WHERE " +
      "(syncStatus = 'PENDING' OR (syncStatus = 'FAILED' AND retryCount < $MAX_SYNC_RETRIES)) " +
      "ORDER BY createdAtEpochMillis ASC",
  )
  suspend fun getPendingSync(): List<PendingSupervisorEventEntity>

  @Query("SELECT * FROM pending_supervisor_events ORDER BY createdAtEpochMillis DESC")
  suspend fun getAll(): List<PendingSupervisorEventEntity>
}
