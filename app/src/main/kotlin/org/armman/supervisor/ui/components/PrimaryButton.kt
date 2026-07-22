package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.armman.supervisor.ui.theme.Dimens

/** App-local primary action button following the style guide. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  loading: Boolean = false,
  enabled: Boolean = true,
  containerColor: Color = ButtonDefaults.buttonColors().containerColor,
) {
  Button(
    onClick = onClick,
    enabled = enabled && !loading,
    colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    modifier = modifier.fillMaxWidth().height(Dimens.ButtonHeight),
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(Dimens.SmallSpacing * 2),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
      )
    } else {
      Text(text)
    }
  }
}
