# MIS Dashboard — ClickHouse Field/Table Requirements

Source: Arogya_Sakhi_SRS_v3.0.md §3C.4.1 (12 form linelists), cross-checked against
Arogya_Sakhi_Database_Design_ERD_Table_Definitions (Postgres OLTP).

Decision: no new OLTP tables for MIS. Clinical visit-form fields (ANC/PP/Delivery/NN/INC)
are captured only via `form_submissions.form_data_json` + `form_answers.field_code` (EAV) —
this is the ERD's own intended design for exactly this situation. These fields are extracted
into ClickHouse by the Airflow ETL job (pivoting `form_answers` rows keyed by `field_code`
into flat columns), not by adding dedicated Postgres tables/columns per visit type.

Legend:
- EXISTS — field already exists in Postgres ERD with this exact name; map as-is to ClickHouse
- RENAME — field exists in Postgres but under a different name; alias in ETL / use Postgres name in ClickHouse
- EXTRACT (ETL) — field is captured in `form_answers.field_code` / `form_submissions.form_data_json` only;
  no dedicated Postgres column. ETL must pivot it out by field_code, keyed to the form's Revised App Form
  Final schema — no new table/column needed.
- ADD — field does not exist anywhere in Postgres and is NOT a form-answer (e.g. derived/computed, or
  belongs on a non-form table like `referrals`/`referral_followups`/`reopen_requests`); needs a real new
  column there before it can be loaded into ClickHouse

---

## 1. Registration Form — Pregnant Woman
Source table(s): `beneficiary_cases`, `mother_case_details`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| beneficiary_id | beneficiary_cases.beneficiary_id | EXISTS |
| registration_date | beneficiary_cases.registration_date | EXISTS |
| reg_fy | registration_date (derived, ETL) | EXTRACT (ETL, derived) |
| lmp_date | mother_case_details.lmp_date | EXISTS |
| edd_date | mother_case_details.edd_date | EXISTS |
| gestational_age_at_reg | mother_case_details (derived from lmp_date/registration_date) or form_answers | EXTRACT (ETL, derived) |
| high_risk_at_reg | mother_case_details.baseline_risk_flag | RENAME |
| permanent_conditions | form_answers (registration form, field_code TBD) | EXTRACT (ETL) |

## 2. Registration Form — Child
Source table(s): `beneficiary_cases`, `child_case_details`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| beneficiary_id | beneficiary_cases.beneficiary_id | EXISTS |
| mother_id | beneficiary_cases.mother_beneficiary_id | RENAME |
| linked_anc_case | child_case_details.linked_anc_case | EXISTS |
| date_of_birth | child_case_details.date_of_birth | EXISTS |
| age_in_months | date_of_birth (derived, ETL — do not store) | EXTRACT (ETL, derived) |
| sex | child_case_details.sex | EXISTS |
| birth_weight | child_case_details.birth_weight_kg | RENAME |
| premature_status | child_case_details.premature_flag | RENAME |

## 3. ANC Visit
Source table(s): `visit_instances`, `visit_schedules` for identity/dates; clinical fields via
`form_answers` (submission linked through `visit_instances.visit_id` → `form_submissions.visit_id`)

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| anc_visit_id | visit_instances.visit_id | EXISTS |
| visit_date | visit_instances.actual_visit_date | RENAME |
| visit_type | visit_schedules.visit_type | EXISTS |
| gestational_age_weeks | form_answers (field_code TBD from ANC form schema) | EXTRACT (ETL) |
| trimester | form_answers (field_code TBD) | EXTRACT (ETL) |
| weight | form_answers (field_code TBD) | EXTRACT (ETL) |
| hb | form_answers (field_code TBD) | EXTRACT (ETL) |
| bp_systolic | form_answers (field_code TBD) | EXTRACT (ETL) |
| bp_diastolic | form_answers (field_code TBD) | EXTRACT (ETL) |
| blood_sugar | form_answers (field_code TBD) | EXTRACT (ETL) |
| urine_protein | form_answers (field_code TBD) | EXTRACT (ETL) |
| high_risk_identified | form_answers (field_code TBD) | EXTRACT (ETL) |

