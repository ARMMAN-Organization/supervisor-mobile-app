package org.armman.supervisor.data.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks which notification ids have already been surfaced (sound played) so a notification
 * only ever triggers the "new notification" sound once, regardless of read/unread status or how
 * many times the Dashboard polls afterward. An id set (not a timestamp watermark) because a tap
 * or the sound firing needs to permanently retire that exact notification, not just everything
 * created before some point in time. Plain (unencrypted) SharedPreferences — this isn't sensitive
 * data, unlike the auth secrets in `SecureKeyValueStore`, so the Keystore-backed store isn't
 * warranted here. */
interface NotificationsSeenStore {
  fun isSeen(notificationId: String): Boolean
  fun markSeen(notificationId: String)

  /** True until the very first [markSeen] call this store has ever made — distinct from
   * per-id tracking, so the repository can tell "nothing has ever run yet" (silently mark the
   * whole initial backlog seen, no sound storm) apart from "this specific id happens to be
   * unseen" (a real new arrival, worth a sound). */
  fun isFirstRun(): Boolean
}

private const val PREFS_FILE_NAME = "notifications_seen_prefs"
private const val KEY_SEEN_IDS = "seen_notification_ids"
private const val KEY_HAS_RUN_BEFORE = "has_run_before"

@Singleton
class SharedPreferencesNotificationsSeenStore @Inject constructor(
  @ApplicationContext private val context: Context,
) : NotificationsSeenStore {

  private val prefs = context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

  override fun isSeen(notificationId: String): Boolean =
    prefs.getStringSet(KEY_SEEN_IDS, emptySet())?.contains(notificationId) == true

  override fun markSeen(notificationId: String) {
    // SharedPreferences docs warn against mutating a Set instance returned by getStringSet and
    // writing it back — copy into a fresh mutable set first.
    val current = HashSet(prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty())
    current.add(notificationId)
    prefs.edit().putStringSet(KEY_SEEN_IDS, current).putBoolean(KEY_HAS_RUN_BEFORE, true).apply()
  }

  override fun isFirstRun(): Boolean = !prefs.getBoolean(KEY_HAS_RUN_BEFORE, false)
}
