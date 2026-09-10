package org.armman.supervisor.data.notifications

import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.armman.supervisor.ui.notifications.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

private class FakeNotificationsApi : NotificationsApi {
  var listResult: Response<NotificationListEnvelopeDto> =
    Response.success(NotificationListEnvelopeDto(success = true, message = "OK", data = emptyList()))
  var updateResult: Response<NotificationEnvelopeDto> =
    Response.success(NotificationEnvelopeDto(success = true, message = "OK", data = null))
  var lastUpdatedId: String? = null
  var lastUpdatedStatus: String? = null

  override suspend fun getNotifications(): Response<NotificationListEnvelopeDto> = listResult

  override suspend fun updateNotificationStatus(id: String, request: UpdateNotificationStatusRequestDto): Response<NotificationEnvelopeDto> {
    lastUpdatedId = id
    lastUpdatedStatus = request.status
    return updateResult
  }
}

private fun notificationDto(id: String = "n-1", status: String = "UNREAD") = NotificationDto(
  id = id,
  recipientUserId = "user-1",
  notificationType = "MISSED_VISIT_ESCALATION",
  title = "Closure form needed",
  body = "A beneficiary needs a closure form filled in.",
  priority = 5,
  ctaType = null,
  linkedEntityType = "EscalationEvent",
  linkedEntityId = "esc-1",
  status = status,
  readAt = null,
  dismissedAt = null,
  createdAt = "2026-08-25T12:24:40.664Z",
  updatedAt = "2026-08-25T12:24:40.664Z",
)

class NotificationsRepositoryImplTest {

  @Test
  fun `getNotifications maps dto list to domain models`() = runTest {
    val api = FakeNotificationsApi().apply {
      listResult = Response.success(
        NotificationListEnvelopeDto(success = true, message = "OK", data = listOf(notificationDto())),
      )
    }
    val repository = NotificationsRepositoryImpl(api)

    val result = repository.getNotifications()

    assertEquals(1, result.size)
    assertEquals("n-1", result[0].id)
    assertEquals("Closure form needed", result[0].title)
    assertEquals(NotificationStatus.UNREAD, result[0].status)
  }

  @Test
  fun `getNotifications on empty list returns empty result`() = runTest {
    val api = FakeNotificationsApi()
    val repository = NotificationsRepositoryImpl(api)

    val result = repository.getNotifications()

    assertTrue(result.isEmpty())
  }

  @Test(expected = IllegalStateException::class)
  fun `getNotifications on http error throws`() = runTest {
    val api = FakeNotificationsApi().apply {
      listResult = Response.error(500, "".toResponseBody(null))
    }
    val repository = NotificationsRepositoryImpl(api)

    repository.getNotifications()
  }

  @Test
  fun `markAsRead sends READ status for the given id`() = runTest {
    val api = FakeNotificationsApi()
    val repository = NotificationsRepositoryImpl(api)

    repository.markAsRead("n-1")

    assertEquals("n-1", api.lastUpdatedId)
    assertEquals("READ", api.lastUpdatedStatus)
  }

  @Test(expected = IllegalStateException::class)
  fun `markAsRead on forbidden response throws`() = runTest {
    val api = FakeNotificationsApi().apply {
      updateResult = Response.error(403, "".toResponseBody(null))
    }
    val repository = NotificationsRepositoryImpl(api)

    repository.markAsRead("n-1")
  }
}
