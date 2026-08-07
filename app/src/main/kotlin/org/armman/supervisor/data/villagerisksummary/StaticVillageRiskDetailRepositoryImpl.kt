package org.armman.supervisor.data.villagerisksummary

import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskDetail
import org.armman.supervisor.ui.villagerisksummary.BeneficiaryRiskLevel
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetail
import org.armman.supervisor.ui.villagerisksummary.VillageRiskDetailRepository
import javax.inject.Inject

/**
 * Concrete [VillageRiskDetailRepository]. Per-beneficiary risk detail remains local sample data
 * — no village-risk-detail endpoint exists yet; the interface and its Hilt binding in
 * `di/RiskSummaryModule.kt` stay unchanged when one is wired up. Unknown village ids resolve to
 * an empty [VillageRiskDetail] rather than throwing, since the screen renders its own empty state.
 */
class StaticVillageRiskDetailRepositoryImpl @Inject constructor() : VillageRiskDetailRepository {

  override suspend fun getVillageRiskDetail(villageId: String): VillageRiskDetail =
    SEEDED_DETAILS[villageId] ?: VillageRiskDetail(villageName = villageId, mothers = emptyList(), children = emptyList())

  private companion object {
    val SEEDED_DETAILS: Map<String, VillageRiskDetail> = mapOf(
      "SushilTest" to VillageRiskDetail(
        villageName = "SushilTest",
        mothers = listOf(
          BeneficiaryRiskDetail(
            id = "beneficiary-sushma-t-test",
            name = "Sushma T Test",
            registrationType = "Mother",
            riskDetails = "Hypertension",
            riskType = BeneficiaryRiskLevel.HIGH,
            visit = "ANC2",
            visitDate = "04-08-2026",
            referred = true,
          ),
          BeneficiaryRiskDetail(
            id = "beneficiary-meera-sample-mother",
            name = "Meera Sample Mother",
            registrationType = "Mother",
            riskDetails = "Anemia",
            riskType = BeneficiaryRiskLevel.MODERATE,
            visit = "ANC1",
            visitDate = "12-07-2026",
            referred = false,
          ),
        ),
        children = listOf(
          BeneficiaryRiskDetail(
            id = "beneficiary-child-1-t-test",
            name = "Child 1 T Test",
            registrationType = "Child",
            riskDetails = "Low Birth Weight",
            riskType = BeneficiaryRiskLevel.MILD,
            visit = "PNC1",
            visitDate = "10-08-2026",
            referred = false,
          ),
        ),
      ),
      "SushilTest1" to VillageRiskDetail(
        villageName = "SushilTest1",
        mothers = listOf(
          BeneficiaryRiskDetail(
            id = "beneficiary-asha-sample-mother",
            name = "Asha Sample Mother",
            registrationType = "Mother",
            riskDetails = "Gestational Diabetes",
            riskType = BeneficiaryRiskLevel.LOW,
            visit = "ANC3",
            visitDate = "01-08-2026",
            referred = false,
          ),
        ),
        children = emptyList(),
      ),
    )
  }
}
