package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.White

/** Small rounded date badge (e.g. "10 Oct 2025") — used as a section header above a transaction list. */
@Composable
fun DatePill(text: String, backgroundColor: Color, modifier: Modifier = Modifier) {
  Surface(color = backgroundColor, shape = RoundedCornerShape(Dimens.DatePillRadius), modifier = modifier) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = White,
      modifier = Modifier.padding(horizontal = Dimens.DatePillPaddingH, vertical = Dimens.DatePillPaddingV),
    )
  }
}
