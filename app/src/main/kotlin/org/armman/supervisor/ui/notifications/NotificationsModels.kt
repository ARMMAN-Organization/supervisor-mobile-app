package org.armman.supervisor.ui.notifications

import org.armman.supervisor.ui.navigation.Routes

/** Status values a notification can be in — mirrors backend's `NotificationStatus` enum. */
enum class NotificationStatus {
  UNREAD,
  READ,
  DISMISSED,
  EXPIRED,
}

/** A single in-app notification shown on the Notifications screen. [notificationType],
 * [linkedEntityType] and [linkedEntityId] drive navigation to the entity this notification is
 * about, where a target screen exists (see [NotificationNavigationTarget]). */
data class AppNotification(
  val id: String,
  val title: String,
  val body: String?,
  val createdAtEpochMillis: Long,
  val status: NotificationStatus,
  val notificationType: String,
  val linkedEntityType: String?,
  val linkedEntityId: String?,
)

/** A resolved navigation destination for a notification, when one exists. */
sealed interface NotificationNavigationTarget {
  data class MeetingDetail(val eventId: String) : NotificationNavigationTarget
  data class QuickResponseCard(val cardId: String) : NotificationNavigationTarget
}

/** Maps a notification to where tapping it should navigate, or `null` if no destination screen
 * exists yet for its type (in that case tapping only marks it read).
 * MEETING_REMINDER/MEETING_UPDATE/TRAINING_REMINDER/TRAINING_UPDATE resolve to
 * `Routes.MEETING_DETAIL`; MISSED_VISIT_ESCALATION/BENEFICIARY_TRANSFER_NOTICE with a
 * linkedEntityType of "EscalationEvent" resolve to the matching Quick Response card (confirmed
 * live: the same id is a valid `GET /quick-response/{cardId}` lookup).
 * SUPERVISOR_APPROVAL_REQUESTED (fired on submission for all 7 approval-request types — LMP
 * change, closure, referral incomplete, data restore, reopen, accompanied referral, transfer)
 * uses a different linkedEntityType, "QuickResponseCard", but the same id resolves via the same
 * `GET /quick-response/{cardId}` endpoint — one branch covers all 7 types since backend fires
 * this single notificationType regardless of which request type triggered it. Every other
 * notification type is deliberately left unresolved rather than guessing at a screen that
 * doesn't exist. */
fun AppNotification.resolveNavigationTarget(): NotificationNavigationTarget? {
  val entityId = linkedEntityId?.takeIf { it.isNotBlank() } ?: return null
  return when (notificationType) {
    "MEETING_REMINDER", "MEETING_UPDATE", "TRAINING_REMINDER", "TRAINING_UPDATE" ->
      NotificationNavigationTarget.MeetingDetail(entityId)
    "MISSED_VISIT_ESCALATION", "BENEFICIARY_TRANSFER_NOTICE" ->
      if (linkedEntityType == "EscalationEvent") NotificationNavigationTarget.QuickResponseCard(entityId) else null
    "SUPERVISOR_APPROVAL_REQUESTED" ->
      if (linkedEntityType == "QuickResponseCard") NotificationNavigationTarget.QuickResponseCard(entityId) else null
    else -> null
  }
}

/** Maps a resolved [NotificationNavigationTarget] to its concrete route string — shared by every
 * place that acts on a notification tap (the Notifications list, the Dashboard's banner stack) so
 * the route mapping itself lives in one place. Callers decide separately what to do when there's
 * no target (e.g. the Notifications list does nothing since it's already there; the Dashboard
 * falls back to `Routes.NOTIFICATIONS`), since that fallback differs by caller. */
fun NotificationNavigationTarget.toRoute(): String = when (this) {
  is NotificationNavigationTarget.MeetingDetail -> Routes.meetingDetail(eventId)
  is NotificationNavigationTarget.QuickResponseCard -> Routes.quickResponse(cardId)
}
