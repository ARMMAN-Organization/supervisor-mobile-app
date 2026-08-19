package org.armman.supervisor.ui.theme

import androidx.compose.ui.graphics.Color

// Brand + status colours from the Arogya Sakhi style guide.
val Primary = Color(0xFF014342)
val Secondary = Color(0xFF77E7B7)
val BackgroundLavender = Color(0xFFF1EDF9)

// Light surface for selected chips and highlight badges.
val PrimarySurface = Color(0xFFE3F5EE)
val RiskHigh = Color(0xFFD32F2F)
val RiskModerate = Color(0xFFF57C00)
val RiskMild = Color(0xFFFBC02D)
val RiskLow = Color(0xFF2E7D32)
val NeutralG10 = Color(0xFFF7F9FC)
val NeutralG50 = Color(0xFFE6E6E6)
val NeutralG75 = Color(0xFFB3B3B3)
val NeutralG100 = Color(0xFF999999)
val NeutralG200 = Color(0xFF656565)
val NeutralG400 = Color(0xFF333333)
val White = Color(0xFFFFFFFF)
val StatusSuccess = Color(0xFF2E7D32)

// Derived surface tint for success banners (StatusSuccess on a near-white wash).
val StatusSuccessSurface = Color(0xFFF1F8F2)

// Informational (blue) — used for the "Active" state chip on the beneficiary profile.
val Information = Color(0xFF1D79E5)
val InformationSurface = Color(0xFFE8F1FC)

// Light red wash for high-risk diagnosis chips / abnormal stat tiles.
val RiskHighSurface = Color(0xFFFCE9E9)

// Light red wash for error banners (e.g. login failure) — slightly lighter than RiskHighSurface.
val ErrorSurface = Color(0xFFFDF2F2)

// Soft shadow tint for cards/bars (black at ~15% opacity, per design shadows).
val ShadowTint = Color(0x26000000)

// Dashboard-only teal/green palette (screenshots, not yet in the shared purple/lavender system —
// confirm exact hex against Figma at the QA round). Also used for the Login screen
// header/heading/labels/button so both screens share the same brand green.
val DashboardHeaderGreen = Color(0xFF1E4B3E)
val DashboardKpiGreen = Color(0xFF4ADE80)
val DashboardPillNeutral = Color(0xFFC9D6E3)

// Download Master Data screen — per-card status colors, measured from the reference design.
// Reused by the Download Beneficiary Data screen, which shares the same row-status visuals.
val MasterDataPending = Color(0xFFE37D4A)
val MasterDataDownloading = Color(0xFFF08A50)
val MasterDataDownloadingTrack = Color(0xFFFFF3E5)
val MasterDataCompleted = Color(0xFF3FBE72)
val MasterDataEmpty = Color(0xFF8C9BAB)
