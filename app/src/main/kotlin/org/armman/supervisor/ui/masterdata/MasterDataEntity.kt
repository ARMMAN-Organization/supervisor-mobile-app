package org.armman.supervisor.ui.masterdata

import androidx.annotation.StringRes
import org.armman.supervisor.R

/**
 * One master-data table downloaded by the Download Master Data screen, in the fixed sequence
 * they download in. [ready] marks the entities [org.armman.supervisor.data.masterdata.MasterDataRepository]
 * can actually fetch today; the rest resolve to
 * [org.armman.supervisor.data.masterdata.MasterDataResult.NotAvailable].
 *
 * [INCENTIVE_RATE] is deliberately kept `ready = false`: backend confirmed `/incentive-rates/active`
 * resolves one rate for a required `rateType` — it is not a bulk list/download endpoint, and no
 * such endpoint exists today. See `docs/MASTER_DATA_ENDPOINTS.md` for the confirmed finding.
 */
enum class MasterDataEntity(@StringRes val labelRes: Int, val ready: Boolean) {
  STATE(R.string.master_data_state, ready = true),
  DISTRICT(R.string.master_data_district, ready = true),
  BLOCK(R.string.master_data_block, ready = true),
  PHC(R.string.master_data_phc, ready = true),
  SUB_CENTER(R.string.master_data_sub_center, ready = true),
  VILLAGE(R.string.master_data_village, ready = true),
  VILLAGE_LIST(R.string.master_data_village_list, ready = true),
  PROJECT_GEOGRAPHY(R.string.master_data_project_geography, ready = true),
  FUNDERS(R.string.master_data_funders, ready = true),
  PROJECTS(R.string.master_data_projects, ready = true),
  SAKHI(R.string.master_data_sakhi, ready = true),
  RISK_CATEGORY(R.string.master_data_risk_category, ready = true),
  RISK(R.string.master_data_risk, ready = true),
  RISK_LANGUAGE(R.string.master_data_risk_language, ready = true),
  RISK_PARAMETER(R.string.master_data_risk_parameter, ready = true),
  RISK_TYPE(R.string.master_data_risk_type, ready = true),
  // Confirmed with backend: /incentive-rates was never a bulk list endpoint (it never existed —
  // not a regression). The only real route, GET /incentive-rates/active, resolves ONE rate for a
  // required rateType and returns 404 when none is seeded — there is no "download every incentive
  // rate" endpoint today. Keep this NotAvailable until backend ships a real bulk/list route.
  INCENTIVE_RATE(R.string.master_data_incentive_rate, ready = false),
  VISIT_CATEGORY(R.string.master_data_visit_category, ready = true),
  ITEM_CATEGORY(R.string.master_data_item_category, ready = true),
  UOM_LIST(R.string.master_data_uom_list, ready = true),
  ITEM_MASTER_LIST(R.string.master_data_item_master_list, ready = true),
  VISIT_MASTER(R.string.master_data_visit_master, ready = true),
  TRANSACTION_TYPE(R.string.master_data_transaction_type, ready = true),
  TRAINING_TOPIC_MASTER(R.string.master_data_training_topic_master, ready = true),
  GATHERING_STATUS(R.string.master_data_gathering_status, ready = true),
  GATHERING_TYPES(R.string.master_data_gathering_types, ready = true),
  DDL_ITEM(R.string.master_data_ddl_item, ready = true),
  APPLICATION_PARAMETER(R.string.master_data_application_parameter, ready = true),
}
