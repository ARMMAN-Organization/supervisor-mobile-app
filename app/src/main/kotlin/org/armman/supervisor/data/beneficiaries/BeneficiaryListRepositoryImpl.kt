package org.armman.supervisor.data.beneficiaries

import org.armman.supervisor.ui.beneficiaries.BeneficiaryDetail
import org.armman.supervisor.ui.beneficiaries.BeneficiaryListRepository
import org.armman.supervisor.ui.beneficiaries.SakhiBeneficiaryList
import javax.inject.Inject

/**
 * Concrete [BeneficiaryListRepository]. No beneficiary-detail endpoint exists yet (no backend
 * fields for EDD/LMP/BMI/birthdate) — data stays local sample data keyed by sakhiId until one is
 * wired up; the interface and its Hilt binding in `di/BeneficiaryListModule.kt` stay unchanged then.
 */
class BeneficiaryListRepositoryImpl @Inject constructor() : BeneficiaryListRepository {

  override suspend fun getBeneficiaries(sakhiId: String): SakhiBeneficiaryList =
    SEEDED_LISTS[sakhiId] ?: throw NoSuchElementException("No beneficiaries found for sakhiId=$sakhiId")

  private companion object {
    val SEEDED_LISTS: Map<String, SakhiBeneficiaryList> = mapOf(
      "sakhi-komal" to SakhiBeneficiaryList(
        sakhiName = "SakhiKomal",
        projectName = "Test-4",
        address = "Test",
        beneficiaries = listOf(
          BeneficiaryDetail.Child(
            name = "Child 1 T Test",
            registrationDate = "04-08-2026",
            phone = "9898989894",
            birthdate = "04-07-2026",
          ),
          BeneficiaryDetail.Mother(
            name = "Sushma T Test",
            registrationDate = "04-08-2026",
            phone = "9898989893",
            edd = "06-01-2027",
            lmp = "01-04-2026",
            heightCm = 156.0,
            weightKg = 56.0,
          ),
        ),
      ),
      "sakhi-meera" to SakhiBeneficiaryList(
        sakhiName = "SakhiMeera",
        projectName = "Test-1",
        address = "Test",
        beneficiaries = listOf(
          BeneficiaryDetail.Mother(
            name = "Meera Sample Mother",
            registrationDate = "04-08-2026",
            phone = "9898989891",
            edd = "12-02-2027",
            lmp = "07-05-2026",
            heightCm = 160.0,
            weightKg = 60.0,
          ),
        ),
      ),
    )
  }
}
