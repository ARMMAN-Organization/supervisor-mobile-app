package org.armman.supervisor.data.masterdata

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupCategoryEnvelopeDto
import org.armman.supervisor.data.lookups.LookupValueDto
import org.armman.supervisor.data.lookups.LookupsApi
import org.armman.supervisor.data.lookups.LookupsEnvelopeDto
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.masterdata.MasterDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeProjectsRepository : ProjectsRepository {
  var projects: List<LocationOption> = listOf(LocationOption("proj-1", "Test Project"))
  var sakhisByProject: Map<String, List<SakhiOption>> = mapOf(
    "proj-1" to listOf(SakhiOption("sakhi-1", "Sushil")),
  )
  var failingProjects = false
  var failingSakhis = false

  override suspend fun getProjects(): List<LocationOption> {
    if (failingProjects) error("Simulated network failure")
    return projects
  }

  override suspend fun getSakhis(projectId: String): List<SakhiOption> {
    if (failingSakhis) error("Simulated network failure fetching Sakhis for $projectId")
    return sakhisByProject[projectId].orEmpty()
  }

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail =
    throw UnsupportedOperationException("not used by these tests")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption =
    throw UnsupportedOperationException("not used by these tests")

  override suspend fun getSakhiProjectId(sakhiId: String): String =
    throw UnsupportedOperationException("not used by these tests")

  override fun clearCache() = Unit
}

private fun geographyUnit(id: String, parentId: String?, geoType: String, name: String) =
  GeographyUnitDto(geographyUnitId = id, parentId = parentId, geoType = geoType, geoCode = null, name = name, status = "ACTIVE")

/** By default resolves every geography level from a small fixed 1-state/1-district/1-block tree,
 * so DISTRICT/BLOCK/etc. each return exactly one row for the one parent id passed in. */
private class FakeGeographyApi : GeographyApi {
  var roots = listOf(geographyUnit("state-1", null, "STATE", "Test State"))
  var unitsByParent: Map<String, List<GeographyUnitDto>> = mapOf(
    "state-1" to listOf(geographyUnit("district-1", "state-1", "DISTRICT", "Test District")),
  )
  var projectGeographyByProject: Map<String, List<ProjectGeographyLinkDto>> = mapOf(
    "proj-1" to listOf(ProjectGeographyLinkDto("link-1", "proj-1", "state-1", "2026-01-01T00:00:00.000Z", null)),
  )
  var failing = false

  override suspend fun getRoots(): Response<GeographyUnitsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(GeographyUnitsEnvelopeDto(success = true, message = "OK", data = roots))
  }

  override suspend fun getUnits(geoType: String, parentId: String): Response<GeographyUnitsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(
      GeographyUnitsEnvelopeDto(success = true, message = "OK", data = unitsByParent[parentId].orEmpty()),
    )
  }

  override suspend fun getProjectGeography(projectId: String): Response<ProjectGeographyEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(
      ProjectGeographyEnvelopeDto(success = true, message = "OK", data = projectGeographyByProject[projectId].orEmpty()),
    )
  }
}

private class FakeRiskAndFundersApi : RiskAndFundersApi {
  var riskConditions = listOf(
    RiskConditionDto("rc-1", "COND_A", "Condition A", "MOTHER", "REGISTRATION", "BINARY", false, false, "ACTIVE"),
  )
  var riskParameters = listOf(
    RiskParameterDto("rp-1", "PARAM_A", "Parameter A", "MOTHER", "mmHg", "NUMERIC", "ACTIVE"),
  )
  var funders = listOf(FunderDto("funder-1", "F1", "Test Funder", "ACTIVE"))
  var visitMasters = listOf(
    VisitMasterDto("vm-1", "ANC1", "ANC", "ANC Visit 1", "MOTHER", 1, "First visit", true),
  )
  var failing = false

  override suspend fun getRiskConditions(): Response<RiskConditionsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RiskConditionsEnvelopeDto(success = true, message = "OK", data = riskConditions))
  }

  override suspend fun getRiskParameters(): Response<RiskParametersEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RiskParametersEnvelopeDto(success = true, message = "OK", data = riskParameters))
  }

  override suspend fun getFunders(): Response<FundersEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(FundersEnvelopeDto(success = true, message = "OK", data = funders))
  }

  override suspend fun getVisitMasters(): Response<VisitMastersEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(VisitMastersEnvelopeDto(success = true, message = "OK", data = visitMasters))
  }
}

