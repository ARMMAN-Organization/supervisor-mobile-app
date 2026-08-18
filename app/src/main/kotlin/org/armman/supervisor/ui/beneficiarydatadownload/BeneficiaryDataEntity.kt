package org.armman.supervisor.ui.beneficiarydatadownload

import androidx.annotation.StringRes
import org.armman.supervisor.R

/**
 * One beneficiary-data table downloaded by the Download Beneficiary Data screen, in the fixed
 * sequence they download in. [ready] marks the entities
 * [org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataRepository] can actually
 * fetch today (per `docs/BENEFICIARY_DATA_DOWNLOAD_ENDPOINTS.md`); the rest resolve to
 * [org.armman.supervisor.data.beneficiarydatadownload.BeneficiaryDataResult.NotAvailable] until
 * their backend endpoint exists.
 *
 * All 16 entities are `ready = true` — backend has shipped `/arogya-sakhi-roster` and
 * `/registration-targets`, the last two gaps.
 */
enum class BeneficiaryDataEntity(@StringRes val labelRes: Int, val ready: Boolean) {
  AROGYA_SAKHI(R.string.beneficiary_data_download_arogya_sakhi, ready = true),
  REGISTRATION_TARGET(R.string.beneficiary_data_download_registration_target, ready = true),
  BENEFICIARIES_LIST(R.string.beneficiary_data_download_beneficiaries_list, ready = true),
  BENEFICIARY_RISK(R.string.beneficiary_data_download_beneficiary_risk, ready = true),
  BENEFICIARY_VISIT(R.string.beneficiary_data_download_beneficiary_visit, ready = true),
  RISK_MONITORING(R.string.beneficiary_data_download_risk_monitoring, ready = true),
  SAKHI_NOT_UPLOADED_DATA(R.string.beneficiary_data_download_sakhi_not_uploaded_data, ready = true),
  SAKHI_ITEM_TRANSACTION(R.string.beneficiary_data_download_sakhi_item_transaction, ready = true),
  SAKHI_ITEM_TRANSACTION_DETAIL(R.string.beneficiary_data_download_sakhi_item_transaction_detail, ready = true),
  GATHERING_LIST(R.string.beneficiary_data_download_gathering_list, ready = true),
  GATHERING_ATTENDANCE(R.string.beneficiary_data_download_gathering_attendance, ready = true),
  GATHERING_TRAINING_MARKS(R.string.beneficiary_data_download_gathering_training_marks, ready = true),
  GATHERING_IMAGES(R.string.beneficiary_data_download_gathering_images, ready = true),
  CALL_DETAILS(R.string.beneficiary_data_download_call_details, ready = true),
  BENEFICIARY_RISK_REFERRAL_HEADER(R.string.beneficiary_data_download_beneficiary_risk_referral_header, ready = true),
  BENEFICIARY_RISK_REFERRAL_DETAILS(R.string.beneficiary_data_download_beneficiary_risk_referral_details, ready = true),
}
