package org.armman.supervisor.ui.risksummary

import org.armman.supervisor.model.LocationOption

/** Data source for the Risk Summary detail screen. Bound to `RiskSummaryRepositoryImpl`. */
interface RiskSummaryRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getRiskSummary(locationId: String?): List<SakhiRiskSummary>
}
