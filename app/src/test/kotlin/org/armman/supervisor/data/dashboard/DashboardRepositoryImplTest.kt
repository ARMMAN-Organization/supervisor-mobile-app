package org.armman.supervisor.data.dashboard

import kotlinx.coroutines.test.runTest
import org.armman.supervisor.data.auth.session.FakeSecureKeyValueStore
import org.armman.supervisor.data.auth.session.SessionStore
import org.armman.supervisor.data.projects.ProjectsRepository
import org.armman.supervisor.model.LocationOption
import org.armman.supervisor.ui.assignitem.SakhiDetail
import org.armman.supervisor.ui.assignitem.SakhiOption
import org.armman.supervisor.ui.dashboard.SummaryRowLabel
import org.armman.supervisor.data.notifications.NotificationsSeenStore
import org.armman.supervisor.ui.notifications.AppNotification
import org.armman.supervisor.ui.notifications.NotificationStatus
import org.armman.supervisor.ui.notifications.NotificationsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeProjectsRepository : ProjectsRepository {
  override suspend fun getProjects(): List<LocationOption> = listOf(LocationOption("loc-1", "Unrestricted Armman"))

  override suspend fun getSakhis(projectId: String): List<SakhiOption> = error("not used")

  override suspend fun getSakhiDetail(sakhiId: String): SakhiDetail = error("not used")

  override suspend fun getSakhiOption(sakhiId: String): SakhiOption = error("not used")

  override suspend fun getSakhiProjectId(sakhiId: String): String = error("not used")

  override suspend fun getMySakhiIds(projectId: String, supervisorUserId: String): Set<String> = error("not used")

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

private class FakeNotificationsRepository(
  private val notifications: List<AppNotification> = emptyList(),
  private var shouldFail: Boolean = false,
) : NotificationsRepository {
  override suspend fun getNotifications(): List<AppNotification> {
    if (shouldFail) error("load failed")
    return notifications
  }

  override suspend fun markAsRead(notificationId: String) = error("not used")

  override suspend fun getUnreadCount(): Int {
    if (shouldFail) error("load failed")
    return notifications.count { it.status == NotificationStatus.UNREAD }
  }
}

private class FakeNotificationsSeenStore(seenIds: Set<String> = emptySet(), hasRunBefore: Boolean = false) : NotificationsSeenStore {
  private val seen = HashSet(seenIds)
  private var everRun = hasRunBefore || seenIds.isNotEmpty()

  override fun isSeen(notificationId: String): Boolean = notificationId in seen

  override fun markSeen(notificationId: String) {
    seen.add(notificationId)
    everRun = true
  }

  override fun isFirstRun(): Boolean = !everRun
}

class DashboardRepositoryImplTest {
  private val projectsRepository = FakeProjectsRepository()
  private val sessionStore = SessionStore(FakeSecureKeyValueStore())
  private val api = FakeDashboardApi()
  private val notificationsRepository = FakeNotificationsRepository()
  private val notificationsSeenStore = FakeNotificationsSeenStore()
  private val repository = DashboardRepositoryImpl(
    projectsRepository,
    sessionStore,
    api,
    notificationsRepository,
    notificationsSeenStore,
  )

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
  fun `getDashboard always reports zero monitor and empty monitoringSummary-staleSakhis`() = runTest {
    val data = repository.getDashboard("loc-1")

    assertEquals(0, data.kpi.monitor)
    assertEquals(0, data.monitoringSummary.single { it.label == SummaryRowLabel.TOTAL }.motherValue)
    assertEquals(true, data.staleSakhis.isEmpty())
  }

  @Test
  fun `getDashboard reports unreadNotificationCount from NotificationsRepository`() = runTest {
    val notifications = listOf(
      AppNotification("n-1", "Title", null, 0L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
      AppNotification("n-2", "Title", null, 0L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
      AppNotification("n-3", "Title", null, 0L, NotificationStatus.READ, "MISSED_VISIT_ESCALATION", null, null),
    )
    val repositoryWithNotifications = DashboardRepositoryImpl(
      projectsRepository,
      sessionStore,
      api,
      FakeNotificationsRepository(notifications),
      FakeNotificationsSeenStore(),
    )

    val data = repositoryWithNotifications.getDashboard("loc-1")

    assertEquals(2, data.unreadNotificationCount)
  }

  @Test
  fun `getDashboard degrades unreadNotificationCount to zero when notifications call fails`() = runTest {
    val repositoryWithFailingNotifications = DashboardRepositoryImpl(
      projectsRepository,
      sessionStore,
      api,
      FakeNotificationsRepository(shouldFail = true),
      FakeNotificationsSeenStore(),
    )

    val data = repositoryWithFailingNotifications.getDashboard("loc-1")

    assertEquals(0, data.unreadNotificationCount)
  }

  @Test
  fun `getDashboard reports empty newlyDetectedNotifications and silently marks the backlog seen on first-ever call`() = runTest {
    val notifications = listOf(
      AppNotification("n-1", "Title", null, 1_000L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
      AppNotification("n-2", "Title", null, 2_000L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
    )
    val seenStore = FakeNotificationsSeenStore()
    val repositoryFirstLoad = DashboardRepositoryImpl(
      projectsRepository,
      sessionStore,
      api,
      FakeNotificationsRepository(notifications),
      seenStore,
    )

    val data = repositoryFirstLoad.getDashboard("loc-1")

    assertTrue(data.newlyDetectedNotifications.isEmpty())
    assertTrue(seenStore.isSeen("n-1"))
    assertTrue(seenStore.isSeen("n-2"))
  }

  @Test
  fun `getDashboard reports notifications never marked seen before, and marks them seen`() = runTest {
    val notifications = listOf(
      AppNotification("n-1", "Title", null, 1_000L, NotificationStatus.READ, "MISSED_VISIT_ESCALATION", null, null),
      AppNotification("n-2", "Title", null, 2_000L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
      AppNotification("n-3", "Title", null, 3_000L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
    )
    val seenStore = FakeNotificationsSeenStore(seenIds = setOf("n-1"))
    val repositoryWithSomeSeen = DashboardRepositoryImpl(
      projectsRepository,
      sessionStore,
      api,
      FakeNotificationsRepository(notifications),
      seenStore,
    )

    val data = repositoryWithSomeSeen.getDashboard("loc-1")

    assertEquals(listOf("n-2", "n-3"), data.newlyDetectedNotifications.map { it.id })
    assertTrue(seenStore.isSeen("n-2"))
    assertTrue(seenStore.isSeen("n-3"))
  }

  @Test
  fun `getDashboard does not re-report a notification already marked seen`() = runTest {
    val notifications = listOf(
      AppNotification("n-1", "Title", null, 1_000L, NotificationStatus.UNREAD, "MISSED_VISIT_ESCALATION", null, null),
    )
    val seenStore = FakeNotificationsSeenStore(seenIds = setOf("n-1"))
    val repositoryUpToDate = DashboardRepositoryImpl(
      projectsRepository,
      sessionStore,
      api,
      FakeNotificationsRepository(notifications),
      seenStore,
    )

    val data = repositoryUpToDate.getDashboard("loc-1")

    assertTrue(data.newlyDetectedNotifications.isEmpty())
  }
}