## 4. PP Visit
Source table(s): `visit_instances`/`visit_schedules` for identity/dates; clinical fields via `form_answers`;
risk fields via `beneficiary_risk_condition_summary` / `risk_flags` (risk-referral-service)

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| visit_date | visit_instances.actual_visit_date | RENAME |
| visit_number | visit_schedules.sequence_no | RENAME |
| days_post_delivery | form_answers (field_code TBD) | EXTRACT (ETL) |
| visit_type | visit_schedules.visit_type | EXISTS |
| mother_alive | form_answers (field_code TBD) | EXTRACT (ETL) |
| maternal_death_date | form_answers (field_code TBD) | EXTRACT (ETL) |
| danger_signs_present | form_answers (field_code TBD) | EXTRACT (ETL) |
| bleeding_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| fever_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| foul_discharge_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| severe_pain_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| wound_infection_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| pallor_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| dehydration_flag | form_answers (field_code TBD) | EXTRACT (ETL) |
| breastfeeding_status | form_answers (field_code TBD) | EXTRACT (ETL) |
| breastfeeding_difficulty | form_answers (field_code TBD) | EXTRACT (ETL) |
| meals_per_day | form_answers (field_code TBD) | EXTRACT (ETL) |
| diet_diversity_score | form_answers (field_code TBD) | EXTRACT (ETL) |
| ifa_taken | form_answers (field_code TBD) | EXTRACT (ETL) |
| ifa_tablets_consumed | form_answers (field_code TBD) | EXTRACT (ETL) |
| calcium_taken | form_answers (field_code TBD) | EXTRACT (ETL) |
| family_planning_method | form_answers (field_code TBD) | EXTRACT (ETL) |
| fp_side_effects | form_answers (field_code TBD) | EXTRACT (ETL) |
| mental_health_status | form_answers (field_code TBD) | EXTRACT (ETL) |
| family_support | form_answers (field_code TBD) | EXTRACT (ETL) |
| migration_status | form_answers (field_code TBD) | EXTRACT (ETL) |
| current_weight | form_answers (field_code TBD) | EXTRACT (ETL) |
| bmi | form_answers (field_code TBD) | EXTRACT (ETL) |
| muac_cm | form_answers (field_code TBD) | EXTRACT (ETL) |
| bp_systolic | form_answers (field_code TBD) | EXTRACT (ETL) |
| bp_diastolic | form_answers (field_code TBD) | EXTRACT (ETL) |
| hb_g_dl | form_answers (field_code TBD) | EXTRACT (ETL) |
| blood_glucose | form_answers (field_code TBD) | EXTRACT (ETL) |
| referral_id | referrals.referral_id | EXISTS (join, do not duplicate) |
| referral_level | referrals.facility_type (join) | RENAME (or EXTRACT if "level" ≠ facility_type — confirm with ARMMAN) |
| referral_reason | Referral_trigger_sources.trigger_reason (join) | RENAME |
| referral_completed_flag | referrals.status (join, derived: status = COMPLETED) | EXTRACT (ETL, derived) |
| referral_trigger_flag | beneficiary_risk_condition_summary.current_referral_trigger_flag (join) | EXISTS |
| maternal_risk_flag_current | beneficiary_risk_condition_summary (per-condition, join) | EXISTS (join, not 1:1) |
| risk_category_current | risk_assessments.overall_risk_category (join via submission) | EXISTS |
| risk_factors_current | risk_flags (join, multiple rows per assessment) | EXISTS (join, not 1:1) |
| next_visit_due_date | visit_schedules.scheduled_date (next open schedule, join) | EXTRACT (ETL, derived) |
| pnc_status | form_answers (field_code TBD) | EXTRACT (ETL) |

