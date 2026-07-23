package org.armman.supervisor.ui.meetingtraining

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

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
