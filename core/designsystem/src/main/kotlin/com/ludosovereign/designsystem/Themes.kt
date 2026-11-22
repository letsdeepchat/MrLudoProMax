package com.ludosovereign.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    /** Subtle geometric pattern behind the board. */
    val patterned: Boolean = false,
)

object BoardThemes {
    /** Okabe–Ito palette: distinguishable under the common forms of colour-vision deficiency. */
    val COLORBLIND_TOKENS = listOf(
        Color(0xFFD55E00), Color(0xFF009E73), Color(0xFFF0E442), Color(0xFF0072B2), Color(0xFFCC79A7), Color(0xFF56B4E9),
    )
    private val CLASSIC_TOKENS = listOf(
        Color(0xFFE53935), Color(0xFF43A047), Color(0xFFFDD835), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFFB8C00),
    )

    /** Default: deep royal-blue patterned backdrop, white high-contrast track, vivid primary yards. */
    val CLASSIC_ROYAL = BoardTheme(
        "classic_royal", "Classic Royal", true,
        background = Color(0xFF0B2A6B), surface = Color(0xFF123782), boardBase = Color(0xFFFFFFFF),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF1B1F2A), safeCell = Color(0xFFFFFFFF),
        center = Color(0xFFFFFFFF), accent = Color(0xFFFFC62F), onSurface = Color(0xFFF2F5FF),
        tokens = listOf(Color(0xFFE5322D), Color(0xFF22A447), Color(0xFFF7C51E), Color(0xFF1E6FE0), Color(0xFF8E3CCB), Color(0xFFF57C1F)),
        fortressMetal = listOf(Color(0xFFFFF1B8), Color(0xFFC9961A), Color(0xFFFFD54F), Color(0xFF8D6608)),
        yardInner = Color(0xFFFFFFFF), frame = Color(0xFF07163B), patterned = true,
    )
    val ROYAL_IVORY = BoardTheme(
        "royal_ivory", "Royal Ivory", false,
        background = Color(0xFFF6F1E7), surface = Color(0xFFFFFBF2), boardBase = Color(0xFFEDE3CF),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF6D5D45), safeCell = Color(0xFFE8D9B5),
        center = Color(0xFFD9C7A0), accent = Color(0xFFB8860B), onSurface = Color(0xFF2B2418), tokens = CLASSIC_TOKENS,
    )
    val MIDNIGHT_SOVEREIGN = BoardTheme(
        "midnight_sovereign", "Midnight Sovereign", true,
        background = Color(0xFF0E1117), surface = Color(0xFF171C26), boardBase = Color(0xFF1D2433),
        cell = Color(0xFF263045), cellBorder = Color(0xFF3E4A66), safeCell = Color(0xFF4A3F1E),
        center = Color(0xFF2E2A1A), accent = Color(0xFFFFC94D), onSurface = Color(0xFFE9ECF2),
        tokens = listOf(Color(0xFFFF5A5F), Color(0xFF3DDC97), Color(0xFFFFD23F), Color(0xFF4EA8DE), Color(0xFFB980F0), Color(0xFFFF9F43)),
        fortressMetal = listOf(Color(0xFFFFE8A3), Color(0xFFB8860B), Color(0xFFFFD700), Color(0xFF8B6508)),
    )
    val EMERALD_JUNGLE = BoardTheme(
        "emerald_jungle", "Emerald Jungle", false,
        background = Color(0xFFE6F2E6), surface = Color(0xFFF4FBF4), boardBase = Color(0xFFCFE6CF),
        cell = Color(0xFFFAFFF7), cellBorder = Color(0xFF2F5D3A), safeCell = Color(0xFFB9DDB0),
        center = Color(0xFF9CCB8F), accent = Color(0xFF2E7D32), onSurface = Color(0xFF16301C), tokens = CLASSIC_TOKENS,
    )
    val DESERT_SAND = BoardTheme(
        "desert_sand", "Desert Sand", false,
        background = Color(0xFFF7E9D2), surface = Color(0xFFFFF5E6), boardBase = Color(0xFFEBCF9F),
        cell = Color(0xFFFFF8EC), cellBorder = Color(0xFF8A5A2B), safeCell = Color(0xFFF0C987),
        center = Color(0xFFDDA35C), accent = Color(0xFFC2571A), onSurface = Color(0xFF3B2412), tokens = CLASSIC_TOKENS,
    )
    val OCEAN_GLASS = BoardTheme(
        "ocean_glass", "Ocean Glass", true,
        background = Color(0xFF06222E), surface = Color(0xFF0B3242), boardBase = Color(0xFF0F3D52),
        cell = Color(0xFF16566F), cellBorder = Color(0xFF5FB3CF), safeCell = Color(0xFF1F7A8C),
        center = Color(0xFF0A4A5E), accent = Color(0xFF7EE8FA), onSurface = Color(0xFFE3F6FB),
        tokens = listOf(Color(0xFFFF6B6B), Color(0xFF6BCB77), Color(0xFFFFD93D), Color(0xFF4D96FF), Color(0xFFC77DFF), Color(0xFFFF9A3C)),
    )
    val NEON_ARCADE = BoardTheme(
        "neon_arcade", "Neon Arcade", true,
        background = Color(0xFF0A0014), surface = Color(0xFF14002A), boardBase = Color(0xFF1A0536),
        cell = Color(0xFF220A45), cellBorder = Color(0xFFFF2BD6), safeCell = Color(0xFF3B0F6E),
        center = Color(0xFF2A0A55), accent = Color(0xFF00F0FF), onSurface = Color(0xFFF5E9FF),
        tokens = listOf(Color(0xFFFF2E63), Color(0xFF08F7FE), Color(0xFFFFE700), Color(0xFF2D7BFF), Color(0xFFFE53BB), Color(0xFF09FBD3)),
    )
    val SAKURA_BLOSSOM = BoardTheme(
        "sakura_blossom", "Sakura Blossom", false,
        background = Color(0xFFFCEEF2), surface = Color(0xFFFFF7F9), boardBase = Color(0xFFF7D9E3),
        cell = Color(0xFFFFFFFF), cellBorder = Color(0xFF9C4E6A), safeCell = Color(0xFFF4C1D2),
        center = Color(0xFFEFA9C1), accent = Color(0xFFD6336C), onSurface = Color(0xFF3A1724), tokens = CLASSIC_TOKENS,
    )
    val GRAPHITE_CONTRAST = BoardTheme(
        "graphite_contrast", "Graphite High Contrast", true,
        background = Color(0xFF000000), surface = Color(0xFF121212), boardBase = Color(0xFF1A1A1A),
        cell = Color(0xFF2A2A2A), cellBorder = Color(0xFFFFFFFF), safeCell = Color(0xFF555555),
        center = Color(0xFF333333), accent = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF), tokens = COLORBLIND_TOKENS,
    )

    val ALL = listOf(CLASSIC_ROYAL, ROYAL_IVORY, MIDNIGHT_SOVEREIGN, EMERALD_JUNGLE, DESERT_SAND, OCEAN_GLASS, NEON_ARCADE, SAKURA_BLOSSOM, GRAPHITE_CONTRAST)

    fun byId(id: String): BoardTheme = ALL.firstOrNull { it.id == id } ?: CLASSIC_ROYAL

    val GLYPHS = TokenGlyph.entries
}

