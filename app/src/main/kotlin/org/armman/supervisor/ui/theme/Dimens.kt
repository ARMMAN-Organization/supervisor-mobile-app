package org.armman.supervisor.ui.theme

import androidx.compose.ui.unit.dp

/** Standardized spacing and sizing tokens from the Arogya Sakhi style guide. */
object Dimens {
  val ScreenPadding = 24.dp
  val ItemSpacing = 16.dp
  val SmallSpacing = 8.dp
  val ExtraSmallSpacing = 4.dp
  val ButtonHeight = 48.dp
  val CardRadius = 16.dp
  val TileRadius = 12.dp
  val TilePadding = 20.dp
  val SheetRadius = 40.dp
  val PillButtonPaddingH = 16.dp

  /** Corner radius for compact inputs/banners (text fields, status banners). */
  val SmallRadius = 8.dp

  /** Inline icon size used alongside body/label text (e.g. status banner icons). */
  val InlineIconSize = 20.dp

  // Tablet button proportions (taller pill, wider inner padding).
  val ButtonHeightTablet = 48.dp
  val PillButtonPaddingHTablet = 48.dp
  val SearchBarWidthTablet = 320.dp

  /** Fixed content width of a pada-card count cell (centers as a block, left-aligns inside). */
  val PadaCountCellWidth = 150.dp

  /** Width breakpoint (dp) at or above which the tablet layout applies. */
  const val TabletMinWidthDp = 600

  // List-screen tokens measured from the My Beneficiaries designs (150dpi).
  val SearchBarHeight = 52.dp
  val ChipHeight = 40.dp
  val ChipSpacing = 12.dp
  val TabIndicatorHeight = 4.dp
  val CardAccentHeight = 6.dp
  val AvatarSize = 44.dp
  val SmallButtonHeight = 44.dp

  /** Hairline divider width — sub-base-4 by design (any visible divider must render below 4dp). */
  val HairlineWidth = 1.dp

  /** Caps and centers list-style content (e.g. Settings) on tablet instead of stretching full-bleed. */
  val ContentMaxWidthTablet = 600.dp

  // Login-screen tokens mirrored from the shared design spec (activity_login.xml).
  val LoginHeaderHeight = 400.dp
  val LoginLogoOuterSize = 150.dp
  val LoginLogoInnerSize = 120.dp
  val LoginCardMargin = 15.dp
  val LoginCardOverlap = 50.dp
  val LoginCardRadius = 15.dp
  val LoginCardPadding = 20.dp
  val LoginButtonMarginH = 60.dp
}
