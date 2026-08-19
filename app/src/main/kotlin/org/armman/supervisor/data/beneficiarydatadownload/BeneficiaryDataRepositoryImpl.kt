package org.armman.supervisor.data.beneficiarydatadownload

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.ui.beneficiarydatadownload.BeneficiaryDataEntity
import javax.inject.Inject

/**
 * Whether [BeneficiaryDataRepositoryImpl] should mock every not-[BeneficiaryDataEntity.ready]
 * entity as a successful download, purely so the 16-row screen can be visually QA'd end-to-end
 * before real endpoints exist for those entities. Bound in
 * [org.armman.supervisor.di.BeneficiaryDataDownloadModule.provideMockUnreadyBeneficiaryDataEntities]
 * to [BuildConfig.DEBUG] — a release build always gets `false`, so this can never leak into
 * production regardless of what it reads in a debug build.
 */
class MockUnreadyBeneficiaryDataEntities(val enabled: Boolean)

/** HTTP codes treated as "this one record is out of the caller's scope" rather than a download
 * failure — e.g. seed-data ownership mismatches. Skipping just the affected record lets the rest
 * of a bulk per-record fetch (per-beneficiary, per-referral, per-transaction) complete instead of
 * failing the whole entity over one inaccessible row. */
private val SKIPPABLE_RECORD_HTTP_CODES = setOf(403, 404)

/**
 * Routes each [BeneficiaryDataEntity] to the repository that actually owns its data. Every
 * `ready` entity MUST have a `when` branch below — the `else` branch throws deliberately (rather
 * than silently no-op-ing) so a `ready = true` entity with no case wired fails loudly during
 * testing instead of surfacing as an unexplained "no internet" dialog in production (this exact
 * bug happened once in the Master Data flow — see `MasterDataRepositoryImpl`'s doc comment).
 *
 * Most calls here need a `sakhiId` or `beneficiaryId` the screen itself doesn't have a natural
 * single scope for (unlike Master Data, which downloads globally) — so every Sakhi-scoped entity
 * sums its result across every Sakhi on every project the Supervisor can see, and every
 * beneficiary-scoped entity additionally sums across every beneficiary in the resulting page.
 *
 * The beneficiaries list and gatherings list are each fetched by more than one entity in the same
 * 16-entity sequence (see [BeneficiaryDataEntity]) — [beneficiariesCache]/[gatheringsCache] hold
 * each Sakhi/page's result for the lifetime of one sequence so repeat entities reuse it instead of
 * re-fetching identical data. [startSession] MUST be called once before a sequence starts
 * (including retries) to clear stale cache from a prior attempt.
 */
class BeneficiaryDataRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val beneficiaryDownloadListApi: BeneficiaryDownloadListApi,
  private val syncPendingApi: SyncPendingApi,
  private val gatheringDownloadApi: GatheringDownloadApi,
  private val beneficiaryRiskApi: BeneficiaryRiskApi,
  private val beneficiaryVisitApi: BeneficiaryVisitApi,
  private val riskMonitoringApi: RiskMonitoringApi,
  private val arogyaSakhiRosterApi: ArogyaSakhiRosterApi,
  private val mockUnreadyEntities: MockUnreadyBeneficiaryDataEntities,
) : BeneficiaryDataRepository {

  private companion object {
    const val MOCK_DOWNLOAD_DELAY_MS = 1200L
    const val MOCK_RECORD_COUNT = 12
  }

  private var beneficiariesCache: List<BeneficiaryDownloadCaseDto>? = null
  private val gatheringsCache = java.util.concurrent.ConcurrentHashMap<String, List<GatheringDto>>()

  override fun startSession() {
    beneficiariesCache = null
    gatheringsCache.clear()
  }

  override suspend fun download(entity: BeneficiaryDataEntity): BeneficiaryDataResult {
    if (!entity.ready) {
      return if (mockUnreadyEntities.enabled) {
        delay(MOCK_DOWNLOAD_DELAY_MS)
        BeneficiaryDataResult.Success(MOCK_RECORD_COUNT)
      } else {
        BeneficiaryDataResult.NotAvailable
      }
    }

    return runCatching {
      when (entity) {
        BeneficiaryDataEntity.AROGYA_SAKHI -> downloadArogyaSakhiRoster()
        BeneficiaryDataEntity.REGISTRATION_TARGET -> forEachSakhi { sakhiId -> downloadRegistrationTargets(sakhiId) }
        BeneficiaryDataEntity.BENEFICIARIES_LIST -> loadAllBeneficiaries().size
        BeneficiaryDataEntity.BENEFICIARY_RISK -> forEachSakhiBeneficiary { id -> downloadBeneficiaryRisk(id) }
        BeneficiaryDataEntity.BENEFICIARY_VISIT -> forEachSakhiBeneficiary { id -> downloadBeneficiaryVisits(id) }
        BeneficiaryDataEntity.RISK_MONITORING -> downloadRiskMonitoring()
        BeneficiaryDataEntity.SAKHI_NOT_UPLOADED_DATA -> forEachSakhi { sakhiId -> downloadPendingSyncItems(sakhiId) }
        BeneficiaryDataEntity.SAKHI_ITEM_TRANSACTION -> downloadAllInventoryTransactions()
        BeneficiaryDataEntity.SAKHI_ITEM_TRANSACTION_DETAIL -> downloadInventoryTransactionDetails()
        BeneficiaryDataEntity.GATHERING_LIST -> forEachSakhi { sakhiId -> loadGatherings(sakhiId).size }
        BeneficiaryDataEntity.GATHERING_ATTENDANCE -> forEachGathering { id -> downloadAttendance(id) }
        BeneficiaryDataEntity.GATHERING_TRAINING_MARKS -> forEachGathering { id -> downloadTrainingMarks(id) }
        BeneficiaryDataEntity.GATHERING_IMAGES -> forEachGathering { id -> downloadGatheringImages(id) }
        BeneficiaryDataEntity.CALL_DETAILS -> forEachSakhi { sakhiId -> downloadSakhiCalls(sakhiId) }
        BeneficiaryDataEntity.BENEFICIARY_RISK_REFERRAL_HEADER -> forEachSakhiBeneficiary { id -> downloadRiskReferrals(id) }
        BeneficiaryDataEntity.BENEFICIARY_RISK_REFERRAL_DETAILS -> downloadRiskReferralDetails()
        else -> error("$entity is marked ready but has no download case wired")
      }
    }.let { result ->
      // CancellationException must propagate to unwind the coroutine on quit/back — folding it
      // into Failure risks a stray "No internet connection" dialog racing the screen's own exit.
      result.exceptionOrNull()?.let { cause -> if (cause is CancellationException) throw cause }
      result
    }.fold(
      onSuccess = { count -> if (count > 0) BeneficiaryDataResult.Success(count) else BeneficiaryDataResult.Empty },
      onFailure = { cause -> BeneficiaryDataResult.Failure(cause) },
    )
  }

  private suspend fun downloadArogyaSakhiRoster(): Int = projectsRepository.getProjects().sumOf { project ->
    val response = arogyaSakhiRosterApi.getArogyaSakhiRoster(project.id)
    if (!response.isSuccessful) error("Failed to load Arogya Sakhi roster: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Arogya Sakhi roster response")
    if (!body.success) error(body.message ?: "Failed to load Arogya Sakhi roster")
    body.data.orEmpty().size
  }

  private suspend fun downloadRegistrationTargets(sakhiId: String): Int {
    val response = arogyaSakhiRosterApi.getRegistrationTargets(sakhiId)
    if (!response.isSuccessful) error("Failed to load registration targets: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty registration targets response")
    if (!body.success) error(body.message ?: "Failed to load registration targets")
    return body.data.orEmpty().size
  }

  /** Fetches every page of the bulk beneficiaries list, following `nextCursor` until the backend
   * reports none left, and caches the full result for the lifetime of the current [download]
   * sequence (cleared by [startSession]) so every entity routed through [forEachSakhiBeneficiary]
   * reuses it instead of re-fetching. */
  private suspend fun loadAllBeneficiaries(): List<BeneficiaryDownloadCaseDto> {
    beneficiariesCache?.let { return it }

    val items = mutableListOf<BeneficiaryDownloadCaseDto>()
    var cursor: String? = null
    do {
      val response = beneficiaryDownloadListApi.getBeneficiariesDownload(cursor = cursor)
      if (!response.isSuccessful) error("Failed to load beneficiaries: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty beneficiaries response")
      if (!body.success) error(body.message ?: "Failed to load beneficiaries")
      val page = body.data ?: break
      items += page.items
      cursor = page.nextCursor
    } while (cursor != null)

    return items.also { beneficiariesCache = it }
  }

  private suspend fun downloadBeneficiaryRisk(beneficiaryId: String): Int {
    val response = beneficiaryRiskApi.getBeneficiaryRisk(beneficiaryId)
    if (response.code() in SKIPPABLE_RECORD_HTTP_CODES) return 0
    if (!response.isSuccessful) error("Failed to load beneficiary risk: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty beneficiary risk response")
    if (!body.success) error(body.message ?: "Failed to load beneficiary risk")
    val profile = body.data ?: return 0
    return profile.currentState.size + profile.assessments.size
  }

  private suspend fun downloadBeneficiaryVisits(beneficiaryId: String): Int {
    val response = beneficiaryVisitApi.getBeneficiaryVisits(beneficiaryId)
    if (response.code() in SKIPPABLE_RECORD_HTTP_CODES) return 0
    if (!response.isSuccessful) error("Failed to load beneficiary visits: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty beneficiary visits response")
    if (!body.success) error(body.message ?: "Failed to load beneficiary visits")
    return body.data.orEmpty().size
  }

  private suspend fun downloadRiskMonitoring(): Int {
    val response = riskMonitoringApi.getRiskMonitoring()
    if (!response.isSuccessful) error("Failed to load risk monitoring: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty risk monitoring response")
    if (!body.success) error(body.message ?: "Failed to load risk monitoring")
    return body.data?.everAtRiskCount ?: 0
  }

  private suspend fun downloadPendingSyncItems(sakhiId: String): Int {
    val response = syncPendingApi.getPendingSyncItems(sakhiId)
    if (!response.isSuccessful) error("Failed to load pending sync items: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty pending sync items response")
    if (!body.success) error(body.message ?: "Failed to load pending sync items")
    return body.data.orEmpty().size
  }

  private suspend fun downloadAllInventoryTransactions(): Int {
    val response = beneficiaryDownloadListApi.getAllInventoryTransactions()
    if (!response.isSuccessful) error("Failed to load inventory transactions: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty inventory transactions response")
    if (!body.success) error(body.message ?: "Failed to load inventory transactions")
    return body.data.orEmpty().size
  }

  /** No single "all transaction details" endpoint exists — fetches every transaction's own detail
   * (currently an alias for a single-transaction fetch, per the backend rework doc) and sums.
   *
   * A transaction returned by the list endpoint can still 403/404 on its own details endpoint
   * (e.g. ownership scoping mismatches in seed data) — that's a per-record access gap, not a
   * reason to fail the whole download, so those rows are skipped rather than propagated as
   * [BeneficiaryDataResult.Failure]. */
  private suspend fun downloadInventoryTransactionDetails(): Int {
    val listResponse = beneficiaryDownloadListApi.getAllInventoryTransactions()
    if (!listResponse.isSuccessful) error("Failed to load inventory transactions: HTTP ${listResponse.code()}")
    val listBody = listResponse.body() ?: error("Empty inventory transactions response")
    if (!listBody.success) error(listBody.message ?: "Failed to load inventory transactions")

    var detailCount = 0
    for (transaction in listBody.data.orEmpty()) {
      val response = beneficiaryDownloadListApi.getInventoryTransactionDetails(transaction.id)
      if (response.code() in SKIPPABLE_RECORD_HTTP_CODES) continue
      if (!response.isSuccessful) error("Failed to load transaction detail: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty transaction detail response")
      if (!body.success) error(body.message ?: "Failed to load transaction detail")
      if (body.data != null) detailCount++
    }
    return detailCount
  }

  /** Fetches one Sakhi's gatherings and caches the result for the lifetime of the current
   * [download] sequence (cleared by [startSession]) so every entity routed through
   * [forEachGathering] reuses it instead of re-fetching per entity. */
  private suspend fun loadGatherings(sakhiId: String): List<GatheringDto> {
    gatheringsCache[sakhiId]?.let { return it }

    val response = gatheringDownloadApi.getGatherings(sakhiId)
    if (!response.isSuccessful) error("Failed to load gatherings: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty gatherings response")
    if (!body.success) error(body.message ?: "Failed to load gatherings")
    return body.data.orEmpty().also { gatheringsCache[sakhiId] = it }
  }

  private suspend fun downloadAttendance(gatheringId: String): Int {
    val response = gatheringDownloadApi.getAttendance(gatheringId)
    if (!response.isSuccessful) error("Failed to load gathering attendance: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty gathering attendance response")
    if (!body.success) error(body.message ?: "Failed to load gathering attendance")
    return body.data.orEmpty().size
  }

  private suspend fun downloadTrainingMarks(gatheringId: String): Int {
    val response = gatheringDownloadApi.getTrainingMarks(gatheringId)
    if (!response.isSuccessful) error("Failed to load gathering training marks: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty gathering training marks response")
    if (!body.success) error(body.message ?: "Failed to load gathering training marks")
    return body.data.orEmpty().size
  }

  private suspend fun downloadGatheringImages(gatheringId: String): Int {
    val response = gatheringDownloadApi.getGatheringImages(gatheringId)
    if (!response.isSuccessful) error("Failed to load gathering images: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty gathering images response")
    if (!body.success) error(body.message ?: "Failed to load gathering images")
    return body.data?.photos?.size ?: 0
  }

  private suspend fun downloadSakhiCalls(sakhiId: String): Int {
    val response = beneficiaryDownloadListApi.getSakhiCalls(sakhiId)
    if (!response.isSuccessful) error("Failed to load Sakhi calls: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty Sakhi calls response")
    if (!body.success) error(body.message ?: "Failed to load Sakhi calls")
    return body.data.orEmpty().size
  }

  private suspend fun downloadRiskReferrals(beneficiaryId: String): Int {
    val response = beneficiaryRiskApi.getRiskReferrals(beneficiaryId)
    if (response.code() in SKIPPABLE_RECORD_HTTP_CODES) return 0
    if (!response.isSuccessful) error("Failed to load risk referrals: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty risk referrals response")
    if (!body.success) error(body.message ?: "Failed to load risk referrals")
    return body.data.orEmpty().size
  }

  private suspend fun downloadRiskReferralDetails(): Int = forEachSakhiBeneficiary { beneficiaryId ->
    val referralsResponse = beneficiaryRiskApi.getRiskReferrals(beneficiaryId)
    if (referralsResponse.code() in SKIPPABLE_RECORD_HTTP_CODES) return@forEachSakhiBeneficiary 0
    if (!referralsResponse.isSuccessful) error("Failed to load risk referrals: HTTP ${referralsResponse.code()}")
    val referralsBody = referralsResponse.body() ?: error("Empty risk referrals response")
    if (!referralsBody.success) error(referralsBody.message ?: "Failed to load risk referrals")

    referralsBody.data.orEmpty().sumOf { referral ->
      val response = beneficiaryRiskApi.getRiskReferralDetails(beneficiaryId, referral.id)
      if (response.code() in SKIPPABLE_RECORD_HTTP_CODES) return@sumOf 0
      if (!response.isSuccessful) error("Failed to load risk referral details: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty risk referral details response")
      if (!body.success) error(body.message ?: "Failed to load risk referral details")
      val details = body.data
      (details?.followups?.size ?: 0) + (details?.triggerSources?.size ?: 0)
    }
  }

  /** Sums [block]'s result across every Sakhi on every project the Supervisor can see, running
   * one [block] call per Sakhi concurrently (matching the `async`/`awaitAll` pattern already used
   * by [org.armman.supervisor.data.risksummary.RiskSummaryRepositoryImpl] and
   * [org.armman.supervisor.data.registrations.RegistrationsRepositoryImpl]) rather than one HTTP
   * round trip at a time. */
  private suspend fun forEachSakhi(block: suspend (sakhiId: String) -> Int): Int = coroutineScope {
    projectsRepository.getProjects()
      .flatMap { project -> projectsRepository.getSakhis(project.id) }
      .map { sakhi -> async { block(sakhi.id) } }
      .awaitAll()
      .sum()
  }

  /** Sums [block]'s result across every beneficiary in [loadAllBeneficiaries]'s (fully paginated,
   * cached) result, one [block] call per beneficiary concurrently — a bounded stand-in until this
   * screen has its own per-Sakhi/per-project beneficiary scoping decision (see the class doc
   * comment). */
  private suspend fun forEachSakhiBeneficiary(block: suspend (beneficiaryId: String) -> Int): Int = coroutineScope {
    loadAllBeneficiaries()
      .map { beneficiary -> async { block(beneficiary.id) } }
      .awaitAll()
      .sum()
  }

  /** Sums [block]'s result across every gathering visible via [loadGatherings], scoped per Sakhi
   * same as [forEachSakhi] (and sharing its cache, so this doesn't re-fetch what [forEachSakhi]
   * or an earlier gathering-scoped entity already loaded), one [block] call per gathering
   * concurrently. */
  private suspend fun forEachGathering(block: suspend (gatheringId: String) -> Int): Int =
    forEachSakhi { sakhiId ->
      coroutineScope {
        loadGatherings(sakhiId).map { gathering -> async { block(gathering.id) } }.awaitAll().sum()
      }
    }
}
