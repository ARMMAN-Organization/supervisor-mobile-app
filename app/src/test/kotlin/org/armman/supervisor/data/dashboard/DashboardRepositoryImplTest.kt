package org.armman.supervisor.data.dashboard

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.auth.session.FakeSecureKeyValueStore
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.dashboard.SummaryRowLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> = listOf(LocationOption("loc-1", "Unrestricted Armman"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used")

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override fun clearCache() = Unit
}

private class FakeDashboardApi : DashboardApi {
  var registrationSummary = RegistrationSummaryDto(total = 2, motherCount = 1, childCount = 1)
  var riskSummary = RiskSummaryTotalsDto(
    total = 2,
    byGrade = mapOf("NORMAL" to 2),
    everAtRiskCount = 0,
    referralTriggerCount = 0,
  )
  var visitSummary = VisitSummaryTotalsDto(total = 0, byStatus = emptyMap())
  var failRegistration = false
  var failRisk = false
  var failVisit = false

  override suspend fun getRegistrationSummary(): Response<RegistrationSummaryEnvelopeDto> {
    if (failRegistration) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(RegistrationSummaryEnvelopeDto(success = true, message = "OK", data = registrationSummary))
  }

  override suspend fun getRiskSummary(): Response<RiskSummaryTotalsEnvelopeDto> {
    if (failRisk) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(RiskSummaryTotalsEnvelopeDto(success = true, message = "OK", data = riskSummary))
  }

  override suspend fun getVisitSummary(): Response<VisitSummaryTotalsEnvelopeDto> {
    if (failVisit) return Response.error(500, okhttp3.ResponseBody.create(null, ""))
    return Response.success(VisitSummaryTotalsEnvelopeDto(success = true, message = "OK", data = visitSummary))
  }
}

class DashboardRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val sessionStore = SessionStore(FakeSecureKeyValueStore())
  private val api = FakeDashboardApi()
  private val repository = DashboardRepositoryImpl(projectsRepository, sessionStore, api)

  @Test
  fun `getDashboard maps the three summary endpoints into DashboardData`() = runTest {
    api.visitSummary = VisitSummaryTotalsDto(total = 5, byStatus = mapOf("PENDING" to 2, "MISSED" to 1, "COMPLETED" to 2))

    val data = repository.getDashboard("loc-1")

    assertEquals(1, data.kpi.mother)
    assertEquals(1, data.kpi.child)
    assertEquals(2, data.kpi.dueVisit)
    assertEquals(0, data.kpi.monitor)
  }

  @Test
  fun `getDashboard resolves to all-zero rows when every endpoint is empty`() = runTest {
    api.registrationSummary = RegistrationSummaryDto(total = 0, motherCount = 0, childCount = 0)
    api.riskSummary = RiskSummaryTotalsDto(total = 0, byGrade = emptyMap(), everAtRiskCount = 0, referralTriggerCount = 0)
    api.visitSummary = VisitSummaryTotalsDto(total = 0, byStatus = emptyMap())

    val data = repository.getDashboard("loc-1")

    assertEquals(0, data.kpi.mother)
    assertEquals(0, data.kpi.child)
    assertEquals(0, data.kpi.dueVisit)
  }

  @Test
  fun `getDashboard defaults dueVisit to zero when byStatus has no PENDING key`() = runTest {
    api.visitSummary = VisitSummaryTotalsDto(total = 3, byStatus = mapOf("COMPLETED" to 3))

    assertEquals(0, repository.getDashboard("loc-1").kpi.dueVisit)
  }

  @Test
  fun `getDashboard throws when the registration summary call fails`() {
    api.failRegistration = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getDashboard("loc-1") } }
  }

  @Test
  fun `getDashboard throws when the risk summary call fails`() {
    api.failRisk = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getDashboard("loc-1") } }
  }

  @Test
  fun `getDashboard throws when the visit summary call fails`() {
    api.failVisit = true

    assertThrows(IllegalStateException::class.java) { runTest { repository.getDashboard("loc-1") } }
  }

  @Test
  fun `getDashboard always reports zero monitor, unsyncedCount and empty monitoringSummary-staleSakhis`() = runTest {
    val data = repository.getDashboard("loc-1")

    assertEquals(0, data.kpi.monitor)
    assertEquals(0, data.unsyncedCount)
    assertEquals(0, data.monitoringSummary.single { it.label == SummaryRowLabel.TOTAL }.motherValue)
    assertEquals(true, data.staleSakhis.isEmpty())
  }
}
