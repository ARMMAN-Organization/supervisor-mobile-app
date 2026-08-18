# 16 endpoints needed for Beneficiary Data Download

> ## Recheck 2026-08-18: all 16 now confirmed live — this doc's "13 need building" finding is stale
>
> Re-verified directly against the live OpenAPI spec at **`https://api.armman.org/api/v1/docs.json`**
> (128 paths) — a real, hardened, reachable host (nginx, HSTS/CSP headers, working `/auth/login`),
> not a placeholder. All 16 entities below, including all 13 previously marked "new"/"needs decision,"
> now exist as live routes with paths/params matching the app's Retrofit interfaces exactly. The app
> (`app/src/main/kotlin/org/armman/supervisor/data/beneficiarydatadownload/`) was already coded
> against the correct shapes; `local.properties`'s `API_BASE_URL` has been switched from the
> per-developer ngrok tunnel to `https://api.armman.org/api/v1/` to match.
>
> One real drift found and fixed: `GET /sync/pending` takes query param `userId` on armman.org, not
> `sakhiId` as the app previously sent (optional param, defaulted to the caller's own id when
> absent/unrecognized — so the per-Sakhi filter was silently ignored). Fixed in `SyncPendingApi.kt`.
>
> The likely explanation for the original "13 missing" finding: this doc's own warning below about
> 16 route-registration commits/day churn on the *ngrok* tunnel — that tunnel is a snapshot of one
> developer's branch, not the stable host. `api.armman.org` was the actual target all along.

Verified against the live backend (92 endpoints, checked by path, tag, and summary — not guessed). 3 of 16 tables can reuse an existing endpoint; 13 need to be built from scratch. This is the full request, formatted for direct hand-off.

> **Before starting:** none of these 16 entities are referenced anywhere in the SRS, ERD, or HLD — they only exist in the reference Android app's implementation. Whoever picks this up should confirm scope and field shapes with product before building, not just implement the proposed schemas below as-is. They're a starting draft, not an approved spec.

**Summary (superseded by the 2026-08-18 recheck above):** 3 tables can reuse an existing, already-live endpoint · 13 tables have no endpoint under any name and need building from scratch · 16 total, none of them referenced in SRS/ERD/HLD.

---

## A. Reuse existing endpoints — no new build (3)

These already work for other screens; confirm the response shape covers this download's needs before wiring, don't rebuild.

| # | Entity | Existing endpoint | Note |
|---|--------|-------------------|------|
| 3 | Beneficiaries List | `GET /beneficiaries` | Cursor-paginated, filterable by project/village/sakhi — good structural fit already. |
| 8 | Sakhi Item Transaction | `GET /inventory-transactions` | Confirm field names match what the download expects. |
| 11 | Gathering Attendance | `GET /gatherings/:gatheringId/attendance` | Direct match on both name and shape. |

---

## B. New endpoints to build (13)

Grouped by proposed owning service. Each block is ready to paste into a ticket.

### `GET /arogya-sakhi-roster?projectId={id}` — **new**
- **Owner:** auth-service (co-located with `SakhiProfile`)
- **Purpose:** Download the Sakhi roster for offline reference — distinct from the existing `/projects/:id/sakhis` assignment list; this is the flat "ArogyaSakhi" Room table shape the reference app expects.
- **Decision needed first:** Whether `SakhiProfile` can be projected directly, or needs its own DTO.

### `GET /registration-targets?sakhiId={id}` — **new**
- **Owner:** auth-service
- **Purpose:** Download a Sakhi's registration target for the period — a new per-Sakhi model. The ERD only has a project-level `registration_target` column; this needs its own table at Sakhi grain.

```prisma
model RegistrationTarget {
  id          String   @id @default(uuid())
  sakhiId     String
  projectId   String
  periodStart DateTime
  periodEnd   DateTime
  targetCount Int
}
```

### `GET /beneficiaries/:id/risk` — **needs decision**
- **Owner:** risk-referral-service
- **Purpose:** Header + detail rows for a beneficiary's risk profile (`beneficiary_risk_header`, `beneficiary_risk_details`). `RiskAssessment`/`RiskStateSnapshot` exist but aren't shaped this way today.
- **Decision needed first:** New models, or a header/detail projection over the existing tables — needs product sign-off on the split.

