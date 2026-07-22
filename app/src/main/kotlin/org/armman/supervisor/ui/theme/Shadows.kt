package org.armman.supervisor.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * App-wide soft box shadow for cards, sheets and bars — single source of truth;
 * never draw shadows directly in screens/components.
 *
 * Uses a native blurred shadow layer instead of Material elevation because
 * elevation shadows render too faintly to match the design's diffuse shadows.
 *
 * Built via [drawWithCache]: the [Paint] and its shadow layer are allocated once and reused
 * across frames, re-built only when this element's size changes — not on every draw call. A
 * plain `drawBehind` here would reallocate the Paint and reconfigure the (software-rendered)
 * shadow layer on every single frame, which showed up as janky scroll frames on-device with
 * multiple shadowed cards on one screen (see Dashboard scroll profiling).
 */
fun Modifier.softShadow(
  cornerRadius: Dp = 0.dp,
  blur: Dp = 20.dp,
  offsetY: Dp = 4.dp,
): Modifier = drawWithCache {
  val paint = Paint().apply {
    asFrameworkPaint().apply {
      color = android.graphics.Color.TRANSPARENT
      setShadowLayer(blur.toPx(), 0f, offsetY.toPx(), ShadowTint.toArgb())
    }
  }
  val cornerRadiusPx = cornerRadius.toPx()

  onDrawBehind {
    drawIntoCanvas { canvas ->
      canvas.drawRoundRect(0f, 0f, size.width, size.height, cornerRadiusPx, cornerRadiusPx, paint)
    }
  }
}
