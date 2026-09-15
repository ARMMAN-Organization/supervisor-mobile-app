package org.armman.supervisor.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200
import org.armman.supervisor.ui.theme.NeutralG400
import org.armman.supervisor.ui.theme.Primary
import org.armman.supervisor.ui.theme.PrimarySurface
import org.armman.supervisor.ui.theme.White
import org.armman.supervisor.ui.theme.softShadow

/** One notification row: an unread dot, title, body, and relative time. Unread notifications get
 * a highlighted surface; tapping marks the notification as read (if unread) and, for
 * notification types with a known destination screen, navigates there. */
@Composable
fun NotificationCard(
  notification: AppNotification,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isUnread = notification.status == NotificationStatus.UNREAD
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .softShadow(Dimens.CardRadius)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(Dimens.CardRadius),
    color = if (isUnread) PrimarySurface else White,
  ) {
    Row(modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = Dimens.SmallSpacing)) {
      if (isUnread) {
        UnreadDot(modifier = Modifier.padding(top = Dimens.TinySpacing, end = Dimens.SmallSpacing))
      }
      Column {
        Text(
          text = notification.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
          color = NeutralG400,
        )
        notification.body?.let { body ->
          Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = NeutralG200,
            maxLines = 2,
            modifier = Modifier.padding(top = Dimens.TinySpacing),
          )
        }
        Text(
          text = DateUtils.getRelativeTimeSpanString(
            notification.createdAtEpochMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
          ).toString(),
          style = MaterialTheme.typography.labelSmall,
          color = NeutralG200,
          modifier = Modifier.padding(top = Dimens.TinySpacing),
        )
      }
    }
  }
}

@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.size(Dimens.SmallSpacing),
    shape = CircleShape,
    color = Primary,
  ) {}
}