### `GET /beneficiaries/:id/visits` — **new**
- **Owner:** visit-form-service (not beneficiary-service — visit data intentionally lives outside it)
- **Purpose:** A beneficiary's visit history for offline reference, filtered from `VisitInstance`/`VisitSchedule`, which already carry `beneficiaryId`.
- **Decision needed first:** Field mapping only — no new table needed if a filtered view is acceptable.

### `GET /risk-monitoring?...` — **needs decision**
- **Owner:** risk-referral-service (proposed — unconfirmed)
- **Purpose:** Unknown. Zero references anywhere in SRS/ERD/HLD or the codebase for what "risk monitoring" tracks beyond what `RiskAssessment`/`RiskStateSnapshot` already capture.
- **Decision needed first:** Product must define the field list before any schema or endpoint work starts here — this is the least-defined item on the list.

### `GET /sync/pending?sakhiId={id}` — **new**
- **Owner:** sync-service
- **Purpose:** Which sync items are still outstanding for a Sakhi — a filtered view over the existing `SyncItem` queue (`status != SUCCESS`), not a new table.

### `GET /item-transactions/:id/details` — **needs decision**
- **Owner:** same service as `/inventory-transactions` (Group A above)
- **Purpose:** Line-item detail for one transaction — check whether `/inventory-transactions/:id` already returns this before building a new route.

### `GET /gatherings?sakhiId={id}` — **new**
- **Owner:** Supervisor Operations service (already owns `/supervisor-events`, `/gatherings/:id/attendance`)
- **Purpose:** List gatherings for offline reference — check whether `/supervisor-events` already returns this shape before adding a parallel route.

### `GET /gatherings/:id/training-marks` — **new**
- **Owner:** Supervisor Operations service
- **Purpose:** Pre/post training marks per gathering. A generic `/topics/:topicId/marks` exists today — confirm whether it already covers this or a dedicated per-gathering rollup is needed.

### `GET /gatherings/:id/images` — **needs decision**
- **Owner:** Supervisor Operations service, joined to media-service's `MediaAsset`
- **Purpose:** Photos attached to a gathering. `/supervisor-events/:id/photos` exists — confirm whether gatherings and events share the same photo mechanism before building a separate route.

### `GET /sakhi-calls?sakhiId={id}` — **new**
- **Owner:** Supervisor Operations service (already owns `/call-logs`)
- **Purpose:** Header + detail call records for offline reference. `/call-logs` and `/call-logs/by-sakhi/:sakhiId` already exist — confirm whether they cover this before building a parallel route.

### `GET /beneficiaries/:id/risk-referrals` — **needs decision**
- **Owner:** risk-referral-service (already owns `/referrals`)
- **Purpose:** Header row for a beneficiary's risk referrals. Existing `Referral` model is flatter than the header/detail split this download expects.
- **Decision needed first:** Expose `Referral` as-is (renamed/mapped), or add a dedicated header/detail projection — resolve together with the row below.

### `GET /beneficiaries/:id/risk-referrals/:referralId/details` — **needs decision**
- **Owner:** risk-referral-service
- **Purpose:** Detail lines under a referral header. Closest existing models are `ReferralFollowup`/`ReferralTriggerSource`, neither an exact fit.

---

## Suggested build order

| Step | What | Why first |
|------|------|-----------|
| 1 | Confirm Group A's 3 existing endpoints actually cover the download shape | Zero build cost if they do — cheapest thing to check first. |
| 2 | Beneficiary Visit, Sakhi Not Uploaded Data (filtered views, no new tables) | No migration needed — fastest real progress. |
| 3 | ArogyaSakhi roster, Registration Target (small new tables, existing services) | Contained scope, clear owner. |
| 4 | Beneficiary Risk, Risk Referral Header/Details (needs a product decision on the header/detail split) | Get the shape decision made once, build both together. |
| 5 | Gathering List/Training Marks/Images, Sakhi Calls, Item Transaction Detail | Check for reuse against existing Supervisor Operations routes before building new ones. |
| 6 | Risk Monitoring | Blocked on product defining what it even tracks — do this last, or in parallel while the rest is built. |

---

*Verified against the live backend at `neuter-morality-refract.ngrok-free.dev/api/v1/docs.json` (92 endpoints, checked by path, tag, and summary). Source list: Beneficiary Data Download — Master Data & Room Tables reference PDF. None of these 16 entities appear in SRS v3.0, the ERD, or HLD v2.0.*
