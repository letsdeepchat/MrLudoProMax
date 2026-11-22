package com.ludosovereign.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Thickness of the white outline around a pin, as a fraction of its head radius. */
const val PIN_OUTLINE = 0.28f

/**
 * Glossy 3-D map-pin pawn. [ground] is where the tip touches the board; [lift] (0..1) raises the
 * body for hops while the elliptical contact shadow stays on the ground and shrinks/fades.
 *
 * Anatomy: a thick pure-white outline with a dark hairline outside it (so the pawn pops on white
 * and coloured tiles alike), a saturated teardrop body lit from the top-left, a curved specular
 * highlight, a white collar around a recessed well, and a coloured centre dot (with a white
 * glyph inside in colour-blind mode so colour is never the only cue).
 *
 * [squashX]/[squashY] deform the body around its tip for landing squash-and-stretch.
 */
fun DrawScope.drawPin(
    ground: Offset,
    r: Float,
    color: Color,
    lift: Float = 0f,
    glyph: TokenGlyph? = null,
    alpha: Float = 1f,
    liftHeight: Float = r * 2.2f,
    squashX: Float = 1f,
    squashY: Float = 1f,
) {
    // Contact shadow: soft wide falloff plus a darker core; shrinks and fades as the pawn rises.
    val sh = (1f - 0.55f * lift).coerceIn(0.3f, 1f)
    val sw = r * 1.15f * sh * squashX
    drawOval(Color.Black.copy(alpha = 0.16f * sh * alpha), Offset(ground.x - sw * 1.2f, ground.y - r * 0.36f * sh), Size(sw * 2.4f, r * 0.72f * sh))
    drawOval(Color.Black.copy(alpha = 0.30f * sh * alpha), Offset(ground.x - sw * 0.75f, ground.y - r * 0.22f * sh), Size(sw * 1.5f, r * 0.44f * sh))

    val tip = ground - Offset(0f, liftHeight * lift)
    scale(squashX, squashY, pivot = tip) {
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
        val white = r * PIN_OUTLINE
        val hair = maxOf(r * 0.07f, 1f)
        // Outline: dark hairline, then the thick white band (the fill covers each stroke's inner half).
        drawPath(body, Color(0xFF0B1020).copy(alpha = 0.55f * alpha), style = Stroke(2 * (white + hair), join = StrokeJoin.Round))
        drawPath(body, Color.White.copy(alpha = alpha), style = Stroke(2 * white, join = StrokeJoin.Round))

        val light = color.lighten(0.42f)
        val dark = color.darken(0.42f)
        drawPath(
            body,
            Brush.radialGradient(
                listOf(light.copy(alpha = alpha), color.copy(alpha = alpha), dark.copy(alpha = alpha)),
                center = c + Offset(-r * 0.38f, -r * 0.42f), radius = r * 2.3f,
            ),
        )
        // Bevel: a darker rim inside the body edge.
        drawPath(body, dark.darken(0.25f).copy(alpha = 0.55f * alpha), style = Stroke(r * 0.08f, join = StrokeJoin.Round))

        // White collar around a recessed well, then the coloured centre dot.
        drawCircle(Color.White.copy(alpha = alpha), r * 0.62f, c)
        drawCircle(
            Brush.verticalGradient(listOf(color.darken(0.45f).copy(alpha = alpha), color.darken(0.1f).copy(alpha = alpha)),
                startY = c.y - r * 0.5f, endY = c.y + r * 0.5f),
            r * 0.48f, c,
        )
        drawCircle(
            Brush.radialGradient(listOf(color.lighten(0.3f).copy(alpha = alpha), color.copy(alpha = alpha)),
                center = c - Offset(r * 0.1f, r * 0.12f), radius = r * 0.4f),
            r * 0.36f, c + Offset(0f, r * 0.03f),
        )
        if (glyph != null) drawGlyph(glyph, c, r * 0.28f, Color.White.copy(alpha = 0.95f * alpha))
        else drawCircle(Color.White.copy(alpha = 0.6f * alpha), r * 0.08f, c - Offset(r * 0.12f, r * 0.13f))

        // Curved specular highlight along the top-left of the head.
        drawArc(
            Color.White.copy(alpha = 0.7f * alpha), startAngle = 192f, sweepAngle = 70f, useCenter = false,
            topLeft = c - Offset(r * 0.8f, r * 0.8f), size = Size(r * 1.6f, r * 1.6f),
            style = Stroke(r * 0.14f, cap = StrokeCap.Round),
        )
    }
}

/** Height of a pin above its ground point, outline included (for hit-testing and stacking). */
fun pinHeight(r: Float) = r * (2.55f + PIN_OUTLINE)
