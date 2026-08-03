package org.armman.supervisor.data.meetingtraining

import org.armman.supervisor.ui.meetingtraining.MeetingEntry

/** Result of scheduling a meeting/training. [entry] is populated in both success cases — the
 * local event row exists immediately regardless of sync state (see
 * [org.armman.supervisor.data.events.PendingSupervisorEventEntity]). */
sealed interface EventScheduleResult {
  data class Synced(val entry: MeetingEntry) : EventScheduleResult
  data class QueuedOffline(val entry: MeetingEntry) : EventScheduleResult
}
