package org.armman.supervisor.ui.monitoringsummary

import org.armman.supervisor.model.LocationOption

/** Data source for the Monitoring Summary detail screen. Bound to `MonitoringSummaryRepositoryImpl`. */
interface MonitoringSummaryRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getMonitoringSummary(locationId: String?): List<SakhiMonitoringSummary>
}
