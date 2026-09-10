package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG200

/** Overall progress across every row on a download screen (Master Data, Beneficiary Data), shown
 * below the header and above the row list — a progress bar plus a "N of M downloaded" label, with
 * an accessibility label announcing the same as a completion state. [progressText] and
 * [progressContentDescription] are passed in fully-resolved (each screen has its own string
 * resource ids) rather than composed here, since the two screens' resources differ. */
@Composable
fun DownloadProgressHeader(
  doneCount: Int,
  totalCount: Int,
  progressText: String,
  progressContentDescription: String,
  progressColor: Color,
  progressTrackColor: Color,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SmallSpacing),
  ) {
    LinearProgressIndicator(
      progress = { if (totalCount == 0) 0f else doneCount / totalCount.toFloat() },
      color = progressColor,
      trackColor = progressTrackColor,
      modifier = Modifier
        .fillMaxWidth()
        .height(Dimens.MasterDataProgressBarHeight)
        .clearAndSetSemantics { contentDescription = progressContentDescription },
    )
    Text(
      text = progressText,
      style = MaterialTheme.typography.labelLarge,
      color = NeutralG200,
      modifier = Modifier.padding(top = Dimens.TinySpacing),
    )
  }
}
