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

  /** Corner radius for [org.armman.supervisor.ui.components.StatTableCard]/[org.armman.supervisor.ui.components.StatCardChrome]
   * (Dashboard summary cards and their Risk/Visit/Monitoring/Registration screen equivalents) —
   * intentionally tighter than [CardRadius] per explicit request to reduce rounding on these cards. */
  val StatCardRadius = 10.dp

  /** Outer screen padding used specifically by Dashboard-style summary screens (Dashboard,
   * Risk/Visit/Monitoring Summary, Registrations) so their cards sit closer to the screen edge,
   * per explicit request. Other screens keep [ScreenPadding]. */
  val StatCardScreenPadding = 16.dp

  /** Internal chrome padding for [org.armman.supervisor.ui.components.StatCardChrome] — tighter
   * than [TilePadding] per explicit request to reduce whitespace inside Dashboard/summary cards. */
  val StatCardTilePadding = 12.dp

  /** Corner radius for compact inputs/banners (text fields, status banners). */
  val SmallRadius = 8.dp

  /** Inline icon size used alongside body/label text (e.g. status banner icons). */
  val InlineIconSize = 20.dp

  /** Registration card's small app-icon badge next to the mother/child count. */
  val RegistrationBadgeIconSize = 36.dp

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

  // Dashboard-screen tokens (measured from screenshots — confirm at QA).
  val IconButtonSize = 40.dp
  val QuickActionIconSize = 56.dp
  val TableRowHeight = 44.dp

  // Assign Item screens (measured from screenshots — confirm at QA).
  val DatePillRadius = 20.dp
  val DatePillPaddingH = 20.dp
  val DatePillPaddingV = 8.dp
  val QuantityFieldWidth = 96.dp
  val QuantityFieldHeight = 44.dp

  /** Corner radius for outlined input boxes (dropdowns, date fields, quantity fields). */
  val InputFieldRadius = 8.dp

  // Landscape-compact header tokens: shrink the header's vertical footprint when the
  // device is rotated to landscape, where screen height is scarce. Portrait is unaffected.
  val ScreenPaddingCompact = 12.dp
  val IconButtonSizeCompact = 32.dp

  /** Smallest base-4 step — tight vertical padding (e.g. inside a pill badge). */
  val TinySpacing = 4.dp

  /** Radio-button icon size (Choose Language dialog). */
  val RadioIconSize = 24.dp

  /** Hairline divider width — sub-base-4 by design (any visible divider must render below 4dp). */
  val HairlineWidth = 1.dp

  /** Thin sliding progress-bar height on the Download Master Data screen's row cards —
   * sub-base-4 by design, same intentional exception as [HairlineWidth] above. Reused by the
   * Download Beneficiary Data screen, which shares the same row-status visuals. */
  val MasterDataProgressBarHeight = 3.dp

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
