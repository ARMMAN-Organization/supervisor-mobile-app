package org.armman.supervisor.data.masterdata

import android.util.Log
import kotlinx.coroutines.delay
import org.armman.supervisor.BuildConfig
import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupCategoryEnvelopeDto
import org.armman.supervisor.data.lookups.LookupsApi
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import retrofit2.Response
import javax.inject.Inject

/**
 * Whether [MasterDataRepositoryImpl] should mock every not-[MasterDataEntity.ready] entity as a
 * successful download, purely so the ~28-row screen can be visually QA'd end-to-end before real
 * endpoints exist for those entities. Bound in
 * [org.armman.supervisor.di.MasterDataModule.provideMockUnreadyMasterDataEntities] to
 * [BuildConfig.DEBUG] — a release build always gets `false`, so this can never leak into
 * production regardless of what it reads in a debug build. [enabled] is a plain constructor
 * parameter (not [MasterDataRepositoryImpl] reading [BuildConfig] itself) so a unit test can
 * construct [MasterDataRepositoryImpl] against an instance with [enabled] pinned to either value
 * directly, exercising both the mock-on and the real mock-off/"not available yet" branch
 * deterministically, without needing to control a compile-time constant.
 */
class MockUnreadyMasterDataEntities(val enabled: Boolean)

/**
 * Routes each [MasterDataEntity] to the repository that actually owns its data. Every `ready`
 * entity MUST have a `when` branch below — the `else` branch throws deliberately (rather than
 * silently no-op-ing) so a `ready = true` entity with no case wired fails loudly during testing
 * instead of surfacing as an unexplained "no internet" dialog in production.
 *
 * [MasterDataEntity.INCENTIVE_RATE] stays `ready = false`: backend confirmed `/incentive-rates/active`
 * resolves one rate for a required `rateType`, not a bulk list — there is no "download every
 * incentive rate" endpoint today.
 *
 * Geography (State→District→Block→PHC→Sub-Center→Village) is fetched top-down: each level's
 * download needs the parent ids the previous level just returned, so [geographyParentIds] holds
 * them for the run. The screen always downloads [MasterDataEntity.STATE] first (it's the first
 * enum entry), so that call resets the cache — the natural "a new chain just started" signal,
 * including on retry.
 */
