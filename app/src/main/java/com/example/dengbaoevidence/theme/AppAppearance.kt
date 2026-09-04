package com.example.dengbaoevidence.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppAppearance(
  val storageValue: String,
  val title: String,
) {
  CLASSIC("classic", "珊瑚红"),
  PAPER_BLUE("paper_blue", "纸张蓝"),
  ;

  companion object {
    fun fromStorage(value: String?): AppAppearance =
      entries.firstOrNull { it.storageValue == value } ?: PAPER_BLUE
  }
}

@Immutable
data class EvidenceColors(
  val success: Color,
  val successSoft: Color,
  val danger: Color,
  val dangerSoft: Color,
  val warning: Color,
  val warningSoft: Color,
  val neutralSoft: Color,
  val keyScreenshot: Color,
  val keyScreenshotSoft: Color,
  val progressTrack: Color,
  val progressTrackSecondary: Color,
  val divider: Color,
  val photoBorder: Color,
  val navigationSelected: Color,
  val navigationUnselectedIcon: Color,
  val navigationUnselectedText: Color,
)

val ClassicEvidenceColors = EvidenceColors(
  success = Color(0xFF238957),
  successSoft = Color(0xFFE6F5EC),
  danger = Color(0xFFCC413B),
  dangerSoft = Color(0xFFFFEBEA),
  warning = Color(0xFFBC7624),
  warningSoft = Color(0xFFFFF1DD),
  neutralSoft = Color(0xFFEAEAE7),
  keyScreenshot = Color(0xFFE75D50),
  keyScreenshotSoft = Color(0xFFFFF0ED),
  progressTrack = Color(0xFFDCE2E6),
  progressTrackSecondary = Color(0xFFE2E7EA),
  divider = Color(0xFFE5E9EC),
  photoBorder = Color(0xFFD4DADF),
  navigationSelected = Color(0xFF252525),
  navigationUnselectedIcon = Color(0xFF9B9B95),
  navigationUnselectedText = Color(0xFF858581),
)

val PaperBlueEvidenceColors = EvidenceColors(
  success = Color(0xFF2E705D),
  successSoft = Color(0xFFE6F1EB),
  danger = Color(0xFFD92D3F),
  dangerSoft = Color(0xFFFFE7EA),
  warning = Color(0xFF94691F),
  warningSoft = Color(0xFFF6EDDC),
  neutralSoft = Color(0xFFE9E8E2),
  keyScreenshot = Color(0xFF2E43A8),
  keyScreenshotSoft = Color(0xFFE8ECF8),
  progressTrack = Color(0xFFE4E5E8),
  progressTrackSecondary = Color(0xFFE5E6E9),
  divider = Color(0x29212340),
  photoBorder = Color(0xFFBEC4D0),
  navigationSelected = Color(0xFF202D70),
  navigationUnselectedIcon = Color(0xFF858A98),
  navigationUnselectedText = Color(0xFF70727B),
)

val LocalEvidenceColors = staticCompositionLocalOf { ClassicEvidenceColors }
