package com.ludosovereign.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen backdrop: the theme's vertical gradient, a soft light pool near the top, and (for
 * patterned themes) a faint lattice of board-game motifs: pips, pawns and stars.
 */
fun DrawScope.drawBackdrop(theme: BoardTheme) {
    val p = theme.ui
    drawRect(Brush.verticalGradient(listOf(p.backdropTop, p.backdropBottom)))
    drawRect(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.18f), radius = size.maxDimension * 0.65f),
    )
    if (!theme.patterned) return
    val ink = if (p.backdropBottom.relativeLuminance() > 0.4) Color.Black.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.07f)
    val step = 52f * density
    val m = 7f * density
    var row = 0
    var y = step * 0.5f
    while (y < size.height + step) {
        var x = if (row % 2 == 0) step * 0.5f else step
        var col = 0
        while (x < size.width + step) {
            val c = Offset(x, y)
            when ((row + col) % 3) {
                0 -> rotate(18f, c) { // tiny die
                    drawRoundRect(ink, c - Offset(m, m), Size(2 * m, 2 * m), CornerRadius(m * 0.45f), style = Stroke(1.4f * density))
                    drawCircle(ink, m * 0.22f, c - Offset(m * 0.45f, m * 0.45f)); drawCircle(ink, m * 0.22f, c + Offset(m * 0.45f, m * 0.45f))
                }
                1 -> { drawCircle(ink, m * 0.75f, c, style = Stroke(1.4f * density)); drawCircle(ink, m * 0.28f, c) }
                else -> drawPath(starPath(c, m * 0.9f), ink)
            }
            x += step; col++
        }
        y += step * 0.75f; row++
    }
}

/**
 * Tactile 3-D surface: a solid face with a crisp border sitting on a darker lip. Pressing pushes
 * the face down onto the lip (translateY) with a slight squeeze, and releasing springs it back.
 */
@Composable
fun TactileSurface(
    onClick: (() -> Unit)?,
    fill: Color,
    border: Color,
    lip: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    lipHeight: Dp = 4.dp,
    borderWidth: Dp = 1.5.dp,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "press")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .padding(bottom = lipHeight)
            .drawBehind {
                val lp = lipHeight.toPx()
                val cr = CornerRadius(corner.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.16f), Offset(0f, lp + 2.5f * density), size, cr)
                drawRoundRect(lip, Offset(0f, lp), size, cr)
            }
            .graphicsLayer {
                translationY = press * lipHeight.toPx()
                val sq = 1f - 0.018f * press
                scaleX = sq; scaleY = sq
            }
            .clip(shape)
            .background(fill)
            .drawWithContent {
                drawContent()
                // Gloss on the upper half for a lit, rounded face.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.55f))
            }
            .border(borderWidth, border, shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/** Bright call-to-action (Resume, Play) in the theme's CTA colours. */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp,
) {
    val p = LocalBoardTheme.current.ui
    TactileSurface(
        onClick = if (enabled) onClick else null,
        fill = if (enabled) p.cta else p.cta.copy(alpha = 0.45f), border = p.ctaLip.copy(alpha = 0.55f), lip = p.ctaLip,
        corner = 16.dp, contentColor = p.onCta, modifier = modifier.heightIn(min = height),
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Secondary pill button (Stats, Audio Studio, menu actions) with an icon and a crisp border. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val p = LocalBoardTheme.current.ui
    TactileSurface(
        onClick = onClick, fill = p.pill, border = p.pillBorder, lip = p.cardLip,
        corner = 26.dp, lipHeight = 3.dp, borderWidth = 2.dp, contentColor = p.onPill, modifier = modifier.heightIn(min = 50.dp),
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Top app bar colours for screens drawn straight on the backdrop. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun backdropTopBarColors(): androidx.compose.material3.TopAppBarColors {
    val p = LocalBoardTheme.current.ui
    return androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent,
        titleContentColor = p.onBackdrop, navigationIconContentColor = p.onBackdrop, actionIconContentColor = p.onBackdrop,
    )
}

/** Card surface used by settings-style sections: the palette's card face with its crisp border. */
@Composable
fun PaletteCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalBoardTheme.current.ui
    androidx.compose.material3.Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = p.card, contentColor = p.onCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, p.cardBorder),
        content = content,
    )
}