class MasterDataRepositoryImpl @Inject constructor(
  private val projectsRepository: ProjectsRepository,
  private val geographyApi: GeographyApi,
  private val riskAndFundersApi: RiskAndFundersApi,
  private val categoryApi: MasterDataCategoryApi,
  private val lookupsApi: LookupsApi,
  private val itemMasterAndTrainingApi: ItemMasterAndTrainingApi,
  private val applicationParameterApi: ApplicationParameterApi,
  private val mockUnreadyEntities: MockUnreadyMasterDataEntities,
) : MasterDataRepository {

  private companion object {
    const val MOCK_DOWNLOAD_DELAY_MS = 1200L
    const val MOCK_RECORD_COUNT = 12
  }

  /** Parent geography-unit ids from the level downloaded just before the current one. Reset every
   * time [MasterDataEntity.STATE] downloads, since that's always the first row in a chain. */
  private var geographyParentIds: List<String> = emptyList()

  override suspend fun download(entity: MasterDataEntity): MasterDataResult {
    if (!entity.ready) {
      return if (mockUnreadyEntities.enabled) {
        delay(MOCK_DOWNLOAD_DELAY_MS)
        MasterDataResult.Success(MOCK_RECORD_COUNT)
      } else {
        MasterDataResult.NotAvailable
      }
    }

    return runCatching {
      when (entity) {
        MasterDataEntity.STATE -> downloadGeographyRoots()
        MasterDataEntity.DISTRICT -> downloadGeographyLevel("DISTRICT")
        MasterDataEntity.BLOCK -> downloadGeographyLevel("BLOCK")
        MasterDataEntity.PHC -> downloadGeographyLevel("PHC")
        MasterDataEntity.SUB_CENTER -> downloadGeographyLevel("SUBCENTRE")
        // Village is the last level anything descends from, so its own call doesn't need to
        // advance the cache — leaving geographyParentIds pointed at the sub-centers both this row
        // and Village List query from. No separate "village list" endpoint exists; one
        // GeographyUnit row per village already covers both rows in the reference app's list, so
        // Village List simply re-downloads the same level.
        MasterDataEntity.VILLAGE -> downloadGeographyLevel("VILLAGE", advanceCache = false)
        MasterDataEntity.VILLAGE_LIST -> downloadGeographyLevel("VILLAGE", advanceCache = false)
        MasterDataEntity.PROJECT_GEOGRAPHY -> downloadAllProjectGeography()
        MasterDataEntity.FUNDERS -> downloadFunders()
        MasterDataEntity.PROJECTS -> projectsRepository.getProjects().size
        MasterDataEntity.SAKHI -> downloadAllSakhis()
        MasterDataEntity.RISK_CATEGORY -> downloadCategory { lookupsApi.getCategory("RISK_GRADE") }
        MasterDataEntity.RISK -> downloadRiskConditions()
        MasterDataEntity.RISK_LANGUAGE -> downloadCategory { categoryApi.getRiskLanguages() }
        MasterDataEntity.RISK_PARAMETER -> downloadRiskParameters()
        MasterDataEntity.RISK_TYPE -> downloadCategory { categoryApi.getRiskTypes() }
        MasterDataEntity.VISIT_CATEGORY -> downloadCategory { categoryApi.getVisitCategories() }
        MasterDataEntity.ITEM_CATEGORY -> downloadCategory { categoryApi.getItemCategories() }
        MasterDataEntity.UOM_LIST -> downloadCategory { categoryApi.getUomList() }
        MasterDataEntity.ITEM_MASTER_LIST -> downloadItemMasterList()
        MasterDataEntity.VISIT_MASTER -> downloadVisitMasters()
        MasterDataEntity.TRANSACTION_TYPE -> downloadCategory { categoryApi.getTransactionTypes() }
        MasterDataEntity.TRAINING_TOPIC_MASTER -> downloadTrainingTopics()
        MasterDataEntity.GATHERING_STATUS -> downloadCategory { categoryApi.getGatheringStatuses() }
        MasterDataEntity.GATHERING_TYPES -> downloadCategory { categoryApi.getGatheringTypes() }
        MasterDataEntity.DDL_ITEM -> downloadDdlItems()
        MasterDataEntity.APPLICATION_PARAMETER -> downloadApplicationParameters()
        else -> error("$entity is marked ready but has no download case wired")
      }
    }.fold(
      onSuccess = { count -> if (count > 0) MasterDataResult.Success(count) else MasterDataResult.Empty },
      onFailure = { cause ->
        // No PII/tokens/response bodies — just enough to tell which entity failed and why,
        // safe to leave enabled in release (unlike HttpLoggingInterceptor, which stays NONE).
        Log.w("MasterDataDownload", "$entity failed: ${cause.javaClass.simpleName}: ${cause.message}")
        MasterDataResult.Failure(cause)
      },
    )
  }

  /** No single "all Sakhis" endpoint exists — roster is fetched per project and summed. */
  private suspend fun downloadAllSakhis(): Int =
    projectsRepository.getProjects().sumOf { project -> projectsRepository.getSakhis(project.id).size }

  /** [MasterDataEntity.STATE] is always the first row downloaded, so it's the signal to start a
   * fresh geography traversal — clearing any parent ids left over from an earlier run/retry. */
  private suspend fun downloadGeographyRoots(): Int {
    val response = geographyApi.getRoots()
    if (!response.isSuccessful) error("Failed to load geography roots: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty geography roots response")
    if (!body.success) error(body.message ?: "Failed to load geography roots")
    val roots = body.data.orEmpty()
    geographyParentIds = roots.map { it.geographyUnitId }
    return roots.size
  }

  /** Fetches every unit at [geoType] under each id in [geographyParentIds], then — unless
   * [advanceCache] is false — replaces [geographyParentIds] with the ids just fetched, so the next
   * enum entry's call descends one level further. */
  private suspend fun downloadGeographyLevel(geoType: String, advanceCache: Boolean = true): Int {
    val units = geographyParentIds.flatMap { parentId ->
      val response = geographyApi.getUnits(geoType = geoType, parentId = parentId)
      if (!response.isSuccessful) error("Failed to load $geoType: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty $geoType response")
      if (!body.success) error(body.message ?: "Failed to load $geoType")
      body.data.orEmpty()
    }
    if (advanceCache) geographyParentIds = units.map { it.geographyUnitId }
    return units.size
  }

  private suspend fun downloadFunders(): Int {
    val response = riskAndFundersApi.getFunders()
    if (!response.isSuccessful) error("Failed to load funders: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty funders response")
    if (!body.success) error(body.message ?: "Failed to load funders")
    return body.data.orEmpty().size
  }

  private suspend fun downloadRiskConditions(): Int {
    val response = riskAndFundersApi.getRiskConditions()
    if (!response.isSuccessful) error("Failed to load risk conditions: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty risk conditions response")
    if (!body.success) error(body.message ?: "Failed to load risk conditions")
    return body.data.orEmpty().size
  }

  /** Shared by every single-category master-data endpoint (Risk Category/Type/Language, Visit
   * Category, Item Category, UOM List, Transaction Type, Gathering Status/Types) — each returns
   * one [LookupCategoryDto], and the row's "record count" is that category's value count. */
  private suspend fun downloadCategory(call: suspend () -> Response<LookupCategoryEnvelopeDto>): Int {
    val response = call()
    if (!response.isSuccessful) error("Failed to load category: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty category response")
    if (!body.success) error(body.message ?: "Failed to load category")
    return body.data?.values?.size ?: 0
  }

  private suspend fun downloadDdlItems(): Int {
    val response = categoryApi.getDdlItems()
    if (!response.isSuccessful) error("Failed to load DDL items: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty DDL items response")
    if (!body.success) error(body.message ?: "Failed to load DDL items")
    return body.data.orEmpty().sumOf { it.values.size }
  }

  private suspend fun downloadItemMasterList(): Int {
    val response = itemMasterAndTrainingApi.getItemMasterList()
    if (!response.isSuccessful) error("Failed to load item master list: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty item master list response")
    if (!body.success) error(body.message ?: "Failed to load item master list")
    return body.data.orEmpty().size
  }

  private suspend fun downloadTrainingTopics(): Int {
    val response = itemMasterAndTrainingApi.getTrainingTopics()
    if (!response.isSuccessful) error("Failed to load training topics: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty training topics response")
    if (!body.success) error(body.message ?: "Failed to load training topics")
    return body.data.orEmpty().size
  }

  private suspend fun downloadRiskParameters(): Int {
    val response = riskAndFundersApi.getRiskParameters()
    if (!response.isSuccessful) error("Failed to load risk parameters: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty risk parameters response")
    if (!body.success) error(body.message ?: "Failed to load risk parameters")
    return body.data.orEmpty().size
  }

  private suspend fun downloadVisitMasters(): Int {
    val response = riskAndFundersApi.getVisitMasters()
    if (!response.isSuccessful) error("Failed to load visit masters: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty visit masters response")
    if (!body.success) error(body.message ?: "Failed to load visit masters")
    return body.data.orEmpty().size
  }

  private suspend fun downloadApplicationParameters(): Int {
    val response = applicationParameterApi.getApplicationParameters()
    if (!response.isSuccessful) error("Failed to load application parameters: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty application parameters response")
    if (!body.success) error(body.message ?: "Failed to load application parameters")
    return body.data.orEmpty().size
  }

  /** No single "all project geography" endpoint exists — the mapping is fetched per project and
   * summed, same pattern as [downloadAllSakhis]. */
  private suspend fun downloadAllProjectGeography(): Int =
    projectsRepository.getProjects().sumOf { project ->
      val response = geographyApi.getProjectGeography(project.id)
      if (!response.isSuccessful) error("Failed to load project geography: HTTP ${response.code()}")
      val body = response.body() ?: error("Empty project geography response")
      if (!body.success) error(body.message ?: "Failed to load project geography")
      body.data.orEmpty().size
    }
}
