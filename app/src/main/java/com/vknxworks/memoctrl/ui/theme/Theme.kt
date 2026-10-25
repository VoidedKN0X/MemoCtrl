package com.vknxworks.memoctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.data.settings.ThemeChoice

/**
 * Ctrl / Signal: the shared look of the Ctrl series. Dark only, near-black background,
 * graphite cards with hairline borders and one accent colour.
 */
object CtrlColors {
    /** The one accent colour. Change it here and the whole app follows. */
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** Card borders. */
    val Outline = Color(0xFF232328)
    /** Borders on controls: buttons, fields, the top bar avatar, the tab bar. */
    val ControlOutline = Color(0xFF2A2A2F)
    /** Dividers between list rows. */
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled or inactive things. */
    val Inactive = Color(0xFF8C8C94)
    val SwitchOffTrack = Color(0xFF2A2A30)
    val ProgressTrack = Color(0xFF26262C)
    val PinEmpty = Color(0xFF3A3A41)
    val Error = Color(0xFFFF6B6B)
}

/** Kept for code that refers to the app's accent by its old name. */
val MemoAccent = CtrlColors.Accent

private val Scheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Raised,
    onSecondaryContainer = CtrlColors.Text,
    tertiary = CtrlColors.Accent,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Background,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Raised,
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = CtrlColors.Error,
    scrim = Color.Black,
)

// ---------- Type ----------

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk: all UI text and headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** JetBrains Mono: numbers, data, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0, family: FontFamily = SpaceGrotesk) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

/** Extra styles of the Ctrl series that Material's type scale has no slot for. */
object CtrlType {
    /** Section labels: mono, uppercase, wide tracking, muted. Uppercase the text yourself. */
    val Label = style(11, 14, 500, 0.16, JetBrainsMono)
    /** "MEMO/CTRL" in the top bar. */
    val Wordmark = style(13, 16, 600, 0.14, JetBrainsMono)
    /** Small mono readouts: times, counts, addresses. */
    val Mono = style(12, 16, 400, 0.0, JetBrainsMono)
    val MonoStrong = style(11, 14, 600, 0.1, JetBrainsMono)
    /** Giant numbers on a hero card. */
    val Readout = style(64, 60, 700, -0.05)
    val ReadoutSmall = style(30, 34, 600, -0.03)
}

private val AppTypography = Typography(
    displayLarge = style(56, 54, 700, -0.04),
    displayMedium = style(48, 48, 700, -0.04),
    displaySmall = style(40, 42, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(30, 34, 700, -0.03),
    headlineSmall = style(24, 30, 700, -0.02),
    titleLarge = style(18, 24, 600),
    titleMedium = style(16, 22, 600),
    titleSmall = style(14, 20, 600),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(14, 21, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500, 0.02),
)

// ---------- Shapes ----------

object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Chip = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
    val Sheet = RoundedCornerShape(26.dp)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * The app theme. Ctrl / Signal is dark only: [choice] and [dynamic] are kept so old settings still
 * load, but no longer change anything (no light theme, no Material You colours).
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MemoCtrlTheme(choice: ThemeChoice = ThemeChoice.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
