package org.armman.supervisor.ui.quickresponse

/** Data source for the Quick Response flow. Bound to `QuickResponseRepositoryImpl`. */
interface QuickResponseRepository {
  /** Pending Quick Response cards for the current Supervisor (SRS FR-SV-4.1). */
  suspend fun getRequests(): List<QuickResponseRequest>

  /**
   * Submits the chosen [reason] for [requestId].
   *
   * TODO: no decision endpoint exists yet on `approval-service` (only list + create) and its
   * status enum has no "Restored" value — this is a client-side stub pending backend work; see
   * SRS FR-SV-4.6's own note that the Data Restore flow is unconfirmed with ARMMAN.
   */
  suspend fun submitReason(requestId: String, reason: ReasonOption)
}
