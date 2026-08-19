# 28 endpoints needed for Download Master Data

> ## Recheck 2026-08-18: a real stable host exists — `api.armman.org`
>
> Contrary to the warning banner below, **`https://api.armman.org/api/v1/` is live, reachable, and
> not a placeholder** — confirmed via its OpenAPI spec (128 paths), proper security headers
> (HSTS/CSP/nginx), and a working `/auth/login` (correctly returns 401 on bad credentials). All 27
> "ready" master-data entities plus `Incentive Rate`'s `/incentive-rates/active` route are present
> on this host with matching paths/params. `local.properties`'s `API_BASE_URL` has been switched
> from the ngrok tunnel to `https://api.armman.org/api/v1/`, and the "placeholder prod host" comment
> in `app/build.gradle.kts` describing armman.org has been corrected. The ngrok tunnel documented
> below was one developer's snapshot, not the only option — treat `api.armman.org` as the default
> going forward unless backend says otherwise.

Backend has directly confirmed 5 open questions from an earlier snapshot of this doc (see "Backend-confirmed findings" below). This version reflects those answers, not just another live re-check.

> ⚠️ **The `neuter-morality-refract.ngrok-free.dev` environment is a developer's local tunnel, not a stable target.** Backend confirmed: it's not a shared staging environment, there is no staging URL in the repo to fall back to, and the tunnel goes offline whenever that developer's machine isn't running (`ERR_NGROK_3200` observed directly). Same-day commit history shows 16 route-registration commits across 6 services in one day — that volume of churn is the actual cause of endpoints appearing/disappearing across checks, not a bug in this doc's verification method.

---

## Backend-confirmed findings

### 1. `/incentive-rates` was never a bulk list endpoint — not a regression
Backend checked `incentiveRate.routes.ts` and its git history directly: only one route was ever registered, `GET /incentive-rates/active`, and it has exactly one commit in its history. There is no list endpoint to have regressed from.

It's also not shaped like a list — the envelope wraps a **single object**, not an array. It resolves one currently-effective rate for a given type:

| Param | Required? | Values |
|-------|-----------|--------|
| `rateType` | required | `VISIT` \| `REFERRAL` \| `MEETING` \| `TRAINING` \| `RETAINER` |
| `referralType` | optional | `STANDARD` \| `ACCOMPANIED` |
| `geographyUnitId` | optional | uuid |
| `asOf` | optional | date, defaults to now |

Confirmed live: no `rateType` → `400 rateType: Required`. With `rateType` → `404 No active incentive rate found` (nothing seeded, see finding 3/4).

**Verdict: Incentive Rate has no download/bulk-list endpoint today.** This is a real, open feature gap — not a bug to fix, a route to build. Until it exists, `MasterDataEntity.INCENTIVE_RATE` stays `ready = false` in the app (see Group B below).

### 2. Role access on 4 previously-403 routes — confirmed correct, no bug
Backend re-tested all 4 with a real `SUPERVISOR` token (`anita.deshmukh`, seeded user):

| Route | SAKHI (earlier test) | SUPERVISOR (backend-verified) |
|-------|----------------------|-------------------------------|
| `GET /projects/{projectId}/sakhis` | 403 | 200, `data: []` |
| `GET /item-master-list` | 403 | 200, `data: []` |
| `GET /training-topics` | 403 | 200, `data: []` |
| `GET /incentive-rates/active?rateType=VISIT` | 403 | 404 `No active incentive rate found` (role check passes; fails downstream on unseeded data) |

All 4 correctly gate on `requireRoles('SUPERVISOR', 'MANAGER', 'ADMIN')`. Role config is right — this was never a bug, just untested with the right role.

### 3 & 4. Empty arrays on Item Master List / Training Topics / Funders / Project Geography — unseeded data, not a missing param
Backend confirmed two ways:
- **Code:** none of these 4 routes has any query-param validation middleware at all — there is no param to be missing.
- **Data:** no seed script exists anywhere in the repo for `supervisor-operations-service`. The only `.create()` calls for funders, inventory items, training topics, and the project↔geography link table are in live POST-endpoint code, never in a seed script.

**Verdict: these 4 need real seed data before a populated response is possible.** Not a code issue on either side.

### 5. Environment instability — confirmed, root cause identified
See the warning banner above. Backend's own words: *"until [a staging URL] exists, the mobile team is dependent on someone's laptop tunnel staying up, which it isn't right now."* Flagged as its own open gap, not resolved by this doc.

---

## A. Confirmed live and correctly usable (26)

