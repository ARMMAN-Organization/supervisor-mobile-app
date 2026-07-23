package org.armman.supervisor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CallLogDao {
  // rowid DESC breaks ties when two calls land in the same millisecond, so the most recently
  // inserted entry always sorts first instead of depending on wall-clock resolution.
  @Query("SELECT * FROM call_logs WHERE sakhiId = :sakhiId ORDER BY timestampEpochMillis DESC, rowid DESC")
  suspend fun getBySakhi(sakhiId: String): List<CallLogEntity>

  @Query("SELECT * FROM call_logs WHERE sakhiId = :sakhiId ORDER BY timestampEpochMillis DESC, rowid DESC LIMIT 1")
  suspend fun getLatestForSakhi(sakhiId: String): CallLogEntity?

  @Insert
  suspend fun insert(entity: CallLogEntity)
}
