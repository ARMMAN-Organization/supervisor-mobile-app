package org.armman.supervisor.ui.registrations

import org.armman.supervisor.model.LocationOption

/** Data source for the Registrations detail screen. Bound to `RegistrationsRepositoryImpl`. */
interface RegistrationsRepository {
  suspend fun getLocations(): List<LocationOption>

  suspend fun getRegistrations(locationId: String?): List<SakhiRegistrationSummary>
}
