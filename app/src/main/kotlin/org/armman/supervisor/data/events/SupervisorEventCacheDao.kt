package org.armman.supervisor.data.events

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SupervisorEventCacheDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(event: SupervisorEventCacheEntity)

  @Query("SELECT * FROM supervisor_event_cache WHERE id = :id LIMIT 1")
  suspend fun getById(id: String): SupervisorEventCacheEntity?

  /** Every event the backend has confirmed for this supervisor — used to reconcile events whose
   * local [org.armman.supervisor.data.local.SupervisorEventEntity] row no longer exists (e.g.
   * after a schema-migration wipe, reinstall, or a fresh device), so the list/detail screens don't
   * silently lose track of real, server-confirmed events. */
  @Query("SELECT * FROM supervisor_event_cache")
  suspend fun getAll(): List<SupervisorEventCacheEntity>
}