val LocalBoardTheme = staticCompositionLocalOf { BoardThemes.CLASSIC_ROYAL }
val LocalColorblind = staticCompositionLocalOf { false }

fun BoardTheme.tokenColor(colorIndex: Int, colorblind: Boolean): Color =
    (if (colorblind) BoardThemes.COLORBLIND_TOKENS else tokens)[colorIndex.mod(6)]

private fun BoardTheme.toColorScheme(): ColorScheme = if (dark) darkColorScheme(
    primary = accent, onPrimary = background, secondary = tokens[3], tertiary = tokens[1],
    background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
    surfaceVariant = boardBase, onSurfaceVariant = onSurface.copy(alpha = 0.75f), outline = cellBorder,
    surfaceContainer = boardBase, surfaceContainerHigh = cell, surfaceContainerLow = surface,
) else lightColorScheme(
    primary = accent, onPrimary = Color.White, secondary = tokens[3], tertiary = tokens[1],
    background = background, onBackground = onSurface, surface = surface, onSurface = onSurface,
    surfaceVariant = boardBase, onSurfaceVariant = onSurface.copy(alpha = 0.75f), outline = cellBorder,
    surfaceContainer = boardBase, surfaceContainerHigh = cell, surfaceContainerLow = surface,
)

@Composable
fun LudoSovereignTheme(theme: BoardTheme, colorblind: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBoardTheme provides theme, LocalColorblind provides colorblind) {
        MaterialTheme(colorScheme = theme.toColorScheme(), content = content)
    }
}
