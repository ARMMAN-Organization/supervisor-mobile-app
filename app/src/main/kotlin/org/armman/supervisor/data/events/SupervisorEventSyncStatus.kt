package org.armman.supervisor.data.events

/** Lifecycle of a locally-queued supervisor-event creation as it moves toward the server. */
enum class SupervisorEventSyncStatus {
  /** Saved on-device, not yet attempted. */
  PENDING,

  /** A sync attempt is currently in flight — advisory only (no DB-level lock); actual
   * concurrency safety comes from [org.armman.supervisor.data.meetingtraining.SupervisorEventSyncExecutor]
   * serializing all its entry points behind an in-process mutex, not from this status value. */
  SYNCING,

  /** Synced successfully. Terminal. */
  SYNCED,

  /** A sync attempt failed. Picked up again alongside PENDING rows until
   * [org.armman.supervisor.data.events.PendingSupervisorEventDao]'s retry cap is reached. */
  FAILED,
}
