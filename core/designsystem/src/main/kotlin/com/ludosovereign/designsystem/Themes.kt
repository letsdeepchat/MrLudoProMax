package com.ludosovereign.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/**
 * Menu and chrome palette, kept separate from the board colours so every theme can guarantee
 * contrast: text on the backdrop, tactile cards that never share the backdrop's tone, secondary
 * pills, and a bright call-to-action (Resume, Play). `ThemeContrastTest` audits every theme.
 */
@Immutable
data class UiPalette(
    val backdropTop: Color,
    val backdropBottom: Color,
    val onBackdrop: Color,
    /** Primary game cards and dialogs. */
    val card: Color,
    val onCard: Color,
    val cardBorder: Color,
    /** Darker bottom edge that gives cards their 3-D lip. */
    val cardLip: Color,
    /** Secondary buttons (Stats, Audio Studio). */
    val pill: Color,
    val onPill: Color,
    val pillBorder: Color,
    /** Call-to-action fill (Resume, Play) and its lip. */
    val cta: Color,
    val onCta: Color,
    val ctaLip: Color,
    /** Controls drawn on cards: selected chips, switches, sliders, icons. */
    val primary: Color,
    val onPrimary: Color,
)

/** Shape drawn inside each token so colours are never the only cue (colour-blind support). */
enum class TokenGlyph { CIRCLE, TRIANGLE, SQUARE, DIAMOND, STAR, HEXAGON }

@Immutable
data class BoardTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val boardBase: Color,
    val cell: Color,
    val cellBorder: Color,
    val safeCell: Color,
    val center: Color,
    val accent: Color,
    val onSurface: Color,
    /** Six player colours in seat order. */
    val tokens: List<Color>,
    val fortressMetal: List<Color> = listOf(Color(0xFFF5F5F5), Color(0xFF9E9E9E), Color(0xFFE0E0E0), Color(0xFF757575)),
    /** Inner container of each yard that holds the four spawn bases. */
    val yardInner: Color = cell,
    /** Frame drawn around the board. */
    val frame: Color = cellBorder,
    /** Subtle geometric pattern on the backdrop. */
    val patterned: Boolean = false,
    val ui: UiPalette,
    /** Thin grid lines between track tiles. */
    val grid: Color = cellBorder,
    /** Yard fills by colour index, when a theme wants yards in a different shade than lanes and pawns. */
    val yardTokens: List<Color>? = null,
    /** Rotating ring under pawns that can move. */
    val selectRing: Color = accent,
)

object BoardThemes {
    /**
     * Default token-palette index for each board seat. Palette order is red, green, yellow, blue,
     * purple, orange; seats run clockwise from the top-left yard, so this puts red top-left, green
     * top-right, blue bottom-right and yellow bottom-left on the cross board (same cycle on hex).
     */
    private val SEAT_COLORS = intArrayOf(0, 1, 3, 2, 4, 5)

    fun seatColorIndex(seat: Int): Int = SEAT_COLORS[seat.mod(SEAT_COLORS.size)]

