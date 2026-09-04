package com.example.dengbaoevidence.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = ClassicPrimaryDark,
  secondary = ClassicSecondaryDark,
  error = Color(0xFFFFB4AB),
  background = ClassicSurfaceDark,
  surface = ClassicSurfaceDark,
)

private val ClassicLightColorScheme = lightColorScheme(
  primary = ClassicPrimary,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFFF0ED),
  onPrimaryContainer = Color(0xFF7C251F),
  secondary = ClassicSecondary,
  secondaryContainer = Color(0xFFEAEAE7),
  onSecondaryContainer = Color(0xFF343431),
  error = ClassicError,
  background = ClassicBackground,
  surface = Color.White,
  onBackground = Color(0xFF252525),
  onSurface = Color(0xFF252525),
)

private val PaperBlueLightColorScheme = lightColorScheme(
  primary = PaperBluePrimary,
  onPrimary = Color.White,
  primaryContainer = PaperBlueSoft,
  onPrimaryContainer = PaperBlueDeep,
  secondary = PaperBlueSecondary,
  onSecondary = Color.White,
  secondaryContainer = PaperBlueSurfaceVariant,
  onSecondaryContainer = PaperBlueText,
  error = PaperBlueEvidenceColors.danger,
  errorContainer = PaperBlueEvidenceColors.dangerSoft,
  onErrorContainer = PaperBlueEvidenceColors.danger,
  background = PaperBlueBackground,
  onBackground = PaperBlueText,
  surface = PaperBlueSurface,
  onSurface = PaperBlueText,
  surfaceVariant = PaperBlueSurfaceVariant,
  onSurfaceVariant = PaperBlueMutedText,
  outline = PaperBlueOutline,
  outlineVariant = Color(0x33212340),
)

private val ClassicShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(18.dp),
)

@Composable
fun DengBaoEvidenceTheme(
  appearance: AppAppearance = AppAppearance.CLASSIC,
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val usePaperBlue = appearance == AppAppearance.PAPER_BLUE
  val colorScheme = when {
    darkTheme -> DarkColorScheme
    usePaperBlue -> PaperBlueLightColorScheme
    else -> ClassicLightColorScheme
  }
  CompositionLocalProvider(
    LocalEvidenceColors provides if (usePaperBlue) PaperBlueEvidenceColors else ClassicEvidenceColors,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = ClassicTypography,
      shapes = ClassicShapes,
      content = content,
    )
  }
}
