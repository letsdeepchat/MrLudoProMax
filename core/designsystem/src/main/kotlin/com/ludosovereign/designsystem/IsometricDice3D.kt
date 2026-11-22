package com.ludosovereign.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
}

/** A cube face: outward normal plus two in-plane axes for placing pips. */
private class Face(val n: V3, val u: V3, val v: V3, val slot: Int)

private val FACES = listOf(
    Face(V3(0f, 1f, 0f), V3(1f, 0f, 0f), V3(0f, 0f, -1f), 0), // top
    Face(V3(0f, -1f, 0f), V3(1f, 0f, 0f), V3(0f, 0f, 1f), 1), // bottom
    Face(V3(0f, 0f, 1f), V3(1f, 0f, 0f), V3(0f, 1f, 0f), 2), // front
    Face(V3(0f, 0f, -1f), V3(-1f, 0f, 0f), V3(0f, 1f, 0f), 3), // back
    Face(V3(1f, 0f, 0f), V3(0f, 0f, -1f), V3(0f, 1f, 0f), 4), // right
    Face(V3(-1f, 0f, 0f), V3(0f, 0f, 1f), V3(0f, 1f, 0f), 5), // left
)

private val PIPS: Map<Int, List<Pair<Float, Float>>> = mapOf(
    1 to listOf(0f to 0f),
    2 to listOf(-0.5f to 0.5f, 0.5f to -0.5f),
    3 to listOf(-0.5f to 0.5f, 0f to 0f, 0.5f to -0.5f),
    4 to listOf(-0.5f to 0.5f, 0.5f to 0.5f, -0.5f to -0.5f, 0.5f to -0.5f),
    5 to listOf(-0.5f to 0.5f, 0.5f to 0.5f, 0f to 0f, -0.5f to -0.5f, 0.5f to -0.5f),
    6 to listOf(-0.5f to 0.55f, 0.5f to 0.55f, -0.5f to 0f, 0.5f to 0f, -0.5f to -0.55f, 0.5f to -0.55f),
)

/** Values on [top, bottom, front, back, right, left] for a die showing [top]; opposite faces sum to 7. */
private fun faceValues(top: Int): IntArray {
    val t = top.coerceIn(1, 6)
    val sides = (1..6).filter { it != t && it != 7 - t }
    val front = sides[0]
    val right = sides.first { it != front && it != 7 - front }
    return intArrayOf(t, 7 - t, front, 7 - front, right, 7 - right)
}

/**
 * Isometric 3-D die drawn on a Canvas (no 3-D engine, no assets). While [rolling] it tumbles with
 * flickering faces; when it settles it springs into the classic isometric pose with [value] on top.
 */
@Composable
fun IsometricDice3D(
    value: Int,
    rolling: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pipColor: Color = Color(0xFF1B1B1B),
    contentDescription: String = "Dice",
    onClick: () -> Unit = {},
) {
    val yaw = remember { Animatable(45f) }
    val pitch = remember { Animatable(30f) }
    var shown by remember { mutableIntStateOf(value.coerceIn(1, 6)) }

    LaunchedEffect(rolling) {
        if (rolling) {
            launch { while (true) { shown = Random.nextInt(1, 7); delay(70) } }
            launch { while (true) { yaw.animateTo(yaw.value + 360f, tween(380, easing = LinearEasing)) } }
            while (true) { pitch.animateTo(pitch.value + 360f, tween(520, easing = LinearEasing)) }
        }
    }
    LaunchedEffect(rolling, value) {
        if (!rolling && value in 1..6) {
            shown = value
            val targetYaw = (Math.round((yaw.value - 45f) / 360f) * 360f) + 45f
            val targetPitch = (Math.round((pitch.value - 30f) / 360f) * 360f) + 30f
            launch { yaw.animateTo(targetYaw, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            pitch.animateTo(targetPitch, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }
    }

    Canvas(
        modifier
            .semantics { this.contentDescription = "$contentDescription $value" }
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        drawDie(shown, Math.toRadians(yaw.value.toDouble()).toFloat(), Math.toRadians(pitch.value.toDouble()).toFloat(), color, pipColor, enabled)
    }
}

private fun DrawScope.drawDie(top: Int, yaw: Float, pitch: Float, body: Color, pip: Color, enabled: Boolean) {
    val s = size.minDimension * 0.30f
    val c = Offset(size.width / 2, size.height / 2)
    val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
    fun rot(p: V3): V3 {
        val x1 = p.x * cy + p.z * sy
        val z1 = -p.x * sy + p.z * cy
        val y2 = p.y * cp - z1 * sp
        val z2 = p.y * sp + z1 * cp
        return V3(x1, y2, z2)
    }
    fun proj(p: V3) = Offset(c.x + p.x * s, c.y - p.y * s)
    val light = V3(-0.4f, 0.7f, 0.6f)
    val values = faceValues(top)
    val visible = FACES.map { it to rot(it.n) }.filter { it.second.z > 0.01f }.sortedBy { it.second.z }

    // Soft shadow
    drawOval(Color.Black.copy(alpha = 0.18f), topLeft = Offset(c.x - s * 1.3f, c.y + s * 1.15f), size = androidx.compose.ui.geometry.Size(s * 2.6f, s * 0.5f))

    for ((face, n) in visible) {
        val corners = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (a, b) -> proj(rot(face.n + face.u * a + face.v * b)) }
        val shade = (0.55f + 0.45f * (n.x * light.x + n.y * light.y + n.z * light.z).coerceIn(0f, 1f))
        val fill = lerp(Color.Black, body, shade).let { if (enabled) it else lerp(it, Color.Gray, 0.5f) }
        val path = Path().apply {
            moveTo(corners[0].x, corners[0].y); corners.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        drawPath(path, fill)
        drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(width = s * 0.04f))
        val count = values[face.slot]
        PIPS.getValue(count).forEach { (a, b) ->
            val center3 = face.n + face.u * (a * 1.15f) + face.v * (b * 1.15f)
            val pts = (0 until 10).map { k ->
                val t = (k / 10f) * 2f * Math.PI.toFloat()
                proj(rot(center3 + face.u * (cos(t) * 0.2f) + face.v * (sin(t) * 0.2f)))
            }
            val pipPath = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(pipPath, pip.copy(alpha = 0.35f + 0.65f * n.z))
        }
    }
}
