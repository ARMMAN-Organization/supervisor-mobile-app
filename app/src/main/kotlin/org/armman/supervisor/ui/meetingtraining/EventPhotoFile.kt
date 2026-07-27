package org.armman.supervisor.ui.meetingtraining

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a private app-storage file to capture an event photo into, and returns both its
 * absolute path (stored in Room) and its `content://` Uri (handed to the camera intent — a raw
 * `file://` Uri would throw `FileUriExposedException` on modern Android).
 */
fun createEventPhotoFile(context: Context, eventId: String): Pair<String, Uri> {
  val dir = File(context.filesDir, "event_photos").apply { mkdirs() }
  val file = File(dir, "${eventId}_${System.currentTimeMillis()}.jpg")
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  return file.absolutePath to uri
}

/** Deletes event photo files no longer referenced by any Room row. Abstracted so
 * [org.armman.supervisor.ui.meetingtraining.MeetingTrainingViewModel] can be unit-tested with a
 * fake, the same rationale as [org.armman.supervisor.data.connectivity.ConnectivityChecker]. */
interface EventPhotoCleanup {
  suspend fun deleteUnreferenced(referencedFilePaths: Set<String>)
}

@Singleton
class AndroidEventPhotoCleanup @Inject constructor(
  @ApplicationContext private val context: Context,
) : EventPhotoCleanup {
  /**
   * Deletes any file under `event_photos/` that isn't referenced by [referencedFilePaths]. Events
   * and their photo rows are never deleted (cancel is a soft status change), so this only clears
   * genuinely orphaned files — e.g. a photo captured to disk but never [MeetingTrainingRepository
   * .addPhoto]-ed (camera intent cancelled/crashed mid-capture) — never a photo that's still part
   * of an event's record. Safe to call opportunistically (e.g. on screen load); silently skips a
   * missing directory.
   */
  override suspend fun deleteUnreferenced(referencedFilePaths: Set<String>) {
    val dir = File(context.filesDir, "event_photos")
    val files = dir.listFiles() ?: return
    for (file in files) {
      if (file.absolutePath !in referencedFilePaths) {
        file.delete()
      }
    }
  }
}
