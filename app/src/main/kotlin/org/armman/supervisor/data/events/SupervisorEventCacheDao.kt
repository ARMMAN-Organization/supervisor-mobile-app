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
}
