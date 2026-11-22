package com.ludosovereign.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private val PIP_LAYOUT = mapOf(
    1 to listOf(0f to 0f),
    2 to listOf(-1f to -1f, 1f to 1f),
    3 to listOf(-1f to -1f, 0f to 0f, 1f to 1f),
    4 to listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f),
    5 to listOf(-1f to -1f, 1f to -1f, 0f to 0f, -1f to 1f, 1f to 1f),
    6 to listOf(-1f to -1f, 1f to -1f, -1f to 0f, 1f to 0f, -1f to 1f, 1f to 1f),
)

/**
 * Crisp rounded die with bevel shading and recessed pips, sized to sit inside a corner dice box.
 *
 * When [rolling] turns true it tumbles for [rollMs]: 3-D spin on all axes, flickering faces and a
 * 1.0 → 1.15 → 1.0 scale bounce. When it stops it springs flat onto [value] and flashes a short
 * highlight pulse. The session keeps the tumble and the dice value in step, so the face shown is
 * always the one that was rolled.
 */
@Composable
fun PodDice(
    value: Int,
    rolling: Boolean,
    modifier: Modifier = Modifier,
    rollMs: Long = 380,
    enabled: Boolean = true,
    dimmed: Boolean = false,
    body: Color = Color.White,
    pip: Color = Color(0xFF1C1C1C),
    accent: Color = Color(0xFFFFC62F),
    contentDescription: String = "Dice",
    onClick: () -> Unit = {},
) {
    var shown by remember { mutableIntStateOf(value.coerceIn(1, 6)) }
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val rz = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(rolling) {
        if (!rolling) return@LaunchedEffect
        val ms = rollMs.toInt().coerceAtLeast(120)
        launch { while (true) { shown = Random.nextInt(1, 7); delay(55) } }
        launch { scale.animateTo(1f, keyframes { durationMillis = ms; 1.15f at ms / 2; 1f at ms }) }
        launch { rx.animateTo(rx.value + 540f, tween(ms, easing = FastOutSlowInEasing)) }
        launch { ry.animateTo(ry.value - 360f, tween(ms, easing = FastOutSlowInEasing)) }
        rz.animateTo(rz.value + 200f, tween(ms, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(rolling, value) {
        if (rolling || value !in 1..6) return@LaunchedEffect
        shown = value
        val settle = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        launch { rx.animateTo((Math.round(rx.value / 360f) * 360f), settle) }
        launch { ry.animateTo((Math.round(ry.value / 360f) * 360f), settle) }
        launch { rz.animateTo((Math.round(rz.value / 360f) * 360f), settle) }
        launch { scale.animateTo(1f, settle) }
        pulse.snapTo(1f); pulse.animateTo(0f, tween(420))
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "press")
    Canvas(
        modifier
            .semantics { this.contentDescription = "$contentDescription $value"; role = Role.Button }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer {
                rotationX = rx.value; rotationY = ry.value; rotationZ = rz.value
                val k = scale.value * (1f - 0.1f * press)
                scaleX = k; scaleY = k
                cameraDistance = 12f * density
                alpha = if (dimmed) 0.45f else 1f
            },
    ) {
        drawDieFace(shown, body, pip, accent, pulse.value)
    }
}

private fun DrawScope.drawDieFace(n: Int, body: Color, pip: Color, accent: Color, pulse: Float) {
    val s = size.minDimension * 0.86f
    val tl = Offset((size.width - s) / 2, (size.height - s) / 2)
    val corner = CornerRadius(s * 0.2f)
    // Drop shadow and bevelled body.
    drawRoundRect(Color.Black.copy(alpha = 0.28f), tl + Offset(s * 0.03f, s * 0.06f), Size(s, s), corner)
    drawRoundRect(
        Brush.linearGradient(listOf(body.lighten(0.6f), body, body.darken(0.22f)), start = tl, end = tl + Offset(s, s)),
        tl, Size(s, s), corner,
    )
    val inset = s * 0.07f
    drawRoundRect(
        Brush.radialGradient(listOf(body.lighten(0.5f), body.darken(0.05f)), center = tl + Offset(s * 0.35f, s * 0.3f), radius = s),
        tl + Offset(inset, inset), Size(s - 2 * inset, s - 2 * inset), CornerRadius(s * 0.16f),
    )
    drawRoundRect(Color.Black.copy(alpha = 0.35f), tl, Size(s, s), corner, style = Stroke(s * 0.025f))
    // Recessed pips: dark well with a light lower-right rim.
    val pr = s * 0.085f
    val step = s * 0.25f
    val c = tl + Offset(s / 2, s / 2)
    PIP_LAYOUT.getValue(n.coerceIn(1, 6)).forEach { (a, b) ->
        val p = c + Offset(a * step, b * step)
        drawCircle(Color.White.copy(alpha = 0.7f), pr * 1.08f, p + Offset(pr * 0.18f, pr * 0.22f))
        drawCircle(
            Brush.radialGradient(listOf(pip.lighten(0.25f), pip), center = p + Offset(pr * 0.3f, pr * 0.3f), radius = pr * 1.2f),
            pr, p,
        )
    }
    if (pulse > 0f) {
        drawRoundRect(accent.copy(alpha = 0.9f * pulse), tl - Offset(s * 0.05f, s * 0.05f), Size(s * 1.1f, s * 1.1f),
            CornerRadius(s * 0.24f), style = Stroke(s * 0.07f))
    }
}
