package org.armman.supervisor.ui.callsheet

import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiOption

/** Data source for the Call Sheet flow. Bound to `CallSheetRepositoryImpl`. */
interface CallSheetRepository {
  suspend fun getLocations(): List<LocationOption>

  /** Sakhis under [locationId] with their stats and last-called timestamp (SRS FR-SV-3.1/3.4). */
  suspend fun getSakhiSummaries(locationId: String?): List<SakhiCallSummary>

  suspend fun getSakhiOption(sakhiId: String): SakhiOption

  /** Full call history for the Sakhi, newest first (SRS FR-SV-3.3). */
  suspend fun getCallHistory(sakhiId: String): List<CallLogEntry>

  /** Logs a new call attempt; returns the created entry (with its assigned id). */
  suspend fun logCall(submission: CallLogSubmission): CallLogEntry

  /** Beneficiaries with a due visit for this Sakhi (backs "Visit Due" drill-down). Placeholder
   * data — no backend endpoint exists yet, see [CallSheetRepositoryImpl]. */
  suspend fun getDueVisits(sakhiId: String): List<DueVisitItem>

  /** Beneficiaries whose visit expires within 3 days (backs "Visit 3 Days to expire"). Placeholder. */
  suspend fun getVisitsExpiringSoon(sakhiId: String): List<DueVisitItem>

  /** Beneficiaries with a missed visit (backs "Missed Visit"). Placeholder. */
  suspend fun getMissedVisits(sakhiId: String): List<DueVisitItem>

  /** Backs "Followup Pending": the Sakhi's most recent call if it's CALL_BACK and no reason has
   * been recorded against it yet — real, backend-derived data (see [CallSheetRepositoryImpl]). */
  suspend fun getFollowupPending(sakhiId: String): List<FollowupPendingItem>

  /** Beneficiaries with a pending closure form (backs "Closure Form Pending"). Placeholder. */
  suspend fun getClosurePending(sakhiId: String): List<ClosurePendingItem>

  /** Beneficiaries flagged high-risk ANC or PNC for this Sakhi (backs "High Risk ANC"/"High Risk
   * PNC"). Placeholder. */
  suspend fun getHighRisk(sakhiId: String, type: HighRiskType): List<HighRiskItem>

  /** The recorded reason for this Sakhi's last data sync date, if any (backs "Last Sync Date
   * Reason"). Returns null when no reason has been submitted yet. Placeholder. */
  suspend fun getLastSyncReason(sakhiId: String): SyncReasonItem?

  /** Submits a reason for one of the three [ReasonContext] flows. Placeholder — no backend
   * endpoint exists yet. */
  suspend fun submitReason(submission: ReasonSubmission)
}
