package org.armman.supervisor.ui.meetingtraining

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
import org.armman.supervisor.ui.theme.Dimens

/**
 * Decodes a captured event photo from its local file path. No image-loading library is a
 * dependency of this app yet — event photos are small, locally-captured JPEGs decoded once per
 * composition, so a direct [BitmapFactory] decode is simpler than adding one.
 */
@Composable
fun EventPhotoThumbnail(filePath: String, modifier: Modifier = Modifier) {
  val bitmap = remember(filePath) { BitmapFactory.decodeFile(filePath) }
  if (bitmap != null) {
    Image(
      bitmap = bitmap.asImageBitmap(),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(Dimens.AvatarSize * 2).clip(RoundedCornerShape(Dimens.SmallRadius)),
    )
  }
}
