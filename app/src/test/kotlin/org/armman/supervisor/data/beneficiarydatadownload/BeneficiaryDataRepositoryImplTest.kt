package org.armman.supervisor.data.beneficiarydatadownload

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.beneficiarydatadownload.BeneficiaryDataEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeProjectsRepository : ProjectsRepository {
  var projects: List<LocationOption> = listOf(LocationOption("proj-1", "Test Project"))
  var sakhisByProject: Map<String, List<SakhiOption>> = mapOf(
    "proj-1" to listOf(SakhiOption("sakhi-1", "Sushil")),
  )

  override suspend fun getProjects(): List<LocationOption> = projects
  override suspend fun getSakhis(projectId: String): List<SakhiOption> = sakhisByProject[projectId].orEmpty()
  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = throw UnsupportedOperationException("not used")
  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = throw UnsupportedOperationException("not used")
  override suspend fun getSakhiProjectId(sakhiId: String): String = throw UnsupportedOperationException("not used")
  override fun clearCache() = Unit
}

private class FakeBeneficiaryDownloadListApi : BeneficiaryDownloadListApi {
  var beneficiaries = listOf(BeneficiaryDownloadCaseDto("ben-1", "MOTHER"))
  var inventoryTransactions = listOf(InventoryTransactionRowDto("txn-1", "sakhi-1", "item-1"))
  var transactionDetail: InventoryTransactionDetailDto? = InventoryTransactionDetailDto("txn-1", "item-1", 5)
  var sakhiCalls = listOf(SakhiCallDto("call-1", "sakhi-1", "COMPLETED"))
  var forbiddenTransactionIds: Set<String> = emptySet()
  var failing = false

  override suspend fun getBeneficiariesDownload(limit: Int, cursor: String?): Response<BeneficiaryDownloadEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(
      BeneficiaryDownloadEnvelopeDto(true, "OK", BeneficiaryDownloadPageDto(beneficiaries, null)),
    )
  }

  override suspend fun getAllInventoryTransactions(): Response<InventoryTransactionsListEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(InventoryTransactionsListEnvelopeDto(true, "OK", inventoryTransactions))
  }

  override suspend fun getInventoryTransactionDetails(id: String): Response<InventoryTransactionDetailEnvelopeDto> {
    if (failing) error("Simulated network failure")
    if (id in forbiddenTransactionIds) {
      return Response.error(
        403,
        "{\"success\":false,\"message\":\"You do not have access to this transaction.\"}"
          .toResponseBody("application/json".toMediaType()),
      )
    }
    return Response.success(InventoryTransactionDetailEnvelopeDto(true, "OK", transactionDetail))
  }

  override suspend fun getSakhiCalls(sakhiId: String): Response<SakhiCallsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(SakhiCallsEnvelopeDto(true, "OK", sakhiCalls))
  }
}

private class FakeSyncPendingApi : SyncPendingApi {
  var items = listOf(SyncPendingItemDto("item-1", "BENEFICIARY_CASE", "QUEUED"))
  var failing = false

  override suspend fun getPendingSyncItems(userId: String): Response<SyncPendingEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(SyncPendingEnvelopeDto(true, "OK", items))
  }
}

private class FakeGatheringDownloadApi : GatheringDownloadApi {
  var gatherings = listOf(GatheringDto("gathering-1", "event-1", "2026-01-01T00:00:00.000Z", "COMPLETED"))
  var trainingMarks = listOf(GatheringTrainingMarkDto("mark-1", "gathering-1", "sakhi-1", "PRE"))
  var images = GatheringImagesDto("gathering-1", "media-1", listOf(GatheringPhotoDto("photo-1", "media-2")))
  var failing = false

  override suspend fun getGatherings(sakhiId: String): Response<GatheringsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(GatheringsEnvelopeDto(true, "OK", gatherings))
  }

  override suspend fun getTrainingMarks(gatheringId: String): Response<GatheringTrainingMarksEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(GatheringTrainingMarksEnvelopeDto(true, "OK", trainingMarks))
  }

  override suspend fun getGatheringImages(gatheringId: String): Response<GatheringImagesEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(GatheringImagesEnvelopeDto(true, "OK", images))
  }
}

private class FakeBeneficiaryRiskApi : BeneficiaryRiskApi {
  var riskProfile = BeneficiaryRiskProfileDto(
    "ben-1",
    listOf(RiskStateSnapshotDto("snap-1", "ANC", "2026-01-01T00:00:00.000Z")),
    listOf(RiskAssessmentSummaryDto("assess-1", "2026-01-01T00:00:00.000Z")),
  )
  var referrals = listOf(RiskReferralDto("ref-1", "ben-1", "2026-01-01T00:00:00.000Z", "PENDING"))
  var referralDetails = RiskReferralDetailsDto(
    "ref-1",
    listOf(ReferralFollowupDto("followup-1")),
    listOf(ReferralTriggerSourceDto("trigger-1")),
  )
  var failing = false

