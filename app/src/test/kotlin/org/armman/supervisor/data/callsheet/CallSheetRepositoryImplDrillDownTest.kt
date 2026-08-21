package org.armman.supervisor.data.callsheet

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.calllog.CallLogApi
import org.armman.supervisor.data.calllog.CallLogEnvelopeDto
import org.armman.supervisor.data.calllog.CallLogsEnvelopeDto
import org.armman.supervisor.data.calllog.CallSheetStatsListEnvelopeDto
import org.armman.supervisor.data.calllog.CreateCallLogRequestDto
import org.armman.supervisor.data.calllog.UpdateCallLogRequestDto
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.callsheet.HighRiskType
import org.armman.supervisor.ui.callsheet.ReasonContext
import org.armman.supervisor.ui.callsheet.ReasonSubmission
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/** Covers the Call Sheet drill-down / Add Reason mock methods on [CallSheetRepositoryImpl] —
 * approved Step 3 test cases 1-15 (repository level). Neither dependency is exercised by these
 * methods, so both are minimal not-used stubs (mirrors [CallSheetRepositoryImplTest]'s
 * `FakeProjectsRepository` pattern). */
class CallSheetRepositoryImplDrillDownTest {
  private lateinit var repository: CallSheetRepositoryImpl

  @Before
  fun setUp() {
    repository = CallSheetRepositoryImpl(
      projectsRepository = object : ProjectsRepository {
        override suspend fun getProjects(): List<LocationOption> = error("not used")
        override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used")
        override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")
        override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")
        override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")
        override fun clearCache() = error("not used")
      },
      callLogApi = object : CallLogApi {
        override suspend fun createCallLog(request: CreateCallLogRequestDto): Response<CallLogEnvelopeDto> = error("not used")
        override suspend fun getCallLogsBySakhi(sakhiId: String): Response<CallLogsEnvelopeDto> = error("not used")
        override suspend fun updateCallLog(callLogId: String, request: UpdateCallLogRequestDto): Response<CallLogEnvelopeDto> = error("not used")
        override suspend fun getCallSheetStatsBatch(sakhiIds: String): Response<CallSheetStatsListEnvelopeDto> = error("not used")
      },
    )
  }

  @Test
  fun `getDueVisits returns populated items for a known sakhi`() = runTest {
    val items = repository.getDueVisits("sakhi-1")
    assertTrue(items.isNotEmpty())
    val item = items.first()
    assertTrue(item.beneficiaryName.isNotBlank())
    assertTrue(item.villageName.isNotBlank())
    assertTrue(item.uniqueId.isNotBlank())
  }

  @Test
  fun `getVisitsExpiringSoon returns empty list when none pending`() = runTest {
    assertTrue(repository.getVisitsExpiringSoon("sakhi-1").isEmpty())
  }

  @Test
  fun `getMissedVisits returns empty list when none pending`() = runTest {
    assertTrue(repository.getMissedVisits("sakhi-1").isEmpty())
  }

  @Test
  fun `getClosurePending returns populated items with non-negative overdue days`() = runTest {
    val items = repository.getClosurePending("sakhi-1")
    assertTrue(items.isNotEmpty())
    items.forEach { assertTrue(it.overdueDays >= 0) }
  }

  @Test
  fun `getHighRisk ANC and PNC return distinct filtered lists`() = runTest {
    val anc = repository.getHighRisk("sakhi-1", HighRiskType.ANC)
    val pnc = repository.getHighRisk("sakhi-1", HighRiskType.PNC)
    assertTrue(anc.isNotEmpty())
    assertTrue(pnc.isEmpty())
    anc.forEach {
      assertTrue(it.beneficiaryName.isNotBlank())
      assertTrue(it.riskName.isNotBlank())
    }
  }

  @Test
  fun `getLastSyncReason returns a recorded reason`() = runTest {
    val reason = repository.getLastSyncReason("sakhi-1")
    assertNotNull(reason)
    assertTrue(reason!!.syncDate.isNotBlank())
    assertTrue(reason.reason.isNotBlank())
  }

  @Test
  fun `submitReason succeeds for Closure context keyed by itemId`() = runTest {
    repository.submitReason(
      ReasonSubmission(context = ReasonContext.CLOSURE_PENDING, itemId = "item-1", sakhiId = null, reasonCode = "OTHERS", remark = "note"),
    )
  }

  @Test
  fun `submitReason succeeds for LastSync context keyed by sakhiId not itemId`() = runTest {
    repository.submitReason(
      ReasonSubmission(context = ReasonContext.LAST_SYNC, itemId = null, sakhiId = "sakhi-1", reasonCode = "FORGOT_TO_SYNC", remark = null),
    )
  }

  @Test
  fun `submitReason succeeds with blank remark for a still-mock context`() = runTest {
    repository.submitReason(
      ReasonSubmission(context = ReasonContext.CLOSURE_PENDING, itemId = "item-1", sakhiId = null, reasonCode = "OTHERS", remark = ""),
    )
  }

  @Test
  fun `submitReason throws when reasonCode is blank`() = runTest {
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking {
        repository.submitReason(
          ReasonSubmission(context = ReasonContext.CLOSURE_PENDING, itemId = "item-1", sakhiId = null, reasonCode = "", remark = null),
        )
      }
    }
  }
}
