package org.armman.supervisor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local persisted row for one call log attempt — stands in for the future `call_logs` backend
 * table (see [org.armman.supervisor.ui.callsheet.CallLogEntry]).
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
  @PrimaryKey val id: String,
  val sakhiId: String,
  val timestampEpochMillis: Long,
  val connected: Boolean,
  val successOutcome: String?,
  val failureReason: String?,
  val responder: String?,
  val durationMinutes: Int?,
  val notes: String?,
  val followUpAction: String?,
)
