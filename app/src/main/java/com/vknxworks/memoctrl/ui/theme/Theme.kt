package com.vknxworks.memoctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** One full set of Ctrl / Signal colours. [CtrlColors.Dark] and [CtrlColors.Light] are the two. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    /** Card borders. */
    val outline: Color,
    /** Borders on controls: buttons, fields, the top bar avatar, the tab bar. */
    val controlOutline: Color,
    /** Dividers between list rows. */
    val divider: Color,
    val text: Color,
    val muted: Color,
    /** Disabled or inactive things. */
    val inactive: Color,
    /** The accent as text or an icon on the background (the fill colour is too pale on white). */
    val accentText: Color,
    /** Colour on top of [accentText] when it's used as a fill (Material's onPrimary). */
    val onAccentText: Color,
    val switchOffTrack: Color,
    val switchOffKnob: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val pinEmpty: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    /** #tag chips. */
    val chipFill: Color,
    val chipBorder: Color,
    /** Heatmap: no memos, then 1, 2, 3-4 and 5+ memos that day. */
    val heat: List<Color>,
)

/**
 * Ctrl / Signal: the shared look of the Ctrl series. Follows the phone's dark/light setting.
 * The accent is defined once here; [Accent] is the fill (hero cards, main buttons, the selected tab,
 * switches, PIN dots) and [AccentText] the accent for text and icons.
 * The other colours read the current palette from [LocalCtrlColors].
 */
object CtrlColors {
    /** The one accent colour, as a fill. Change it here and the whole app follows. */
    val Accent = Color(0xFFB48CFF)
    /** Content on an accent fill (both themes). */
    val OnAccent = Color(0xFF08080A)
    /** The accent as text or icon on light backgrounds. */
    val AccentTextLight = Color(0xFF6A3BDB)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        inactive = Color(0xFF8C8C94),
        accentText = Accent,
        onAccentText = OnAccent,
        switchOffTrack = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        progressTrack = Color(0xFF26262C),
        progressFill = Color(0xFFF4F4F2),
        pinEmpty = Color(0xFF3A3A41),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF3A1416),
        chipFill = Accent.copy(alpha = 0.12f),
        chipBorder = Accent.copy(alpha = 0.28f),
        heat = listOf(
            Color(0xFF1C1C21),
            Accent.copy(alpha = 0.30f),
            Accent.copy(alpha = 0.52f),
            Accent.copy(alpha = 0.76f),
            Accent,
        ),
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        inactive = Color(0xFF74747C),
        accentText = AccentTextLight,
        onAccentText = Color(0xFFFFFFFF),
        switchOffTrack = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        progressTrack = Color(0xFFE2E2DC),
        progressFill = Color(0xFF0B0B0D),
        pinEmpty = Color(0xFFC9C9C1),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE3E3),
        chipFill = Accent.copy(alpha = 0.18f),
        chipBorder = AccentTextLight.copy(alpha = 0.30f),
        // Pale lilac to deep violet, so even one memo shows on a white card.
        heat = listOf(
            Color(0xFFECECE7),
            Accent.copy(alpha = 0.40f),
            Accent.copy(alpha = 0.70f),
            Accent,
            AccentTextLight,
        ),
    )

    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.outline
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    val Inactive: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.inactive
    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val SwitchOffTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOffTrack
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOffKnob
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressTrack
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.progressFill
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
    val ChipFill: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.chipFill
    val ChipBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.chipBorder
    val Heat: List<Color> @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.heat
}

/** The palette of the current theme. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/** Kept for code that refers to the app's accent by its old name. */
val MemoAccent = CtrlColors.Accent

private fun schemeOf(p: CtrlPalette) = (if (p.isDark) darkColorScheme() else lightColorScheme()).copy(
    // primary is the accent as Material components use it: for text, icons, checkboxes and spinners.
    // Fills that must be the bright accent (main buttons, hero cards) use CtrlColors.Accent directly.
    primary = p.accentText,
    onPrimary = p.onAccentText,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = p.text,
    onSecondary = p.background,
    secondaryContainer = p.raised,
    onSecondaryContainer = p.text,
    tertiary = p.accentText,
    onTertiary = p.onAccentText,
    tertiaryContainer = p.raised,
    onTertiaryContainer = p.text,
    background = p.background,
    onBackground = p.text,
    surface = p.background,
    onSurface = p.text,
    surfaceVariant = p.raised,
    onSurfaceVariant = p.muted,
    surfaceTint = Color.Transparent,
    inverseSurface = p.text,
    inverseOnSurface = p.background,
    surfaceBright = p.raised,
    surfaceDim = p.background,
    surfaceContainerLowest = p.background,
    surfaceContainerLow = p.surface,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.raised,
    surfaceContainerHighest = p.raised,
    outline = p.controlOutline,
    outlineVariant = p.outline,
    error = p.error,
    onError = p.onError,
    errorContainer = p.errorContainer,
    onErrorContainer = p.error,
    scrim = Color.Black,
)

private val DarkScheme = schemeOf(CtrlColors.Dark)
private val LightScheme = schemeOf(CtrlColors.Light)

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
 * The app theme. It follows the phone's dark/light setting; there's no in-app theme choice and no
 * Material You colours. [choice] and [dynamic] are kept so old settings still load, but do nothing.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MemoCtrlTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    dynamic: Boolean = false,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
