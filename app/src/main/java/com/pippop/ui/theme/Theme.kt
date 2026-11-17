package com.pippop.ui.theme

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pippop.R
import com.pippop.data.ColourTheme
import com.pippop.data.ThemeMode

/**
 * Colours of the app in the Toy Box look: lavender background, white cards with bold ink
 * outlines and a coral accent. The Sudoku board uses them too: ink lines, a coral selected
 * square with its row, column and box in lavender, and your digits in deep blue.
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
    /**
     * You, the player: the same coral in every colourway, so your pieces and marks stay apart from
     * the opponent's orange and from the sea, and boards look the same. [meText] is its text form.
     */
    val me: Color,
    val meText: Color,
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
    me = Color(0xFFFF6B6B),
    meText = Color(0xFFC02F2F),
    accent = Color(0xFFF4F0FF),
    good = Color(0xFF047857),
    bad = Color(0xFFC42B30),
    warning = Color(0xFFB45309),
    opponentText = Color(0xFFB4530A),
    scrim = Color(0xFF0B0816),
    ink = Color(0xFF2B2440),
    hairline = Color(0xFFD9D2EE),
    boardBackground = Color(0xFFFFFFFF),
    lineThick = Color(0xFF2B2440),
    lineThin = Color(0xFF8A95A8),
    cellSelected = Color(0xFFFFD9D2),
    cellSameDigit = Color(0xFFE2DAFF),
    cellPeer = Color(0xFFF4F0FF),
    cellWrong = Color(0xFFF7CFD6),
    given = Color(0xFF2B2440),
    entry = Color(0xFF2B5FCC),
    wrong = Color(0xFFE55C6C),
    note = Color(0xFF6F6688),
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
    me = Color(0xFFFF7B7B),
    meText = Color(0xFFFF7B7B),
    accent = Color(0xFF2F2850),
    good = Color(0xFF4CCB8B),
    bad = Color(0xFFFF6B7D),
    warning = Color(0xFFE08A00),
    opponentText = Color(0xFFF2994A),
    scrim = Color(0xFF0B0816),
    // A light ink, so outlines and pressed edges show on the dark background (5:1).
    ink = Color(0xFF8A80B8),
    hairline = Color(0xFF3A3360),
    // Board: each highlight a clear step from the next (row/column/box 1.3:1 on the board,
    // the same digit 2.1:1, the selected square 2.5:1, with a coral ring), digits 4.5:1 or more.
    boardBackground = Color(0xFF17132A),
    lineThick = Color(0xFFB9B0E0),
    lineThin = Color(0xFF6E66A0),
    cellSelected = Color(0xFF8E3B47),
    cellSameDigit = Color(0xFF4C3F96),
    cellPeer = Color(0xFF2F2752),
    cellWrong = Color(0xFF6B1E2C),
    given = Color(0xFFF1EDFF),
    entry = Color(0xFFB3CCFF),
    wrong = Color(0xFFFF6B7D),
    note = Color(0xFFA49CC4),
)

/**
 * The other colourways. Light: the page, the main colour, its text form, the soft fill, the thin
 * line and the two soft board highlights change; ink, cards and everything a game paints stay.
 */
private val MintLight = LightPalette.copy(
    background = Color(0xFFDDF5EA), primary = Color(0xFF2FBF8F), primaryText = Color(0xFF0B6E4F), accent = Color(0xFFEAFBF3),
    hairline = Color(0xFFBFE5D3), cellPeer = Color(0xFFEAFBF3), cellSameDigit = Color(0xFFC9EEDD),
)
private val PeachLight = LightPalette.copy(
    background = Color(0xFFFFE9DC), primary = Color(0xFFFF8A5B), primaryText = Color(0xFFB8431A), accent = Color(0xFFFFF3EB),
    hairline = Color(0xFFF3CDB8), cellPeer = Color(0xFFFFF3EB), cellSameDigit = Color(0xFFFFDCC8),
)
private val SkyLight = LightPalette.copy(
    background = Color(0xFFDDEEFF), primary = Color(0xFF5AA9FF), primaryText = Color(0xFF1B5FB3), accent = Color(0xFFEDF6FF),
    hairline = Color(0xFFBCD8F5), cellPeer = Color(0xFFEDF6FF), cellSameDigit = Color(0xFFCFE6FC),
)

