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

/** Colored pill tagging a beneficiary card as Mother or Child (e.g. "Mother", "Deliver Child"). */
@Composable
fun BeneficiaryTypeTag(text: String, containerColor: Color, modifier: Modifier = Modifier) {
  Surface(color = containerColor, shape = RoundedCornerShape(Dimens.ChipHeight), modifier = modifier) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      color = White,
      modifier = Modifier.padding(horizontal = Dimens.SmallSpacing, vertical = Dimens.TinySpacing),
    )
  }
}
