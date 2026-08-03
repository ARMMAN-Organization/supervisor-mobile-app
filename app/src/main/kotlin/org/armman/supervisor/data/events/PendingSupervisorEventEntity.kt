package org.armman.supervisor.data.events

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued supervisor-event creation not yet confirmed by the server. [id] is a client-generated
 * id — the SAME id used as [org.armman.supervisor.data.local.SupervisorEventEntity.id] (the rich
 * local-only table backing attendance/marks/photos), so that table can be populated immediately
 * regardless of connectivity and never needs its identity rewritten once this row syncs.
 */
@Entity(tableName = "pending_supervisor_events")
data class PendingSupervisorEventEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val supervisorId: String,
  val eventType: String,
  val eventDate: String,
  val topicsJson: String,
  val remarks: String?,
  val status: String,
  val syncStatus: String,
  val createdAtEpochMillis: Long,
  val lastAttemptAtEpochMillis: Long?,
  val retryCount: Int,
  /** Server-assigned event id once SYNCED — a side reference only; [id] remains the permanent
   * local identity everywhere else in the app. */
  val remoteId: String?,
  val lastErrorMessage: String?,
)
