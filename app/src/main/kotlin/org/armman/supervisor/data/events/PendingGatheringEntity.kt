package org.armman.supervisor.data.events

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A queued Training gathering creation not yet confirmed by the server. [id] is the SAME id used
 * as [org.armman.supervisor.data.local.EventGatheringEntity.id] (the local placeholder,
 * "gathering-&lt;uuid&gt;"), mirroring [PendingSupervisorEventEntity]'s "one client id, forever"
 * design. Queued whenever the gathering couldn't be created server-side immediately — either its
 * parent event hadn't synced yet, or connectivity dropped mid-request.
 */
@Entity(tableName = "pending_gatherings")
data class PendingGatheringEntity(
  @PrimaryKey val id: String,
  val eventId: String,
  val gatheringDate: String,
  /** Comma-free topic names can't collide with this delimiter — training topic names are short
   * catalog labels (e.g. "Antenatal Care Basics"), never free text containing "|". */
  val topicNamesJoined: String,
  val remarks: String,
  val syncStatus: String,
  val createdAtEpochMillis: Long,
  val lastAttemptAtEpochMillis: Long?,
  val retryCount: Int,
  val lastErrorMessage: String?,
)
