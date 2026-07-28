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
}
