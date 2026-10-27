package com.arcade.sudoku.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.arcade.sudoku.R
import com.arcade.sudoku.data.ThemeMode

/**
 * Colours of the app in the Toy Box look: lavender background, white cards with bold ink
 * outlines and a coral accent. The Sudoku board keeps its clean blue-on-white colours.
 */
data class SudokuPalette(
    val dark: Boolean,
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val onPrimary: Color,
    /** The brand colour as text and thin lines: deep enough to read on light surfaces. */
    val primaryText: Color,
    val accent: Color,
    val good: Color,
    val bad: Color,
    /** Lines that need attention without being errors (levels that don't match). */
    val warning: Color,
    /** The opponent's colour as text (their fill is [OpponentColor]). */
    val opponentText: Color,
    /** The dim layer behind popups: always dark, whatever the theme's ink. */
    val scrim: Color,
    /** Outlines and the pressed edge under cards and buttons. */
    val ink: Color,
    /** The thin border of flat information cards. */
    val hairline: Color,
    // Board
    val boardBackground: Color,
    val lineThick: Color,
    val lineThin: Color,
    val cellSelected: Color,
    val cellSameDigit: Color,
    val cellPeer: Color,
    val cellWrong: Color,
    val given: Color,
    val entry: Color,
    val wrong: Color,
    val note: Color,
)

val LightPalette = SudokuPalette(
    dark = false,
    background = Color(0xFFEFEAFF),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF2B2440),
    muted = Color(0xFF6F6688),
    primary = Color(0xFFFF6B6B),
    // Ink on coral is 5.3:1; white on coral was 2.8:1.
    onPrimary = Color(0xFF2B2440),
    primaryText = Color(0xFFC02F2F),
    accent = Color(0xFFF4F0FF),
    good = Color(0xFF047857),
    bad = Color(0xFFC42B30),
    warning = Color(0xFFB45309),
    opponentText = Color(0xFFB4530A),
    scrim = Color(0xFF0B0816),
    ink = Color(0xFF2B2440),
    hairline = Color(0xFFD9D2EE),
    boardBackground = Color(0xFFFFFFFF),
    lineThick = Color(0xFF344861),
    lineThin = Color(0xFF8A95A8),
    cellSelected = Color(0xFFBBDEFB),
    cellSameDigit = Color(0xFFC3D7EA),
    cellPeer = Color(0xFFE2EBF3),
    cellWrong = Color(0xFFF7CFD6),
    given = Color(0xFF344861),
    entry = Color(0xFF0072E3),
    wrong = Color(0xFFE55C6C),
    note = Color(0xFF6E7C91),
)

val DarkPalette = SudokuPalette(
    dark = true,
    background = Color(0xFF17132A),
    card = Color(0xFF241E3D),
    text = Color(0xFFF1EDFF),
    muted = Color(0xFFA49CC4),
    primary = Color(0xFFFF7B7B),
    onPrimary = Color(0xFF2B0D12),
    primaryText = Color(0xFFFF7B7B),
    accent = Color(0xFF2F2850),
    good = Color(0xFF4CCB8B),
    bad = Color(0xFFFF6B7D),
    warning = Color(0xFFE08A00),
    opponentText = Color(0xFFF2994A),
    scrim = Color(0xFF0B0816),
    // A light ink, so outlines and pressed edges show on the dark background (5:1).
    ink = Color(0xFF8A80B8),
    hairline = Color(0xFF3A3360),
    boardBackground = Color(0xFF1C2230),
    lineThick = Color(0xFF9FB3CF),
    lineThin = Color(0xFF6B7893),
    cellSelected = Color(0xFF2B5C98),
    cellSameDigit = Color(0xFF2E4A70),
    cellPeer = Color(0xFF252E40),
    cellWrong = Color(0xFF5A2A35),
    given = Color(0xFFE6EAF2),
    entry = Color(0xFF5CB0FF),
    wrong = Color(0xFFFF6B7D),
    note = Color(0xFF8A98B0),
)

/** Body text: Figtree. */
val BodyFont = FontFamily(
    Font(R.font.figtree_medium, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

/** Headings and big numbers: the rounded Baloo 2. */
val DisplayFont = FontFamily(Font(R.font.baloo2_bold, FontWeight.Bold))

private val AppTypography = Typography().let { t ->
    fun TextStyle.body() = copy(fontFamily = BodyFont)
    fun TextStyle.display() = copy(fontFamily = DisplayFont, fontWeight = FontWeight.Bold)
    t.copy(
        displayLarge = t.displayLarge.display(), displayMedium = t.displayMedium.display(), displaySmall = t.displaySmall.display(),
        headlineLarge = t.headlineLarge.display(), headlineMedium = t.headlineMedium.display(), headlineSmall = t.headlineSmall.display(),
        titleLarge = t.titleLarge.display(), titleMedium = t.titleMedium.body(), titleSmall = t.titleSmall.body(),
        bodyLarge = t.bodyLarge.body(), bodyMedium = t.bodyMedium.body(), bodySmall = t.bodySmall.body(),
        labelLarge = t.labelLarge.body(), labelMedium = t.labelMedium.body(), labelSmall = t.labelSmall.body(),
    )
}

/** The opponent's colour in battle progress bars. */
val OpponentColor = Color(0xFFF2994A)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object AppTheme {
    val palette: SudokuPalette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ArcadeSudokuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val p = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, background = p.background, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.accent, onSurfaceVariant = p.muted,
            outline = p.lineThin, error = p.bad,
        )
    } else {
        lightColorScheme(
            primary = p.primary, onPrimary = p.onPrimary, background = p.background, onBackground = p.text,
            surface = p.card, onSurface = p.text, surfaceVariant = p.accent, onSurfaceVariant = p.muted,
            outline = p.lineThin, error = p.bad,
        )
    }
    CompositionLocalProvider(LocalPalette provides p, LocalReduceMotion provides reduceMotionSetting()) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
