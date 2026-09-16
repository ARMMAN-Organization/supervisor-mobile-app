package org.armman.supervisor.ui.villagerisksummary

/** Data source for the Village Risk Detail screen. Bound to `BeneficiaryVillageRiskDetailRepositoryImpl`. */
interface VillageRiskDetailRepository {
  suspend fun getVillageRiskDetail(sakhiId: String, villageName: String): VillageRiskDetail
}