## 5. Delivery Form
Source table(s): `child_case_details` for outcome-linked fields; rest via `form_answers`
(delivery form's submission)

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| delivery_date | form_answers (field_code TBD) | EXTRACT (ETL) |
| delivery_outcome | form_answers (field_code TBD) | EXTRACT (ETL) |
| delivery_location | form_answers (field_code TBD) | EXTRACT (ETL) |
| birth_weight | child_case_details.birth_weight_kg | EXISTS |
| birth_length | child_case_details.birth_length_cm | EXISTS |
| total_anc_visits | visit_instances (count, join+derived) | EXTRACT (ETL, derived) |
| completed_4plus_anc | visit_instances (count, join+derived) | EXTRACT (ETL, derived) |
| high_risk_in_anc | beneficiary_risk_condition_summary.ever_at_risk_flag (join) | EXISTS |

## 6. Neonatal Visit
Source table(s): `visit_instances`/`visit_schedules` for identity/dates; clinical fields via `form_answers`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| nn_visit_type | visit_schedules.visit_type | EXISTS |
| visit_date | visit_instances.actual_visit_date | RENAME |
| age_in_days | form_answers (field_code TBD) | EXTRACT (ETL) |
| weight | form_answers (field_code TBD) | EXTRACT (ETL) |
| temperature | form_answers (field_code TBD) | EXTRACT (ETL) |
| danger_signs | form_answers (field_code TBD) | EXTRACT (ETL) |
| feeding_status | form_answers (field_code TBD) | EXTRACT (ETL) |
| jaundice | form_answers (field_code TBD) | EXTRACT (ETL) |

## 7. Infant Visit (INC)
Source table(s): `visit_instances`/`visit_schedules` for identity/dates; clinical fields via `form_answers`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| inc_visit_type | visit_schedules.visit_type | EXISTS |
| visit_date | visit_instances.actual_visit_date | RENAME |
| age_in_months | date_of_birth + visit_date (derived, ETL — do not store) | EXTRACT (ETL, derived) |
| weight | form_answers (field_code TBD) | EXTRACT (ETL) |
| length | form_answers (field_code TBD) | EXTRACT (ETL) |
| muac | form_answers (field_code TBD) | EXTRACT (ETL) |
| z_score_waz | form_answers (field_code TBD) | EXTRACT (ETL) |
| z_score_haz | form_answers (field_code TBD) | EXTRACT (ETL) |
| z_score_whz | form_answers (field_code TBD) | EXTRACT (ETL) |
| immunisation_status | form_answers (field_code TBD) | EXTRACT (ETL) |
| feeding_status | form_answers (field_code TBD) | EXTRACT (ETL) |

## 8. Mother Closure
Source table: `closures`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| closure_date | closures.closure_date | EXISTS |
| closure_type | closures.closure_type | EXISTS |
| closure_reason | closures.closure_reason | EXISTS |
| event_date | closures.event_date | EXISTS |

## 9. Infant / Child Closure
Source table: `closures`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| closure_date | closures.closure_date | EXISTS |
| closure_type | closures.closure_type | EXISTS |
| closure_reason | closures.closure_reason | EXISTS |
| event_date | closures.event_date | EXISTS |
| age_at_closure | closure_date − date_of_birth (derived, ETL — do not store) | EXTRACT (ETL, derived) |

## 10. Referral Linelist
Source table: `referrals` (some fields need join to `referral_followups` / `referral_trigger_sources`)

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| referral_id | referrals.referral_id | EXISTS |
| referral_type | referrals.referral_type_lookup_value_id (join to lookup_values; value codes STANDARD/ACCOMPANIED unchanged) | EXISTS (join required, not a direct enum column) |
| referral_date | referrals.referral_date | EXISTS |
| referred_condition | — | ADD (currently only in referral_trigger_sources.trigger_reason as free text) |
| facility_visited | — | ADD (or join referral_followups.visited_facility_flag) |
| follow_up_date | — | ADD (or join referral_followups.followup_date) |
| days_between_referral_and_followup | referral_date − followup_date (derived, ETL — join referrals + referral_followups) | EXTRACT (ETL, derived) |

## 11. Referral Follow-up Linelist
Source table: `referral_followups`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| followup_id | referral_followups.followup_id | EXISTS |
| referral_id | referral_followups.referral_id | EXISTS |
| followup_date | referral_followups.followup_date | EXISTS |
| visited_facility_flag | referral_followups.visited_facility_flag | EXISTS |
| not_visited_reason | referral_followups.not_visited_reason | EXISTS |
| treatment_given | referral_followups.treatment_given | EXISTS |
| diagnosis_confirmed | referral_followups.diagnosis | RENAME |
| referral_outcome | referral_followups.outcome | RENAME |
| case_paper_uploaded | referral_followups.case_paper_media_id | RENAME (media ref, not boolean — confirm intended type with ARMMAN) |
| followup_status | referral_followups.followup_status | EXISTS |
| beneficiary_id | — | ADD (joinable via referrals.beneficiary_id; not stored on this table) |
| entity_type | — | ADD |
| child_id | — | ADD |
| followup_attempt_number | — | ADD |
| information_source | — | ADD |
| asha_accompaniment_offered | — | ADD |
| first_facility_type | — | ADD |
| first_facility_name | — | ADD |
| first_visit_date | — | ADD |
| multiple_facilities_flag | — | ADD |
| number_of_facilities | — | ADD |
| last_facility_type | — | ADD |
| last_facility_name | — | ADD |
| last_visit_date | — | ADD |
| treatment_type | — | ADD |
| clinical_status | — | ADD |
| further_referral_flag | — | ADD |
| next_visit_date | — | ADD |
| next_facility_type | — | ADD |
| next_facility_name | — | ADD |
| investigation_uploaded | — | ADD |

## 12. Beneficiary Reopen Linelist
Source table: `reopen_requests`

| Field Name | Postgres Table.Column | Status |
|---|---|---|
| date_of_request | reopen_requests.requested_at | RENAME |
| approved_by_supervisor | reopen_requests.supervisor_status | RENAME |
| reason_for_reopening | reopen_requests.request_reason | RENAME |
| notes | reopen_requests.decision_notes | RENAME |
| lbw_flag | — | ADD (no home anywhere currently) |

---

## Notes
- **No new OLTP tables for MIS.** All ANC/PP/Delivery/NN/INC clinical fields are captured only in
  `form_submissions.form_data_json` / `form_answers.field_code` (visit-form-service) — this is the
  ERD's own intended design for exactly this reporting need (`form_answers` exists specifically to
  make submitted answers "normalized/searchable"). The Airflow ETL job must pivot these EAV rows into
  flat ClickHouse columns per linelist, keyed by each field's `field_code` from the active
  `form_versions.schema_json` — no new Postgres table or column is required for these.