    /** Okabe–Ito palette: distinguishable under the common forms of colour-vision deficiency. */
    val COLORBLIND_TOKENS = listOf(
        Color(0xFFD55E00), Color(0xFF009E73), Color(0xFFF0E442), Color(0xFF0072B2), Color(0xFFCC79A7), Color(0xFF56B4E9),
    )
    private val CLASSIC_TOKENS = listOf(
        Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFB8C00),
    )

    private val AMBER_CTA = Triple(Color(0xFFFFB300), Color(0xFF2B1A00), Color(0xFFA86F00))

    private fun ui(
        top: Long, bottom: Long, onBackdrop: Long,
        card: Long, onCard: Long, cardBorder: Long, cardLip: Long,
        pill: Long, onPill: Long, pillBorder: Long,
        cta: Triple<Color, Color, Color>,
        primary: Long, onPrimary: Long,
    ) = UiPalette(
        Color(top), Color(bottom), Color(onBackdrop), Color(card), Color(onCard), Color(cardBorder), Color(cardLip),
        Color(pill), Color(onPill), Color(pillBorder), cta.first, cta.second, cta.third, Color(primary), Color(onPrimary),
    )

    /**
     * Default: cheerful sky-to-royal azure backdrop and crisp white cards around a classic board:
     * solid red, green, mustard and sky-blue yards with white boxes, a white track with thin grey
     * grid lines, solid coloured home lanes and start squares.
     */
    val CLASSIC_ROYAL = BoardTheme(
        "classic_royal", "Classic Royal", false,
        background = Color(0xFF1C4FBF), surface = Color(0xFFFFFFFF), boardBase = Color(0xFFFFFFFF),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF1B1F2A), safeCell = Color(0xFFFFFFFF),
        center = Color(0xFFFFFFFF), accent = Color(0xFFFFC62F), onSurface = Color(0xFF14244F),
        tokens = listOf(Color(0xFFEE1D23), Color(0xFF00A148), Color(0xFFF9DC0E), Color(0xFF2AABF2), Color(0xFF8E3CCB), Color(0xFFF57C1F)),
        fortressMetal = listOf(Color(0xFFFFF1B8), Color(0xFFC9961A), Color(0xFFFFD54F), Color(0xFF8D6608)),
        yardInner = Color(0xFFFFFFFF), frame = Color(0xFF0B2A6B), patterned = true,
        ui = ui(0xFF2B6FE6, 0xFF1C4FBF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFF14244F, 0xFFFFFFFF, 0xFF0D3796,
            0xFFE6EFFF, 0xFF14244F, 0xFFFFFFFF,
            Triple(Color(0xFFFFC62F), Color(0xFF3A2600), Color(0xFFB07800)),
            0xFF1E56C5, 0xFFFFFFFF),
        grid = Color(0xFFA9ADB3),
        yardTokens = listOf(Color(0xFFEE1D23), Color(0xFF00A148), Color(0xFFC2AB1C), Color(0xFF2AABF2), Color(0xFF8E3CCB), Color(0xFFF57C1F)),
        selectRing = Color(0xFF8A2C2C),
    )
    val ROYAL_IVORY = BoardTheme(
        "royal_ivory", "Royal Ivory", false,
        background = Color(0xFFF6F1E7), surface = Color(0xFFFFFBF2), boardBase = Color(0xFFEDE3CF),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF6D5D45), safeCell = Color(0xFFE8D9B5),
        center = Color(0xFFD9C7A0), accent = Color(0xFFB8860B), onSurface = Color(0xFF2B2418), tokens = CLASSIC_TOKENS,
        patterned = true,
        ui = ui(0xFFEDE2CA, 0xFFD8C59E, 0xFF2B2418,
            0xFFFFFDF7, 0xFF2B2418, 0xFF6E5530, 0xFF6E5530,
            0xFF5C4A2E, 0xFFFFF8EA, 0xFF3E311D,
            AMBER_CTA, 0xFF7A5A12, 0xFFFFFFFF),
    )
    val MIDNIGHT_SOVEREIGN = BoardTheme(
        "midnight_sovereign", "Midnight Royale", true,
        background = Color(0xFF0E1117), surface = Color(0xFF171C26), boardBase = Color(0xFF1D2433),
        cell = Color(0xFF263045), cellBorder = Color(0xFF3E4A66), safeCell = Color(0xFF4A3F1E),
        center = Color(0xFF2E2A1A), accent = Color(0xFFFFC94D), onSurface = Color(0xFFE9ECF2),
        tokens = listOf(Color(0xFFFF5A5F), Color(0xFF3DDC97), Color(0xFFFFD23F), Color(0xFF4EA8DE), Color(0xFFB980F0), Color(0xFFFF9F43)),
        fortressMetal = listOf(Color(0xFFFFE8A3), Color(0xFFB8860B), Color(0xFFFFD700), Color(0xFF8B6508)),
        ui = ui(0xFF1C2436, 0xFF0E1117, 0xFFE9ECF2,
            0xFF34425F, 0xFFF2F4F8, 0xFF8D9BC0, 0xFF0A0D13,
            0xFF2A3550, 0xFFF2F4F8, 0xFFFFC94D,
            Triple(Color(0xFFFFC94D), Color(0xFF241A00), Color(0xFF8B6508)),
            0xFFFFC94D, 0xFF1A1400),
    )
    val EMERALD_JUNGLE = BoardTheme(
        "emerald_jungle", "Emerald Jungle", false,
        background = Color(0xFFE6F2E6), surface = Color(0xFFF4FBF4), boardBase = Color(0xFFCFE6CF),
        cell = Color(0xFFFAFFF7), cellBorder = Color(0xFF2F5D3A), safeCell = Color(0xFFB9DDB0),
        center = Color(0xFF9CCB8F), accent = Color(0xFF2E7D32), onSurface = Color(0xFF16301C), tokens = CLASSIC_TOKENS,
        patterned = true,
        ui = ui(0xFFCFE7C9, 0xFF9FCB96, 0xFF12301A,
            0xFFFFFFFF, 0xFF16301C, 0xFF2F5D3A, 0xFF2F5D3A,
            0xFF2E7D32, 0xFFFFFFFF, 0xFF1B5E20,
            AMBER_CTA, 0xFF2E7D32, 0xFFFFFFFF),
    )
    val DESERT_SAND = BoardTheme(
        "desert_sand", "Desert Sand", false,
        background = Color(0xFFF7E9D2), surface = Color(0xFFFFF5E6), boardBase = Color(0xFFEBCF9F),
        cell = Color(0xFFFFF8EC), cellBorder = Color(0xFF8A5A2B), safeCell = Color(0xFFF0C987),
        center = Color(0xFFDDA35C), accent = Color(0xFFC2571A), onSurface = Color(0xFF3B2412), tokens = CLASSIC_TOKENS,
        patterned = true,
        ui = ui(0xFFF3DDB8, 0xFFE2BC80, 0xFF3B2412,
            0xFFFFFDF8, 0xFF3B2412, 0xFF8A5A2B, 0xFF8A5A2B,
            0xFFA84A14, 0xFFFFFFFF, 0xFF7A3409,
            Triple(Color(0xFF1E8449), Color(0xFFFFFFFF), Color(0xFF135C32)),
            0xFFA84A14, 0xFFFFFFFF),
    )
    val OCEAN_GLASS = BoardTheme(
        "ocean_glass", "Ocean Glass", true,
        background = Color(0xFF06222E), surface = Color(0xFF0B3242), boardBase = Color(0xFF0F3D52),
        cell = Color(0xFF16566F), cellBorder = Color(0xFF5FB3CF), safeCell = Color(0xFF1F7A8C),
        center = Color(0xFF0A4A5E), accent = Color(0xFF7EE8FA), onSurface = Color(0xFFE3F6FB),
        tokens = listOf(Color(0xFFFF6B6B), Color(0xFF6BCB77), Color(0xFFFFD93D), Color(0xFF4D96FF), Color(0xFFC77DFF), Color(0xFFFF9A3C)),
        ui = ui(0xFF0E4C66, 0xFF06222E, 0xFFE3F6FB,
            0xFF155D78, 0xFFFFFFFF, 0xFF7EE8FA, 0xFF04303F,
            0xFF0B3A4D, 0xFFE3F6FB, 0xFF7EE8FA,
            Triple(Color(0xFF7EE8FA), Color(0xFF06222E), Color(0xFF2FA9BF)),
            0xFF7EE8FA, 0xFF06222E),
    )
    val NEON_ARCADE = BoardTheme(
        "neon_arcade", "Neon Arcade", true,
        background = Color(0xFF0A0014), surface = Color(0xFF14002A), boardBase = Color(0xFF1A0536),
        cell = Color(0xFF220A45), cellBorder = Color(0xFFFF2BD6), safeCell = Color(0xFF3B0F6E),
        center = Color(0xFF2A0A55), accent = Color(0xFF00F0FF), onSurface = Color(0xFFF5E9FF),
        tokens = listOf(Color(0xFFFF2E63), Color(0xFF08F7FE), Color(0xFFFFE700), Color(0xFF2D7BFF), Color(0xFFFE53BB), Color(0xFF09FBD3)),
        ui = ui(0xFF23064A, 0xFF0A0014, 0xFFF5E9FF,
            0xFF2C0B5E, 0xFFF5E9FF, 0xFFFF2BD6, 0xFF12002A,
            0xFF1A0536, 0xFFF5E9FF, 0xFF08F7FE,
            Triple(Color(0xFF00F0FF), Color(0xFF0A0014), Color(0xFF0097A7)),
            0xFF00F0FF, 0xFF0A0014),
    )
    val SAKURA_BLOSSOM = BoardTheme(
        "sakura_blossom", "Sakura Blossom", false,
        background = Color(0xFFFCEEF2), surface = Color(0xFFFFF7F9), boardBase = Color(0xFFF7D9E3),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF9C4E6A), safeCell = Color(0xFFF4C1D2),
        center = Color(0xFFEFA9C1), accent = Color(0xFFD6336C), onSurface = Color(0xFF3A1724), tokens = CLASSIC_TOKENS,
        patterned = true,
        ui = ui(0xFFF8D5E1, 0xFFEDB2C6, 0xFF3A1724,
            0xFFFFFFFF, 0xFF3A1724, 0xFF9C4E6A, 0xFF9C4E6A,
            0xFFC2255C, 0xFFFFFFFF, 0xFF8E1240,
            AMBER_CTA, 0xFFC2255C, 0xFFFFFFFF),
    )
    val GRAPHITE_CONTRAST = BoardTheme(
        "graphite_contrast", "Graphite High Contrast", true,
        background = Color(0xFF000000), surface = Color(0xFF121212), boardBase = Color(0xFF1A1A1A),
        cell = Color(0xFF2A2A2A), cellBorder = Color(0xFFFFFFFF), safeCell = Color(0xFF555555),
        center = Color(0xFF333333), accent = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF), tokens = COLORBLIND_TOKENS,
        ui = ui(0xFF1A1A1A, 0xFF000000, 0xFFFFFFFF,
            0xFF141414, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF5A5A5A,
            0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF,
            Triple(Color(0xFFFFD600), Color(0xFF000000), Color(0xFFA38900)),
            0xFFFFFFFF, 0xFF000000),
    )

    val ALL = listOf(CLASSIC_ROYAL, ROYAL_IVORY, MIDNIGHT_SOVEREIGN, EMERALD_JUNGLE, DESERT_SAND, OCEAN_GLASS, NEON_ARCADE, SAKURA_BLOSSOM, GRAPHITE_CONTRAST)

    fun byId(id: String): BoardTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC_ROYAL

    val GLYPHS = TokenGlyph.entries
}

