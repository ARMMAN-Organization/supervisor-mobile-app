package org.armman.supervisor.ui.villagerisksummary

import androidx.annotation.StringRes

/** Risk severity for a beneficiary risk row — drives the risk-type value's text color. */
enum class BeneficiaryRiskLevel {
  HIGH,
  MODERATE,
  MILD,
  LOW,
}

/** One beneficiary's risk detail row on the Village Risk Detail screen. */
data class BeneficiaryRiskDetail(
  val id: String,
  val name: String,
  val registrationType: String,
  val riskDetails: String,
  val riskType: BeneficiaryRiskLevel,
  val visit: String,
  val visitDate: String,
  val referred: Boolean,
)

/** Mother/Child tab selector for the Village Risk Detail screen. */
enum class Tab {
  MOTHER,
  CHILD,
}

/** UI state for the Village Risk Detail screen — covers loading, error and success. */
sealed interface VillageRiskDetailUiState {
  data object Loading : VillageRiskDetailUiState

  /** [exceptionMessage] is shown if present; otherwise the screen falls back to [fallbackMessageRes]. */
  data class Error(
    @StringRes val fallbackMessageRes: Int,
    val exceptionMessage: String?,
  ) : VillageRiskDetailUiState

  data class Success(
    val villageName: String,
    val sakhiName: String,
    val mothers: List<BeneficiaryRiskDetail>,
    val children: List<BeneficiaryRiskDetail>,
    val selectedTab: Tab,
  ) : VillageRiskDetailUiState {
    /** True when the whole village has no beneficiaries in either tab — the screen-level empty state. */
    val isEmpty: Boolean get() = mothers.isEmpty() && children.isEmpty()

    /** Beneficiaries for [selectedTab] — the list the screen currently renders. */
    val visibleBeneficiaries: List<BeneficiaryRiskDetail> get() = when (selectedTab) {
      Tab.MOTHER -> mothers
      Tab.CHILD -> children
    }
  }
}

/** One village's beneficiary risk detail — mothers and children under the enclosing Sakhi. */
data class VillageRiskDetail(
  val villageName: String,
  val mothers: List<BeneficiaryRiskDetail>,
  val children: List<BeneficiaryRiskDetail>,
)