private class FakeApplicationParameterApi : ApplicationParameterApi {
  var params = listOf(ApplicationParameterDto("param-1", "SYNC_INTERVAL_MINUTES", "15", "test", true))
  var failing = false

  override suspend fun getApplicationParameters(): Response<ApplicationParametersEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(ApplicationParametersEnvelopeDto(success = true, message = "OK", data = params))
  }
}

private fun category(code: String, valueCount: Int) = LookupCategoryDto(
  categoryCode = code,
  categoryName = code,
  values = List(valueCount) { i -> LookupValueDto(id = "$code-$i", valueCode = "VAL_$i", valueLabel = "Value $i") },
)

private class FakeItemMasterAndTrainingApi : ItemMasterAndTrainingApi {
  var items = listOf(ItemMasterDto("item-1", "IT1", "Test Item", "CONSUMABLE", "PIECE", "ACTIVE"))
  var topics = listOf(TrainingTopicDto("topic-1", "T1", "Test Topic", "ACTIVE"))
  var failing = false

  override suspend fun getItemMasterList(): Response<ItemMasterListEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(ItemMasterListEnvelopeDto(success = true, message = "OK", data = items))
  }

  override suspend fun getTrainingTopics(): Response<TrainingTopicsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(TrainingTopicsEnvelopeDto(success = true, message = "OK", data = topics))
  }
}

/** Fakes the generic `/lookups` endpoints; [RISK_CATEGORY][MasterDataEntity.RISK_CATEGORY] downloads
 * via `getCategory("RISK_GRADE")` here rather than [FakeMasterDataCategoryApi]. */
private class FakeLookupsApi : LookupsApi {
  var riskGrade = category("RISK_GRADE", 3)
  var failing = false

  override suspend fun getLookups(): Response<LookupsEnvelopeDto> =
    throw UnsupportedOperationException("not used by these tests")

  override suspend fun getCategory(categoryCode: String): Response<LookupCategoryEnvelopeDto> {
    if (failing) error("fail")
    return Response.success(LookupCategoryEnvelopeDto(true, "OK", riskGrade))
  }
}

private class FakeMasterDataCategoryApi : MasterDataCategoryApi {
  var riskTypes = category("RISK_TYPE", 2)
  var riskLanguages = category("LANGUAGE", 2)
  var visitCategories = category("VISIT_CATEGORY", 4)
  var itemCategories = category("ITEM_CATEGORY", 2)
  var uomList = category("UOM", 5)
  var transactionTypes = category("TRANSACTION_TYPE", 3)
  var gatheringStatuses = category("GATHERING_STATUS", 3)
  var gatheringTypes = category("GATHERING_TYPE", 2)
  var ddlItems = listOf(category("APPROVAL_STATUS", 5), category("CASE_TYPE", 2))
  var failing = false

  private fun envelope(c: LookupCategoryDto) = Response.success(LookupCategoryEnvelopeDto(true, "OK", c))

  override suspend fun getRiskTypes() = if (failing) error("fail") else envelope(riskTypes)
  override suspend fun getRiskLanguages() = if (failing) error("fail") else envelope(riskLanguages)
  override suspend fun getVisitCategories() = if (failing) error("fail") else envelope(visitCategories)
  override suspend fun getItemCategories() = if (failing) error("fail") else envelope(itemCategories)
  override suspend fun getUomList() = if (failing) error("fail") else envelope(uomList)
  override suspend fun getTransactionTypes() = if (failing) error("fail") else envelope(transactionTypes)
  override suspend fun getGatheringStatuses() = if (failing) error("fail") else envelope(gatheringStatuses)
  override suspend fun getGatheringTypes() = if (failing) error("fail") else envelope(gatheringTypes)

  override suspend fun getDdlItems(): Response<LookupCategoryListEnvelopeDto> {
    if (failing) error("fail")
    return Response.success(LookupCategoryListEnvelopeDto(true, "OK", ddlItems))
  }
}

class MasterDataRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val geographyApi = FakeGeographyApi()
  private val riskAndFundersApi = FakeRiskAndFundersApi()
  private val categoryApi = FakeMasterDataCategoryApi()
  private val lookupsApi = FakeLookupsApi()
  private val itemMasterAndTrainingApi = FakeItemMasterAndTrainingApi()
  private val applicationParameterApi = FakeApplicationParameterApi()

  private fun repository(mockUnreadyEntities: Boolean = false): MasterDataRepositoryImpl =
    MasterDataRepositoryImpl(
      projectsRepository,
      geographyApi,
      riskAndFundersApi,
      categoryApi,
      lookupsApi,
      itemMasterAndTrainingApi,
      applicationParameterApi,
      MockUnreadyMasterDataEntities(enabled = mockUnreadyEntities),
    )

  // Regression test for a real bug: ITEM_MASTER_LIST and TRAINING_TOPIC_MASTER were flipped to
  // `ready = true` without a matching `when` branch in MasterDataRepositoryImpl, so they fell
  // through to the `else -> error(...)` branch — caught by runCatching and surfaced as a Failure
  // that the screen showed as a generic "no internet connection" dialog, hiding the real bug.
  // This asserts every `ready` entity resolves to something other than that fallback error by
  // checking a curated list of entities added after the initial wiring pass; a future ready-entity
  // added here without its own `when` branch will fail this the same way it failed in production.
  @Test
  fun `every ready entity used by the app resolves without falling through to the wired-branch error`() = runTest {
    val repo = repository()
    val readyEntities = MasterDataEntity.entries.filter { it.ready }

    for (entity in readyEntities) {
      val result = repo.download(entity)
      val isWiredBranchError = result is MasterDataResult.Failure &&
        result.cause.message?.contains("is marked ready but has no download case wired") == true
      assertTrue("$entity fell through to the unwired-branch error", !isWiredBranchError)
    }
  }

  @Test
  fun `an entity with no bulk backend endpoint returns NotAvailable when mocking is off`() = runTest {
    val result = repository().download(MasterDataEntity.INCENTIVE_RATE)

    assertEquals(MasterDataResult.NotAvailable, result)
  }

  @Test
  fun `an entity with no bulk backend endpoint mock-succeeds when mocking is on`() = runTest {
    val result = repository(mockUnreadyEntities = true).download(MasterDataEntity.INCENTIVE_RATE)

    assertTrue(result is MasterDataResult.Success)
  }

  @Test
  fun `downloading Risk Parameter returns Success with the parameter count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK_PARAMETER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Visit Master returns Success with the visit count`() = runTest {
    val result = repository().download(MasterDataEntity.VISIT_MASTER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Application Parameter returns Success with the param count`() = runTest {
    val result = repository().download(MasterDataEntity.APPLICATION_PARAMETER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Project Geography sums links across all projects`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))
    geographyApi.projectGeographyByProject = mapOf(
      "proj-1" to listOf(ProjectGeographyLinkDto("link-1", "proj-1", "state-1", "2026-01-01T00:00:00.000Z", null)),
      "proj-2" to listOf(
        ProjectGeographyLinkDto("link-2", "proj-2", "state-1", "2026-01-01T00:00:00.000Z", null),
        ProjectGeographyLinkDto("link-3", "proj-2", "state-2", "2026-01-01T00:00:00.000Z", null),
      ),
    )

    val result = repository().download(MasterDataEntity.PROJECT_GEOGRAPHY)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Projects returns Success with the project count`() = runTest {
    val result = repository().download(MasterDataEntity.PROJECTS)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Projects with an empty roster returns Empty`() = runTest {
    projectsRepository.projects = emptyList()

    val result = repository().download(MasterDataEntity.PROJECTS)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `downloading Sakhi sums rosters across all projects`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))
    projectsRepository.sakhisByProject = mapOf(
      "proj-1" to listOf(SakhiOption("s1", "A"), SakhiOption("s2", "B")),
      "proj-2" to listOf(SakhiOption("s3", "C")),
    )

    val result = repository().download(MasterDataEntity.SAKHI)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a Projects failure is surfaced as Failure, not thrown`() = runTest {
    projectsRepository.failingProjects = true

    val result = repository().download(MasterDataEntity.PROJECTS)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `a Sakhi-roster-specific failure is surfaced as Failure even when Projects itself succeeds`() = runTest {
    projectsRepository.failingSakhis = true

    val result = repository().download(MasterDataEntity.SAKHI)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `downloading State returns Success with the root count and resets the geography cache`() = runTest {
    val result = repository().download(MasterDataEntity.STATE)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading District after State descends using the roots just fetched`() = runTest {
    val repo = repository()
    repo.download(MasterDataEntity.STATE)

    val result = repo.download(MasterDataEntity.DISTRICT)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading District without a prior State call finds no parents and returns Empty`() = runTest {
    val result = repository().download(MasterDataEntity.DISTRICT)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `Village List re-downloads the same level as Village without advancing the cache`() = runTest {
    geographyApi.unitsByParent = mapOf(
      "state-1" to listOf(geographyUnit("village-1", "state-1", "VILLAGE", "Test Village")),
    )
    val repo = repository()
    repo.download(MasterDataEntity.STATE)

    val villageResult = repo.download(MasterDataEntity.VILLAGE)
    val villageListResult = repo.download(MasterDataEntity.VILLAGE_LIST)

    assertEquals((villageResult as MasterDataResult.Success).recordCount, (villageListResult as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a geography failure is surfaced as Failure, not thrown`() = runTest {
    geographyApi.failing = true

    val result = repository().download(MasterDataEntity.STATE)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `downloading Funders returns Success with the funder count`() = runTest {
    val result = repository().download(MasterDataEntity.FUNDERS)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Risk returns Success with the condition count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  // Confirmed with backend: /incentive-rates/active resolves one rate for a required rateType,
  // not a bulk list — there is no download-everything endpoint, so this stays NotAvailable.
  @Test
  fun `Incentive Rate stays NotAvailable since no bulk list endpoint exists`() = runTest {
    val result = repository().download(MasterDataEntity.INCENTIVE_RATE)

    assertEquals(MasterDataResult.NotAvailable, result)
  }

  @Test
  fun `a risk-and-funders failure is surfaced as Failure, not thrown`() = runTest {
    riskAndFundersApi.failing = true

    val result = repository().download(MasterDataEntity.FUNDERS)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `downloading Risk Category returns Success with the category's value count`() = runTest {
    val result = repository().download(MasterDataEntity.RISK_CATEGORY)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(3, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering Types returns Success with the category's value count`() = runTest {
    val result = repository().download(MasterDataEntity.GATHERING_TYPES)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(2, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `a category with no values returns Empty`() = runTest {
    lookupsApi.riskGrade = category("RISK_GRADE", 0)

    val result = repository().download(MasterDataEntity.RISK_CATEGORY)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `a category-endpoint failure is surfaced as Failure, not thrown`() = runTest {
    categoryApi.failing = true

    val result = repository().download(MasterDataEntity.VISIT_CATEGORY)

    assertTrue(result is MasterDataResult.Failure)
  }

  @Test
  fun `downloading DDL Item sums values across every returned category`() = runTest {
    val result = repository().download(MasterDataEntity.DDL_ITEM)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(7, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Item Master List returns Success with the item count`() = runTest {
    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Item Master List with no items returns Empty`() = runTest {
    itemMasterAndTrainingApi.items = emptyList()

    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertEquals(MasterDataResult.Empty, result)
  }

  @Test
  fun `downloading Training Topic Master returns Success with the topic count`() = runTest {
    val result = repository().download(MasterDataEntity.TRAINING_TOPIC_MASTER)

    assertTrue(result is MasterDataResult.Success)
    assertEquals(1, (result as MasterDataResult.Success).recordCount)
  }

  @Test
  fun `an item-master-and-training failure is surfaced as Failure, not thrown`() = runTest {
    itemMasterAndTrainingApi.failing = true

    val result = repository().download(MasterDataEntity.ITEM_MASTER_LIST)

    assertTrue(result is MasterDataResult.Failure)
  }
}
