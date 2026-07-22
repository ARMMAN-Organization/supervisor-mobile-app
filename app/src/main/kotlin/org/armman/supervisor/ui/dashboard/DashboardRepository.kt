package org.armman.supervisor.ui.dashboard

import org.armman.supervisor.model.LocationOption

/** Data source for the Supervisor dashboard. Bound to `DashboardRepositoryImpl`. */
interface DashboardRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getDashboard(locationId: String?): DashboardData
}
