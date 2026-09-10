package org.armman.supervisor.ui.notifications

/** Abstraction over the Notifications backend, so the ViewModel depends on an interface rather
 * than a concrete Retrofit-backed implementation (DIP). */
interface NotificationsRepository {
  suspend fun getNotifications(): List<AppNotification>

  /** Marks [notificationId] as read. Throws on failure (e.g. a 403 when the notification does
   * not belong to the current user). */
  suspend fun markAsRead(notificationId: String)

  /** Count of notifications with [NotificationStatus.UNREAD], for the dashboard badge. */
  suspend fun getUnreadCount(): Int
}
