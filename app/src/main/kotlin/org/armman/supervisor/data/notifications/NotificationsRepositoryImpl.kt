package org.armman.supervisor.data.notifications

import org.armman.supervisor.ui.notifications.AppNotification
import org.armman.supervisor.ui.notifications.NotificationStatus
import org.armman.supervisor.ui.notifications.NotificationsRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val STATUS_READ = "READ"

/** Backed by notification-escalation-service's Notifications endpoints via [NotificationsApi]. */
@Singleton
class NotificationsRepositoryImpl @Inject constructor(
  private val api: NotificationsApi,
) : NotificationsRepository {

  override suspend fun getNotifications(): List<AppNotification> {
    val response = api.getNotifications()
    if (!response.isSuccessful) error("Failed to load notifications: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty notifications response")
    if (!body.success) error(body.message ?: "Failed to load notifications")
    return body.data.orEmpty().map { it.toAppNotification() }
  }

  override suspend fun getUnreadCount(): Int = getNotifications().count { it.status == NotificationStatus.UNREAD }

  override suspend fun markAsRead(notificationId: String) {
    val response = api.updateNotificationStatus(notificationId, UpdateNotificationStatusRequestDto(status = STATUS_READ))
    if (!response.isSuccessful) error("Failed to update notification: HTTP ${response.code()}")
    val body = response.body() ?: error("Empty update-notification response")
    if (!body.success) error(body.message ?: "Failed to update notification")
  }

  private fun NotificationDto.toAppNotification() = AppNotification(
    id = id,
    title = title,
    body = body,
    createdAtEpochMillis = Instant.parse(createdAt).toEpochMilli(),
    status = runCatching { NotificationStatus.valueOf(status) }.getOrDefault(NotificationStatus.UNREAD),
    notificationType = notificationType,
    linkedEntityType = linkedEntityType,
    linkedEntityId = linkedEntityId,
  )
}
