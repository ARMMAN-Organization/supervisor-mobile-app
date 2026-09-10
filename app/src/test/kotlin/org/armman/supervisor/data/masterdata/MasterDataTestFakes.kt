package org.armman.supervisor.data.masterdata

import org.armman.supervisor.data.lookups.LookupCategoryDto
import org.armman.supervisor.data.lookups.LookupCategoryEnvelopeDto
import org.armman.supervisor.data.lookups.LookupValueDto
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import retrofit2.Response

/** Shared fakes for [MasterDataRepositoryImpl] tests, split by domain across
 * MasterDataRepositoryImplGeographyTest / ProjectsAndSakhiTest / RiskAndFundersTest /
 * CategoriesTest / ItemMasterAndTrainingTest / CoverageTest — kept in one file since every test
 * class needs the same [MasterDataRepositoryImpl] constructor shape even when it only exercises
 * one or two of these fakes. */

internal class FakeProjectsRepository : ProjectsRepository {
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

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> =
    throw UnsupportedOperationException("not used by these tests")

  override fun clearCache() = Unit
}

internal fun geographyUnit(id: String, parentId: String?, geoType: String, name: String) =
  GeographyUnitDto(geographyUnitId = id, parentId = parentId, geoType = geoType, geoCode = null, name = name, status = "ACTIVE")

/** By default resolves every geography level from a small fixed 1-state/1-district/1-block tree,
 * so DISTRICT/BLOCK/etc. each return exactly one row for the one parent id passed in. */
internal class FakeGeographyApi : GeographyApi {
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

internal class FakeRiskAndFundersApi : RiskAndFundersApi {
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

internal class FakeApplicationParameterApi : ApplicationParameterApi {
  var params = listOf(ApplicationParameterDto("param-1", "SYNC_INTERVAL_MINUTES", "15", "test", true))
  var failing = false

  override suspend fun getApplicationParameters(): Response<ApplicationParametersEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(ApplicationParametersEnvelopeDto(success = true, message = "OK", data = params))
  }
}

internal fun category(code: String, valueCount: Int) = LookupCategoryDto(
  categoryCode = code,
  categoryName = code,
  values = List(valueCount) { i -> LookupValueDto(id = "$code-$i", valueCode = "VAL_$i", valueLabel = "Value $i") },
)

internal class FakeItemMasterAndTrainingApi : ItemMasterAndTrainingApi {
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

internal class FakeMasterDataCategoryApi : MasterDataCategoryApi {
  var riskCategories = category("RISK_GRADE", 3)
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

  override suspend fun getRiskCategories() = if (failing) error("fail") else envelope(riskCategories)
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

/** No-op logger for tests — records warnings instead of calling the real, [android.util.Log]-backed
 * [AndroidMasterDataLogger], so these tests don't depend on the module-wide
 * `testOptions.unitTests.isReturnDefaultValues` Gradle flag to be safe to call from a plain JVM
 * test. */
internal class FakeMasterDataLogger : MasterDataLogger {
  val warnings = mutableListOf<String>()

  override fun warn(message: String) {
    warnings += message
  }
}

/** Builds a [MasterDataRepositoryImpl] wired to fresh fakes, mirroring the production DI shape —
 * shared by every domain test class below so each only wires the fakes it actually exercises. */
internal fun testMasterDataRepository(
  projectsRepository: ProjectsRepository = FakeProjectsRepository(),
  geographyApi: GeographyApi = FakeGeographyApi(),
  riskAndFundersApi: RiskAndFundersApi = FakeRiskAndFundersApi(),
  categoryApi: MasterDataCategoryApi = FakeMasterDataCategoryApi(),
  itemMasterAndTrainingApi: ItemMasterAndTrainingApi = FakeItemMasterAndTrainingApi(),
  applicationParameterApi: ApplicationParameterApi = FakeApplicationParameterApi(),
  mockUnreadyEntities: Boolean = false,
  logger: MasterDataLogger = FakeMasterDataLogger(),
): MasterDataRepositoryImpl = MasterDataRepositoryImpl(
  projectsRepository,
  geographyApi,
  riskAndFundersApi,
  categoryApi,
  itemMasterAndTrainingApi,
  applicationParameterApi,
  MockUnreadyMasterDataEntities(enabled = mockUnreadyEntities),
  logger,
)
