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

/** Distance from a pin's tip to the centre of its head, in head radii (a tall, slender pin). */
const val PIN_STEM = 2.05f

private val SHELL_LIGHT = Color(0xFFFFFFFF)
private val SHELL_MID = Color(0xFFE9ECF0)
private val SHELL_DARK = Color(0xFFAEB4BD)

/**
 * Glossy 3-D pin pawn: a white, slightly silvery teardrop shell holding a big glossy ball in the
 * player's colour. [ground] is where the tip touches the board; [lift] (0..1) raises the pawn for
 * hops while the soft contact shadow stays on the ground and shrinks/fades. [r] is the radius of
 * the shell's head; the pawn stands about 3 head radii tall.
 *
 * The white shell doubles as a high-contrast outline on coloured tiles, and a dark hairline keeps
 * it crisp on white ones. In colour-blind mode a white glyph sits inside the ball so colour is
 * never the only cue. [squashX]/[squashY] deform the pawn around its tip for squash-and-stretch.
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
    val sw = r * 0.9f * sh * squashX
    drawOval(Color.Black.copy(alpha = 0.14f * sh * alpha), Offset(ground.x - sw * 1.2f, ground.y - r * 0.3f * sh), Size(sw * 2.4f, r * 0.6f * sh))
    drawOval(Color.Black.copy(alpha = 0.28f * sh * alpha), Offset(ground.x - sw * 0.7f, ground.y - r * 0.18f * sh), Size(sw * 1.4f, r * 0.36f * sh))

    val tip = ground - Offset(0f, liftHeight * lift)
    scale(squashX, squashY, pivot = tip) {
        val h = r * PIN_STEM
        val c = tip - Offset(0f, h)
        val theta = acos(r / h) // angle between the C→tip axis and the tangent point
        val phiR = Math.toDegrees(atan2(cos(theta), sin(theta)).toDouble()).toFloat()
        val shell = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(c.x - r * sin(theta), c.y + r * cos(theta))
            arcTo(Rect(c, r), 180f - phiR, 180f + 2 * phiR, false)
            close()
        }
        // Dark hairline outside the shell, then the shell lit from the top-left.
        val hair = maxOf(r * 0.08f, 1.2f)
        drawPath(shell, Color(0xFF1A2030).copy(alpha = 0.5f * alpha), style = Stroke(2 * hair, join = StrokeJoin.Round))
        drawPath(
            shell,
            Brush.linearGradient(
                listOf(SHELL_LIGHT.copy(alpha = alpha), SHELL_MID.copy(alpha = alpha), SHELL_DARK.copy(alpha = alpha)),
                start = c + Offset(-r * 0.7f, -r * 0.7f), end = c + Offset(r * 1.0f, r * 1.6f),
            ),
        )
        // Shell gloss along the upper-left rim.
        drawArc(
            Color.White.copy(alpha = 0.95f * alpha), startAngle = 160f, sweepAngle = 95f, useCenter = false,
            topLeft = c - Offset(r * 0.84f, r * 0.84f), size = Size(r * 1.68f, r * 1.68f),
            style = Stroke(r * 0.12f, cap = StrokeCap.Round),
        )

        // The coloured ball, seated in the head.
        val br = r * 0.66f
        val bc = c + Offset(0f, r * 0.02f)
        drawCircle(Color.Black.copy(alpha = 0.18f * alpha), br * 1.04f, bc + Offset(br * 0.06f, br * 0.1f))
        drawCircle(
            Brush.radialGradient(
                listOf(color.lighten(0.4f).copy(alpha = alpha), color.copy(alpha = alpha), color.darken(0.38f).copy(alpha = alpha)),
                center = bc + Offset(-br * 0.35f, -br * 0.4f), radius = br * 1.6f,
            ),
            br, bc,
        )
        drawCircle(color.darken(0.45f).copy(alpha = 0.7f * alpha), br, bc, style = Stroke(r * 0.07f))
        if (glyph != null) drawGlyph(glyph, bc, br * 0.5f, Color.White.copy(alpha = 0.95f * alpha))
        // Specular highlight on the ball.
        drawOval(Color.White.copy(alpha = 0.75f * alpha), bc + Offset(-br * 0.62f, -br * 0.66f), Size(br * 0.62f, br * 0.4f))
    }
}

/** Height of a pin above its ground point (for hit-testing and stacking). */
fun pinHeight(r: Float) = r * (PIN_STEM + 1.1f)
