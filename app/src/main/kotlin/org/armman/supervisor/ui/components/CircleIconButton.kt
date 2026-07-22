package org.armman.supervisor.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.NeutralG10

/** Circular icon button on a light surface — reusable wherever a header/toolbar needs a round icon action. */
@Composable
fun CircleIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backgroundColor: Color = NeutralG10,
  size: Dp = Dimens.IconButtonSize,
  icon: @Composable () -> Unit,
) {
  Surface(shape = CircleShape, color = backgroundColor, modifier = modifier.size(size)) {
    IconButton(onClick = onClick) { icon() }
  }
}