/** The hue of [c] in degrees (0 for a grey). */
private fun hueOf(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val d = max - minOf(c.red, c.green, c.blue)
    if (d == 0f) return 0f
    return 60f * when (max) {
        c.red -> ((c.green - c.blue) / d).mod(6f)
        c.green -> (c.blue - c.red) / d + 2f
        else -> (c.red - c.green) / d + 4f
    }
}

/** [c] with its hue turned by [degrees]; saturation and brightness stay. */
private fun rotateHue(c: Color, degrees: Float): Color {
    val max = maxOf(c.red, c.green, c.blue)
    val s = if (max == 0f) 0f else (max - minOf(c.red, c.green, c.blue)) / max
    val nh = ((hueOf(c) + degrees) % 360f + 360f) % 360f
    fun channel(n: Float): Float {
        val k = (n + nh / 60f) % 6f
        return max - max * s * maxOf(0f, minOf(k, 4f - k, 1f))
    }
    return Color(channel(5f), channel(3f), channel(1f), c.alpha)
}

private fun toLinear(v: Float) = if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
private fun toSrgb(v: Float) = if (v <= 0.0031308f) v * 12.92f else (1.055 * Math.pow(v.toDouble(), 1 / 2.4) - 0.055).toFloat()

/** [c] scaled so its relative luminance equals [like]'s: a turned hue must not also turn lighter or darker. */
private fun sameBrightness(c: Color, like: Color): Color {
    val l = c.luminance()
    if (l <= 0f) return c
    val k = like.luminance() / l
    fun ch(v: Float) = toSrgb((toLinear(v) * k).coerceIn(0f, 1f))
    return Color(ch(c.red), ch(c.green), ch(c.blue), c.alpha)
}

/**
 * The dark version of a colourway: "the same shift" as its light one, made of two moves on
 * [DarkPalette]. (1) The tinted surfaces (page, soft fill, thin line, the two soft board
 * highlights) turn by the hue distance between the light page colours: lavender to this
 * colourway's, then are scaled to the very luminance the lavender one had (a hue turn alone makes
 * greens and yellows much brighter). (2) The main colour is the light one (it is already bright enough on dark), with
 * its text form the same colour, as in DarkPalette. Ink, cards, text and the games' colours stay.
 */
private fun darkOf(light: SudokuPalette): SudokuPalette {
    val turn = hueOf(light.background) - hueOf(LightPalette.background)
    fun t(c: Color) = sameBrightness(rotateHue(c, turn), c)
    return DarkPalette.copy(
        background = t(DarkPalette.background), accent = t(DarkPalette.accent), hairline = t(DarkPalette.hairline),
        cellPeer = t(DarkPalette.cellPeer), cellSameDigit = t(DarkPalette.cellSameDigit),
        primary = light.primary, primaryText = light.primary,
    )
}

/** All eight palettes: the colourway, light or dark. Lavender is [LightPalette] and [DarkPalette] unchanged. */
fun paletteFor(theme: ColourTheme, dark: Boolean): SudokuPalette = when (theme) {
    ColourTheme.LAVENDER -> if (dark) DarkPalette else LightPalette
    ColourTheme.MINT -> if (dark) MintDark else MintLight
    ColourTheme.PEACH -> if (dark) PeachDark else PeachLight
    ColourTheme.SKY -> if (dark) SkyDark else SkyLight
}

private val MintDark = darkOf(MintLight)
private val PeachDark = darkOf(PeachLight)
private val SkyDark = darkOf(SkyLight)

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
fun PipPopTheme(darkTheme: Boolean, colourTheme: ColourTheme = ColourTheme.LAVENDER, content: @Composable () -> Unit) {
    val p = paletteFor(colourTheme, darkTheme)
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
