package org.armman.supervisor.ui.meetingtraining

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.armman.supervisor.ui.theme.Dimens

private const val THUMBNAIL_DP = 88

/**
 * Decodes a captured event photo from its local file path. No image-loading library is a
 * dependency of this app yet — event photos are locally-captured camera JPEGs (full sensor
 * resolution, several MB decoded), so [BitmapFactory.Options.inSampleSize] downsamples to
 * roughly the on-screen thumbnail size instead of decoding (and holding in memory) the full
 * image just to show it at [Dimens.AvatarSize] * 2.
 */
@Composable
fun EventPhotoThumbnail(filePath: String, modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  val bitmap = remember(filePath) {
    val targetPx = with(density) { THUMBNAIL_DP.dp.toPx() }.toInt()
    decodeSampledBitmap(filePath, targetPx)
  }
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(Dimens.AvatarSize * 2).clip(RoundedCornerShape(Dimens.SmallRadius)),
    )
  }
}

private fun decodeSampledBitmap(filePath: String, targetPx: Int): Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(filePath, bounds)
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

  val options = BitmapFactory.Options().apply {
    inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
  }
  return BitmapFactory.decodeFile(filePath, options)
}

/** Largest power-of-two sample size that keeps both dimensions at or above [targetPx] — matches
 * Android's own documented downsampling recipe (developer.android.com/topic/performance/graphics). */
private fun calculateInSampleSize(width: Int, height: Int, targetPx: Int): Int {
  var sampleSize = 1
  while (width / (sampleSize * 2) >= targetPx && height / (sampleSize * 2) >= targetPx) {
    sampleSize *= 2
  }
  return sampleSize
}
