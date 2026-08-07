package org.armman.supervisor.ui.beneficiaries

/** Data source for a Sakhi's beneficiary list. Bound to `BeneficiaryListRepositoryImpl`. */
interface BeneficiaryListRepository {
  suspend fun getBeneficiaries(sakhiId: String): SakhiBeneficiaryList
}
