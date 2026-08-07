package org.armman.supervisor.ui.villagerisksummary

/** Data source for the Village Risk Detail screen. Bound to `StaticVillageRiskDetailRepositoryImpl`. */
interface VillageRiskDetailRepository {
  suspend fun getVillageRiskDetail(villageId: String): VillageRiskDetail
}
