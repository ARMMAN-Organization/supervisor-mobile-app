package org.armman.supervisor.ui.visitsummary

import org.armman.supervisor.model.LocationOption

/** Data source for the Visit Summary detail screen. Bound to `VisitSummaryRepositoryImpl`. */
interface VisitSummaryRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getVisitSummary(locationId: String?): List<SakhiVisitSummary>
}
