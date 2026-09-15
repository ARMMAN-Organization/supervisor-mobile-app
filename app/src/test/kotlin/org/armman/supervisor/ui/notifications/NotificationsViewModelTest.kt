package org.armman.supervisor.ui.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private class TestRepository(
    private val notifications: List<AppNotification> = emptyList(),
    private var shouldFailLoad: Boolean = false,
    private var markAsReadException: Exception? = null,
  ) : NotificationsRepository {
    var getNotificationsCallCount: Int = 0
      private set
    var lastMarkedReadId: String? = null
      private set

    fun failNextLoad(fail: Boolean) {
      shouldFailLoad = fail
    }

    fun failNextMarkAsReadWith(exception: Exception?) {
      markAsReadException = exception
    }

    override suspend fun getNotifications(): List<AppNotification> {
      getNotificationsCallCount++
      if (shouldFailLoad) error("load failed")
      return notifications
    }

    override suspend fun markAsRead(notificationId: String) {
      lastMarkedReadId = notificationId
      markAsReadException?.let { throw it }
    }

    override suspend fun getUnreadCount(): Int = notifications.count { it.status == NotificationStatus.UNREAD }
  }

  private fun notification(
    id: String,
    status: NotificationStatus = NotificationStatus.UNREAD,
    notificationType: String = "MISSED_VISIT_ESCALATION",
    linkedEntityType: String? = null,
    linkedEntityId: String? = null,
  ) = AppNotification(
    id = id,
    title = "Test notification",
    body = "Body text",
    createdAtEpochMillis = 1_000L,
    status = status,
    notificationType = notificationType,
    linkedEntityType = linkedEntityType,
    linkedEntityId = linkedEntityId,
  )

  @Test
  fun `initial state is loading`() = runTest {
    val viewModel = NotificationsViewModel(TestRepository())
    assertEquals(NotificationsUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `load success populates notifications`() = runTest {
    val repository = TestRepository(notifications = listOf(notification("n-1")))
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as NotificationsUiState.Success
    assertEquals(1, state.notifications.size)
    assertEquals("n-1", state.notifications[0].id)
  }

  @Test
  fun `load with empty list produces empty success state`() = runTest {
    val viewModel = NotificationsViewModel(TestRepository())
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as NotificationsUiState.Success
    assertTrue(state.notifications.isEmpty())
  }

  @Test
  fun `load failure produces error state`() = runTest {
    val repository = TestRepository(shouldFailLoad = true)
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is NotificationsUiState.Error)
  }

  @Test
  fun `onRetry reloads notifications`() = runTest {
    val repository = TestRepository(shouldFailLoad = true)
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()
    assertTrue(viewModel.uiState.value is NotificationsUiState.Error)

    repository.failNextLoad(false)
    viewModel.onRetry()
    dispatcher.scheduler.advanceUntilIdle()

    assertTrue(viewModel.uiState.value is NotificationsUiState.Success)
  }

  @Test
  fun `clicking unread notification marks it read and updates local state`() = runTest {
    val repository = TestRepository(notifications = listOf(notification("n-1")))
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onNotificationClick("n-1")
    dispatcher.scheduler.advanceUntilIdle()

    assertEquals("n-1", repository.lastMarkedReadId)
    val state = viewModel.uiState.value as NotificationsUiState.Success
    assertEquals(NotificationStatus.READ, state.notifications[0].status)
    assertNull(state.markingReadId)
  }

  @Test
  fun `clicking already-read notification does not call markAsRead`() = runTest {
    val repository = TestRepository(notifications = listOf(notification("n-1", status = NotificationStatus.READ)))
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onNotificationClick("n-1")
    dispatcher.scheduler.advanceUntilIdle()

    assertNull(repository.lastMarkedReadId)
  }

  @Test
  fun `markAsRead failure clears markingReadId without corrupting list`() = runTest {
    val repository = TestRepository(notifications = listOf(notification("n-1")))
    repository.failNextMarkAsReadWith(IllegalStateException("forbidden"))
    val viewModel = NotificationsViewModel(repository)
    dispatcher.scheduler.advanceUntilIdle()

    viewModel.onNotificationClick("n-1")
    dispatcher.scheduler.advanceUntilIdle()

    val state = viewModel.uiState.value as NotificationsUiState.Success
    assertNull(state.markingReadId)
    assertEquals(NotificationStatus.UNREAD, state.notifications[0].status)
  }

  @Test
  fun `resolveNavigationTarget maps meeting and training types to MeetingDetail`() {
    for (type in listOf("MEETING_REMINDER", "MEETING_UPDATE", "TRAINING_REMINDER", "TRAINING_UPDATE")) {
      val target = notification("n-1", notificationType = type, linkedEntityId = "event-1").resolveNavigationTarget()
      assertEquals(NotificationNavigationTarget.MeetingDetail("event-1"), target)
    }
  }

  @Test
  fun `resolveNavigationTarget returns null for unmapped types`() {
    val target = notification("n-1", notificationType = "LMP_CHANGE_UPDATE", linkedEntityId = "esc-1").resolveNavigationTarget()
    assertNull(target)
  }

  @Test
  fun `resolveNavigationTarget returns null when linkedEntityId is blank or missing`() {
    assertNull(notification("n-1", notificationType = "MEETING_REMINDER", linkedEntityId = null).resolveNavigationTarget())
    assertNull(notification("n-1", notificationType = "MEETING_REMINDER", linkedEntityId = "").resolveNavigationTarget())
  }

  @Test
  fun `resolveNavigationTarget maps escalation and transfer types with EscalationEvent linking to QuickResponseCard`() {
    for (type in listOf("MISSED_VISIT_ESCALATION", "BENEFICIARY_TRANSFER_NOTICE")) {
      val target = notification(
        "n-1",
        notificationType = type,
        linkedEntityType = "EscalationEvent",
        linkedEntityId = "card-1",
      ).resolveNavigationTarget()
      assertEquals(NotificationNavigationTarget.QuickResponseCard("card-1"), target)
    }
  }

  @Test
  fun `resolveNavigationTarget returns null for escalation type without EscalationEvent linkedEntityType`() {
    val target = notification(
      "n-1",
      notificationType = "MISSED_VISIT_ESCALATION",
      linkedEntityType = null,
      linkedEntityId = "card-1",
    ).resolveNavigationTarget()
    assertNull(target)
  }

  @Test
  fun `toRoute maps each target to its concrete route`() {
    assertEquals(
      "meeting_detail/event-1",
      NotificationNavigationTarget.MeetingDetail("event-1").toRoute(),
    )
    assertEquals(
      "quick_response?cardId=card-1",
      NotificationNavigationTarget.QuickResponseCard("card-1").toRoute(),
    )
  }

  @Test
  fun `resolveNavigationTarget maps SUPERVISOR_APPROVAL_REQUESTED with QuickResponseCard linking to QuickResponseCard`() {
    val target = notification(
      "n-1",
      notificationType = "SUPERVISOR_APPROVAL_REQUESTED",
      linkedEntityType = "QuickResponseCard",
      linkedEntityId = "card-2",
    ).resolveNavigationTarget()
    assertEquals(NotificationNavigationTarget.QuickResponseCard("card-2"), target)
  }

  @Test
  fun `resolveNavigationTarget returns null for SUPERVISOR_APPROVAL_REQUESTED without QuickResponseCard linkedEntityType`() {
    val target = notification(
      "n-1",
      notificationType = "SUPERVISOR_APPROVAL_REQUESTED",
      linkedEntityType = null,
      linkedEntityId = "card-2",
    ).resolveNavigationTarget()
    assertNull(target)
  }
}
