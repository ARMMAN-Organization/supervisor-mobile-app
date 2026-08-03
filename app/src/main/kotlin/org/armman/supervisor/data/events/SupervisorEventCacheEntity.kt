package org.armman.supervisor.data.events

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache row mirroring the server `supervisor_events` shape — deliberately separate from
 * `data.local.SupervisorEventEntity`, which encodes Room-only business logic (date ranges,
 * gatherings, attendance, marks, photo-required-to-complete) that has no server equivalent.
 * Populated only for events created via [SupervisorEventsApi.createEvent] from this device — this
 * app does not attempt to reconcile events created elsewhere into the local-Room-backed
 * list/detail screens (see plan §4 Option A). */
@Entity(tableName = "supervisor_event_cache")
data class SupervisorEventCacheEntity(
  @PrimaryKey val id: String,
  val projectId: String,
  val supervisorId: String,
  val eventType: String,
  val eventDate: String,
  val topicsJson: String,
  val remarks: String?,
  val status: String,
  val photoMediaId: String?,
  val createdAt: String,
  val updatedAt: String,
)