val LocalBoardTheme = staticCompositionLocalOf { BoardThemes.CLASSIC_ROYAL }
val LocalColorblind = staticCompositionLocalOf { false }

fun BoardTheme.tokenColor(colorIndex: Int, colorblind: Boolean): Color =
    (if (colorblind) BoardThemes.COLORBLIND_TOKENS else tokens)[colorIndex.mod(6)]

/** Yard fill for a colour index: the theme's yard shade, or the pawn colour. */
fun BoardTheme.yardColor(colorIndex: Int, colorblind: Boolean): Color =
    if (colorblind || yardTokens == null) tokenColor(colorIndex, colorblind) else yardTokens[colorIndex.mod(6)]

/** WCAG relative luminance of an sRGB colour (alpha ignored). */
fun Color.relativeLuminance(): Double {
    fun ch(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * ch(red) + 0.7152 * ch(green) + 0.0722 * ch(blue)
}

/** WCAG contrast ratio between two opaque colours (1..21). */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance(); val lb = b.relativeLuminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * Material scheme built from the [UiPalette]: surfaces are the tactile cards, so every chip,
 * switch and slider sits on a card with guaranteed contrast, while text placed straight on the
 * backdrop uses `onBackground`.
 */
private fun BoardTheme.toColorScheme(): ColorScheme {
    val p = ui
    val lightCards = p.card.relativeLuminance() > 0.4
    val tint = { k: Float -> lerp(p.card, p.primary, k) }
    val selected = lerp(p.card, p.primary, if (lightCards) 0.16f else 0.28f)
    return if (lightCards) lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = selected, onPrimaryContainer = p.onCard,
        secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = selected, onSecondaryContainer = p.onCard,
        tertiary = tokens[1], background = p.backdropBottom, onBackground = p.onBackdrop,
        surface = p.card, onSurface = p.onCard, surfaceVariant = tint(0.07f), onSurfaceVariant = p.onCard.copy(alpha = 0.74f),
        surfaceContainerLowest = p.card, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = tint(0.04f), surfaceContainerHighest = tint(0.08f),
        outline = p.onCard.copy(alpha = 0.45f), outlineVariant = p.onCard.copy(alpha = 0.18f),
    ) else darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = selected, onPrimaryContainer = p.onCard,
        secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = selected, onSecondaryContainer = p.onCard,
        tertiary = tokens[1], background = p.backdropBottom, onBackground = p.onBackdrop,
        surface = p.card, onSurface = p.onCard, surfaceVariant = tint(0.1f), onSurfaceVariant = p.onCard.copy(alpha = 0.78f),
        surfaceContainerLowest = p.card, surfaceContainerLow = p.card, surfaceContainer = p.card,
        surfaceContainerHigh = tint(0.06f), surfaceContainerHighest = tint(0.12f),
        outline = p.onCard.copy(alpha = 0.5f), outlineVariant = p.onCard.copy(alpha = 0.2f),
    )
}

/**
 * App theme: provides the board theme, a Material scheme derived from its [UiPalette], and paints
 * the backdrop gradient once behind every screen (screens use transparent containers).
 */
@Composable
fun LudoSovereignTheme(theme: BoardTheme, colorblind: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBoardTheme provides theme, LocalColorblind provides colorblind) {
        MaterialTheme(colorScheme = theme.toColorScheme()) {
            Box(Modifier.fillMaxSize()) {
                // Own layer: board animations never re-record the backdrop.
                Spacer(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawBackdrop(theme) })
                content()
            }
        }
    }
}
