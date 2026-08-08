package org.armman.supervisor.data.beneficiaries

import org.armman.supervisor.ui.beneficiaries.BeneficiaryDetail
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val DISPLAY_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private const val CASE_TYPE_MOTHER = "MOTHER"

/** Backend dates arrive as ISO instants (`2027-03-08T00:00:00.000Z`); the UI displays `dd-MM-yyyy`. */
fun formatDisplayDate(isoDate: String?): String {
  if (isoDate.isNullOrBlank()) return ""
  return try {
    LocalDate.parse(isoDate.substring(0, MIN_DATE_LENGTH)).format(DISPLAY_DATE_FORMATTER)
  } catch (e: DateTimeParseException) {
    ""
  }
}

/** `motherCaseDetails` carries height and a pre-computed BMI but no recorded weight — back-derive
 * it from BMI so [BeneficiaryDetail.Mother]'s shape (which computes its own BMI from height +
 * weight) stays unchanged. Returns 0.0, not a crash, when either input is missing/unparseable. */
fun deriveWeightKg(heightCm: String?, bmiAtRegistration: String?): Double {
  val height = heightCm?.toDoubleOrNull() ?: return 0.0
  val bmi = bmiAtRegistration?.toDoubleOrNull() ?: return 0.0
  if (height <= 0.0) return 0.0
  val heightM = height / CM_PER_M
  return bmi * heightM * heightM
}

fun BeneficiaryCaseDto.toBeneficiaryDetail(): BeneficiaryDetail =
  if (caseType == CASE_TYPE_MOTHER) {
    val mother = motherCaseDetails
    val heightCm = mother?.heightCm?.toDoubleOrNull() ?: 0.0
    BeneficiaryDetail.Mother(
      name = pii.fullName,
      registrationDate = formatDisplayDate(registrationDate),
      phone = pii.mobileNumber.orEmpty(),
      edd = formatDisplayDate(mother?.eddDate),
      lmp = formatDisplayDate(mother?.lmpDate),
      heightCm = heightCm,
      weightKg = deriveWeightKg(mother?.heightCm, mother?.bmiAtRegistration),
    )
  } else {
    BeneficiaryDetail.Child(
      name = pii.fullName,
      registrationDate = formatDisplayDate(registrationDate),
      phone = pii.mobileNumber.orEmpty(),
      birthdate = formatDisplayDate(childCaseDetails?.dateOfBirth),
    )
  }

private const val CM_PER_M = 100.0
private const val MIN_DATE_LENGTH = 10
