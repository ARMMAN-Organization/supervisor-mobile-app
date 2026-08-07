package org.armman.supervisor.ui.beneficiaries

/** One beneficiary under a Sakhi, either a registered mother or her child. */
sealed interface BeneficiaryDetail {
  val name: String
  val registrationDate: String
  val phone: String

  data class Child(
    override val name: String,
    override val registrationDate: String,
    override val phone: String,
    val birthdate: String,
  ) : BeneficiaryDetail

  data class Mother(
    override val name: String,
    override val registrationDate: String,
    override val phone: String,
    val edd: String,
    val lmp: String,
    val heightCm: Double,
    val weightKg: Double,
  ) : BeneficiaryDetail {
    val bmi: Double get() = weightKg / (heightCm / CM_PER_M).let { it * it }

    private companion object {
      const val CM_PER_M = 100.0
    }
  }
}

/** Beneficiary list for one Sakhi: her project/address context plus her beneficiaries. */
data class SakhiBeneficiaryList(
  val sakhiName: String,
  val projectName: String,
  val address: String,
  val beneficiaries: List<BeneficiaryDetail>,
)
