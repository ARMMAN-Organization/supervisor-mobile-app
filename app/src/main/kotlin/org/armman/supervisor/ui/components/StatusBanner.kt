package org.armman.supervisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.armman.supervisor.ui.theme.Dimens
import org.armman.supervisor.ui.theme.ErrorSurface
import org.armman.supervisor.ui.theme.RiskHigh
import org.armman.supervisor.ui.theme.StatusSuccess
import org.armman.supervisor.ui.theme.StatusSuccessSurface

/** Banner variants from the style guide's toast/notification set. */
enum class StatusBannerVariant(
  internal val contentColor: Color,
  internal val containerColor: Color,
  internal val icon: ImageVector,
) {
  Success(StatusSuccess, StatusSuccessSurface, Icons.Filled.CheckCircle),
  Error(RiskHigh, ErrorSurface, Icons.Filled.Warning),
}

/** Inline status banner (e.g. "You have logged out successfully"). */
@Composable
fun StatusBanner(
  message: String,
  variant: StatusBannerVariant,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .fillMaxWidth()
      .border(Dimens.HairlineWidth, variant.contentColor, RoundedCornerShape(Dimens.SmallRadius))
      .background(variant.containerColor, RoundedCornerShape(Dimens.SmallRadius))
      .padding(horizontal = Dimens.ChipSpacing, vertical = Dimens.ChipSpacing),
  ) {
    Icon(
      imageVector = variant.icon,
      contentDescription = null,
      tint = variant.contentColor,
      modifier = Modifier.size(Dimens.InlineIconSize),
    )
    Text(
      text = message,
      style = MaterialTheme.typography.labelLarge,
      color = variant.contentColor,
      modifier = Modifier.padding(start = Dimens.SmallSpacing),
    )
  }
}