- Fields marked **EXTRACT (ETL)** need their `field_code` looked up in the Revised App Form Final
  (20 Mar 2026) form schema before the ETL mapping can be written — that lookup is the remaining
  open work for this category, not a schema change.
- Fields marked **ADD** are the real schema gaps: they live on structured relational tables
  (`referrals`, `referral_followups`, `reopen_requests`), not form EAV, so there's nowhere for them
  to be pivoted from — a genuine new column is required there.
- Per SRS §3C.4.1, these are the "Key/Critical Fields" per linelist — **non-exhaustive**. Every field
  from the Revised App Form Final (20 Mar 2026) must also be included per form; the EXTRACT/ADD lists
  above are a floor, not a ceiling.
- Every linelist additionally requires (not itemized per-table above, since mostly already available
  via existing FKs/joins):
  - Header variables: Beneficiary ID, Mother ID, Project Name, Funder Name, District, Block, PHC,
    Subcenter, Village, Pada, Arogya Sakhi Name, Supervisor Name, Data Upload Date
  - Risk/grade/flag variables: HR flag, risk category at time of visit, referral trigger flag,
    condition-specific grade fields (Hb grade, BP grade, nutrition status grade), danger signs flags
- SRS explicitly states (line 921 of SRS doc): "Field names above are the canonical column names for
  the ClickHouse schema. These exact names must be used — ARMMAN analysts will write SQL queries
  directly against these names in Metabase." — so use the **Field Name** column values verbatim as
  ClickHouse column names, regardless of whether the source is a renamed Postgres column or an
  EAV-extracted form answer.
- Verified against live Prisma schemas (not just the ERD doc), 2026-09-11: `beneficiary-service`,
  `closure-reopen-service`, `risk-referral-service`, `visit-form-service` in `arogyasakhi-service`.
- **Correction (2026-09-11):** §10 `referral_date` was originally marked RENAME against
  `referrals.referral_form_filled_date` — that column name comes from the ERD doc, which is stale
  here. The live schema (`risk-referral-service/prisma/schema.prisma`) has
  `referralDate DateTime @map("referral_date")` — already named `referral_date`, so this is EXISTS,
  not RENAME. Also corrected: `referral_type` is not a plain enum column in the live schema — it's
  `referral_type_lookup_value_id`, a FK into `lookup_values` (value codes STANDARD/ACCOMPANIED
  unchanged) — reading it for ClickHouse requires a join, not a direct column read. This is the one
  point where the ERD doc and the live database diverge; every other mapping in this file was
  confirmed to match the live Prisma schemas exactly.