  override suspend fun getBeneficiaryRisk(beneficiaryId: String): Response<BeneficiaryRiskEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(BeneficiaryRiskEnvelopeDto(true, "OK", riskProfile))
  }

  override suspend fun getRiskReferrals(beneficiaryId: String): Response<RiskReferralsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RiskReferralsEnvelopeDto(true, "OK", referrals))
  }

  override suspend fun getRiskReferralDetails(
    beneficiaryId: String,
    referralId: String,
  ): Response<RiskReferralDetailsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RiskReferralDetailsEnvelopeDto(true, "OK", referralDetails))
  }
}

private class FakeBeneficiaryVisitApi : BeneficiaryVisitApi {
  var visits = listOf(BeneficiaryVisitDto("visit-1", "ben-1", "2026-01-01T00:00:00.000Z"))
  var failing = false

  override suspend fun getBeneficiaryVisits(beneficiaryId: String): Response<BeneficiaryVisitsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(BeneficiaryVisitsEnvelopeDto(true, "OK", visits))
  }
}

private class FakeRiskMonitoringApi : RiskMonitoringApi {
  var monitoring = RiskMonitoringDto(total = 10, everAtRiskCount = 3, referralTriggerCount = 1)
  var failing = false

  override suspend fun getRiskMonitoring(): Response<RiskMonitoringEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RiskMonitoringEnvelopeDto(true, "OK", monitoring))
  }
}

private class FakeArogyaSakhiRosterApi : ArogyaSakhiRosterApi {
  var roster = listOf(ArogyaSakhiRosterEntryDto("roster-1", "sakhi-1", "Priya Sakhi"))
  var registrationTargets = listOf(RegistrationTargetDto("target-1", "sakhi-1"))
  var failing = false

  override suspend fun getArogyaSakhiRoster(projectId: String): Response<ArogyaSakhiRosterEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(ArogyaSakhiRosterEnvelopeDto(true, "OK", roster))
  }

  override suspend fun getRegistrationTargets(sakhiId: String): Response<RegistrationTargetsEnvelopeDto> {
    if (failing) error("Simulated network failure")
    return Response.success(RegistrationTargetsEnvelopeDto(true, "OK", registrationTargets))
  }
}

class BeneficiaryDataRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val beneficiaryDownloadListApi = FakeBeneficiaryDownloadListApi()
  private val syncPendingApi = FakeSyncPendingApi()
  private val gatheringDownloadApi = FakeGatheringDownloadApi()
  private val beneficiaryRiskApi = FakeBeneficiaryRiskApi()
  private val beneficiaryVisitApi = FakeBeneficiaryVisitApi()
  private val riskMonitoringApi = FakeRiskMonitoringApi()
  private val arogyaSakhiRosterApi = FakeArogyaSakhiRosterApi()

  private fun repository(mockUnreadyEntities: Boolean = false): BeneficiaryDataRepositoryImpl =
    BeneficiaryDataRepositoryImpl(
      projectsRepository,
      beneficiaryDownloadListApi,
      syncPendingApi,
      gatheringDownloadApi,
      beneficiaryRiskApi,
      beneficiaryVisitApi,
      riskMonitoringApi,
      arogyaSakhiRosterApi,
      MockUnreadyBeneficiaryDataEntities(enabled = mockUnreadyEntities),
    )

  // Regression test for the exact bug that hit MasterDataRepositoryImpl: a `ready = true` entity
  // with no matching `when` branch falls through to the `else -> error(...)` fallback, which
  // runCatching turns into a Failure that the screen shows as an unexplained "no internet"
  // dialog. This asserts every `ready` entity resolves to something other than that fallback.
  @Test
  fun `every ready entity resolves without falling through to the wired-branch error`() = runTest {
    val repo = repository()
    val readyEntities = BeneficiaryDataEntity.entries.filter { it.ready }

    for (entity in readyEntities) {
      val result = repo.download(entity)
      val isWiredBranchError = result is BeneficiaryDataResult.Failure &&
        result.cause.message?.contains("is marked ready but has no download case wired") == true
      assertTrue("$entity fell through to the unwired-branch error", !isWiredBranchError)
    }
  }

  @Test
  fun `downloading Arogya Sakhi sums roster entries across every project`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))

    val result = repository().download(BeneficiaryDataEntity.AROGYA_SAKHI)

    assertTrue(result is BeneficiaryDataResult.Success)
    // 1 roster entry per project * 2 projects
    assertEquals(2, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `an Arogya Sakhi roster failure is surfaced as Failure, not thrown`() = runTest {
    arogyaSakhiRosterApi.failing = true

    val result = repository().download(BeneficiaryDataEntity.AROGYA_SAKHI)

    assertTrue(result is BeneficiaryDataResult.Failure)
  }

  @Test
  fun `downloading Registration Target sums targets across every Sakhi`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.REGISTRATION_TARGET)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Registration Target with no targets returns Empty`() = runTest {
    arogyaSakhiRosterApi.registrationTargets = emptyList()

    val result = repository().download(BeneficiaryDataEntity.REGISTRATION_TARGET)

    assertEquals(BeneficiaryDataResult.Empty, result)
  }

  @Test
  fun `downloading Beneficiaries List returns Success with the item count`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.BENEFICIARIES_LIST)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Beneficiaries List with no items returns Empty`() = runTest {
    beneficiaryDownloadListApi.beneficiaries = emptyList()

    val result = repository().download(BeneficiaryDataEntity.BENEFICIARIES_LIST)

    assertEquals(BeneficiaryDataResult.Empty, result)
  }

  @Test
  fun `a beneficiary-download-list failure is surfaced as Failure, not thrown`() = runTest {
    beneficiaryDownloadListApi.failing = true

    val result = repository().download(BeneficiaryDataEntity.BENEFICIARIES_LIST)

    assertTrue(result is BeneficiaryDataResult.Failure)
  }

  @Test
  fun `downloading Beneficiary Risk sums current-state and assessment counts per beneficiary`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.BENEFICIARY_RISK)

    assertTrue(result is BeneficiaryDataResult.Success)
    // 1 beneficiary * (1 currentState + 1 assessment)
    assertEquals(2, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Beneficiary Visit sums visit counts per beneficiary`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.BENEFICIARY_VISIT)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Risk Monitoring returns Success with everAtRiskCount`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.RISK_MONITORING)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(3, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Sakhi Not Uploaded Data sums pending items across every Sakhi`() = runTest {
    projectsRepository.projects = listOf(LocationOption("proj-1", "P1"), LocationOption("proj-2", "P2"))
    projectsRepository.sakhisByProject = mapOf(
      "proj-1" to listOf(SakhiOption("s1", "A")),
      "proj-2" to listOf(SakhiOption("s2", "B")),
    )

    val result = repository().download(BeneficiaryDataEntity.SAKHI_NOT_UPLOADED_DATA)

    assertTrue(result is BeneficiaryDataResult.Success)
    // 1 pending item per Sakhi * 2 Sakhis
    assertEquals(2, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Sakhi Item Transaction returns Success with the transaction count`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.SAKHI_ITEM_TRANSACTION)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Sakhi Item Transaction Detail counts one detail per transaction`() = runTest {
    beneficiaryDownloadListApi.inventoryTransactions = listOf(
      InventoryTransactionRowDto("txn-1", "sakhi-1", "item-1"),
      InventoryTransactionRowDto("txn-2", "sakhi-1", "item-2"),
    )

    val result = repository().download(BeneficiaryDataEntity.SAKHI_ITEM_TRANSACTION_DETAIL)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(2, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `Sakhi Item Transaction Detail skips a transaction whose details 403, counting the rest`() = runTest {
    beneficiaryDownloadListApi.inventoryTransactions = listOf(
      InventoryTransactionRowDto("txn-1", "sakhi-1", "item-1"),
      InventoryTransactionRowDto("txn-2", "sakhi-1", "item-2"),
    )
    beneficiaryDownloadListApi.forbiddenTransactionIds = setOf("txn-2")

    val result = repository().download(BeneficiaryDataEntity.SAKHI_ITEM_TRANSACTION_DETAIL)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering List sums gatherings across every Sakhi`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.GATHERING_LIST)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering Attendance counts one per reachable gathering`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.GATHERING_ATTENDANCE)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering Training Marks sums marks per gathering`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.GATHERING_TRAINING_MARKS)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Gathering Images counts photos per gathering`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.GATHERING_IMAGES)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Call Details sums calls across every Sakhi`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.CALL_DETAILS)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Risk Referral Header sums referrals per beneficiary`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.BENEFICIARY_RISK_REFERRAL_HEADER)

    assertTrue(result is BeneficiaryDataResult.Success)
    assertEquals(1, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `downloading Risk Referral Details sums followups and trigger sources per referral`() = runTest {
    val result = repository().download(BeneficiaryDataEntity.BENEFICIARY_RISK_REFERRAL_DETAILS)

    assertTrue(result is BeneficiaryDataResult.Success)
    // 1 beneficiary -> 1 referral -> (1 followup + 1 triggerSource)
    assertEquals(2, (result as BeneficiaryDataResult.Success).recordCount)
  }

  @Test
  fun `a risk-referral failure is surfaced as Failure, not thrown`() = runTest {
    beneficiaryRiskApi.failing = true

    val result = repository().download(BeneficiaryDataEntity.BENEFICIARY_RISK_REFERRAL_HEADER)

    assertTrue(result is BeneficiaryDataResult.Failure)
  }

  @Test
  fun `a gathering-download failure is surfaced as Failure, not thrown`() = runTest {
    gatheringDownloadApi.failing = true

    val result = repository().download(BeneficiaryDataEntity.GATHERING_LIST)

    assertTrue(result is BeneficiaryDataResult.Failure)
  }
}
