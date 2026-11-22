package com.ludosovereign.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glossy 3-D map-pin pawn. [ground] is where the tip touches the board; [lift] (0..1) raises the
 * body for hops while the soft elliptical shadow stays on the ground and shrinks/fades.
 *
 * Anatomy: shaded teardrop body, specular gloss top-left, white collar ring, coloured centre dot
 * (with a white glyph inside in colour-blind mode so colour is never the only cue).
 */
fun DrawScope.drawPin(
    ground: Offset,
    r: Float,
    color: Color,
    lift: Float = 0f,
    glyph: TokenGlyph? = null,
    alpha: Float = 1f,
    liftHeight: Float = r * 2.2f,
) {
    // Ground shadow: shrinks and softens as the pawn rises.
    val sh = (1f - 0.55f * lift).coerceIn(0.3f, 1f)
    drawOval(
        Color.Black.copy(alpha = 0.32f * sh * alpha),
        topLeft = Offset(ground.x - r * 0.95f * sh, ground.y - r * 0.26f * sh),
        size = Size(r * 1.9f * sh, r * 0.52f * sh),
    )
    val tip = ground - Offset(0f, liftHeight * lift)
    val h = r * 1.55f
    val c = tip - Offset(0f, h)
    val theta = acos(r / h) // angle between the C→tip axis and the tangent point
    val phiR = Math.toDegrees(atan2(cos(theta), sin(theta)).toDouble()).toFloat()
    val body = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(c.x - r * sin(theta), c.y + r * cos(theta))
        arcTo(Rect(c, r), 180f - phiR, 180f + 2 * phiR, false)
        close()
    }
    val light = color.lighten(0.38f)
    val dark = color.darken(0.38f)
    drawPath(
        body,
        Brush.radialGradient(
            listOf(light.copy(alpha = alpha), color.copy(alpha = alpha), dark.copy(alpha = alpha)),
            center = c + Offset(-r * 0.35f, -r * 0.4f), radius = r * 2.4f,
        ),
    )
    drawPath(body, dark.darken(0.35f).copy(alpha = 0.85f * alpha), style = Stroke(r * 0.09f))
    // White collar ring and coloured centre dot.
    drawCircle(Color.White.copy(alpha = alpha), r * 0.6f, c)
    drawCircle(
        Brush.radialGradient(listOf(color.lighten(0.15f).copy(alpha = alpha), color.darken(0.2f).copy(alpha = alpha)),
            center = c - Offset(r * 0.12f, r * 0.12f), radius = r * 0.55f),
        r * 0.44f, c,
    )
    if (glyph != null) drawGlyph(glyph, c, r * 0.3f, Color.White.copy(alpha = 0.95f * alpha))
    // Specular gloss, top-left.
    drawOval(
        Color.White.copy(alpha = 0.55f * alpha),
        topLeft = c + Offset(-r * 0.78f, -r * 0.86f), size = Size(r * 0.62f, r * 0.38f),
    )
}

/** Height of a pin above its ground point (for hit-testing and stacking). */
fun pinHeight(r: Float) = r * 2.55f