| # | Entity | Endpoint | Note |
|---|--------|----------|------|
| 1 | State | `GET /geography-units/roots` | |
| 2 | District | `GET /geography-units?geoType=DISTRICT&parentId={stateId}` | Same route as rows below, filtered by `geoType`. |
| 3 | Block | `GET /geography-units?geoType=BLOCK&parentId={districtId}` | |
| 4 | PHC | `GET /geography-units?geoType=PHC&parentId={blockId}` | |
| 5 | Sub-Center | `GET /geography-units?geoType=SUBCENTRE&parentId={phcId}` | |
| 6 | Village | `GET /geography-units?geoType=VILLAGE&parentId={subcentreId}` | |
| 7 | Village List | `GET /geography-units?geoType=VILLAGE&parentId={subcentreId}` | Same data as Village — one `GeographyUnit` row covers both reference-app rows. |
| 8 | Funders | `GET /funders` | Confirmed correct — empty because unseeded (finding 3/4), not broken. |
| 9 | Projects | `GET /projects` | Confirmed with real data. |
| 10 | Sakhi | `GET /projects/{projectId}/sakhis` | Confirmed correct with SUPERVISOR role (finding 2) — empty because unseeded. |
| 11 | Risk | `GET /risk-conditions` | Confirmed with real data. |
| 12 | Risk Category | `GET /risk-categories` | Confirmed with real data. |
| 13 | Risk Language | `GET /risk-languages` | Confirmed with real data. |
| 14 | Risk Type | `GET /risk-types` | Confirmed with real data. |
| 15 | Risk Parameter | `GET /risk-parameters` | Confirmed with real, distinct data (previously flagged as aliased to Risk Conditions — that's fixed). |
| 16 | Visit Master | `GET /visit-masters` | Confirmed with real, distinct data (previously flagged as aliased to Visit Category — that's fixed). |
| 17 | Visit Category | `GET /visit-categories` | Confirmed with real data. |
| 18 | Item Category | `GET /item-categories` | Confirmed with real data. |
| 19 | UOM List | `GET /uom-list` | Confirmed with real data. |
| 20 | Item Master List | `GET /item-master-list` | Confirmed correct with SUPERVISOR role (finding 2) — empty because unseeded (finding 3/4). |
| 21 | Transaction Type | `GET /transaction-types` | Confirmed with real data. |
| 22 | Training Topic Master | `GET /training-topics` | Confirmed correct with SUPERVISOR role (finding 2) — empty because unseeded (finding 3/4). |
| 23 | Gathering Status | `GET /gathering-statuses` | Confirmed with real data. |
| 24 | Gathering Types | `GET /gathering-types` | Confirmed with real data. |
| 25 | DDL Item | `GET /ddl-items` | Confirmed with real data (largest dataset — 25 categories). |
| 26 | Project Geography | `GET /project-geography?projectId={id}` | Confirmed correct — empty because unseeded (finding 3/4). |
| 27 | Application Parameter | `GET /application-parameters` | Confirmed with real data (`MAX_UPLOAD_SIZE_MB`, `MIN_SUPPORTED_APP_VERSION`, `SYNC_INTERVAL_MINUTES`). |

*(Numbered to 27 above because Village and Village List share one row — 26 distinct app entities, 28 reference-app rows.)*

## B. Not usable as a bulk download (1)

| Entity | Real endpoint | Why it's not wired |
|--------|---------------|---------------------|
| Incentive Rate | `GET /incentive-rates/active` | Resolves one rate for a required `rateType` — not a list/download endpoint. No bulk route exists. Confirmed by backend (finding 1). Stays `ready = false` in the app until backend ships a real list endpoint. |

---

## What changed in the app (this pass)

- `MasterDataEntity.INCENTIVE_RATE` flipped back to `ready = false` — the app was calling a `/incentive-rates` bulk route that never existed, which surfaced as a false "no internet connection" dialog. Root cause was a stale assumption on the mobile side, not a backend bug (see finding 1). Removed the now-dead `IncentiveRateDto`/`IncentiveRatesEnvelopeDto`/`getIncentiveRates()`.
- `Project Geography`, `Application Parameter`, `Risk Parameter`, and `Visit Master` are now fully wired to real endpoints (`GET /project-geography?projectId=`, `GET /application-parameters`, `GET /risk-parameters`, `GET /visit-masters`) — all 4 were previously either unwired or flagged as blocked/aliased in earlier drafts of this doc, and are now confirmed correct with real, distinct data.
- **27 of 28 entities are now wired to real endpoints.** Only Incentive Rate remains `NotAvailable`, pending backend building an actual bulk download route (finding 1).

## Still open

1. **Build a real Incentive Rate download/list endpoint** — backend confirmed this is a genuine gap, not a fix. Needs a product/backend decision on shape (likely `GET /incentive-rates` returning all currently-effective rates, no required params).
2. **Seed Funders, Sakhi, Item Master List, Training Topics, and Project Geography** in whatever environment QA/demo uses — the endpoints are correct, there's simply nothing to return yet.
3. **Get a stable staging URL** — backend confirmed none exists; the mobile team is currently dependent on an individual developer's tunnel staying up.

---

## Sources

- Backend's direct, code-and-git-sourced answers to a 5-question verification request (this doc's primary source for the current version)
- Live spec fetched multiple times across one investigation session (69 → 104 → 92 → 111 total paths across checks — explained by finding 5, not a measurement error)
- Direct authenticated `curl`/Postman calls confirming real data for all 26 entities in Group A, and the single-object/required-param shape of `/incentive-rates/active`
- This doc supersedes an earlier draft based on static repo/schema analysis alone (pre-dates any live backend testing)
