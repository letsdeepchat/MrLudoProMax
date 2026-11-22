package com.ludosovereign.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PaletteCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, enabled: (T) -> Boolean = { true }) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }, enabled = enabled(o))
        }
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, supporting: String? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit, valueText: String = "${(value * 100).toInt()}%") {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange)
    }
}

/** Draws a token: shaded disc, outline, and a glyph so colour is never the only identifier. */
fun DrawScope.drawToken(
    center: Offset,
    radius: Float,
    color: Color,
    glyph: TokenGlyph,
    highlight: Boolean = false,
    alpha: Float = 1f,
) {
    if (highlight) drawCircle(Color.White.copy(alpha = 0.55f * alpha), radius * 1.45f, center)
    drawCircle(Color.Black.copy(alpha = 0.25f * alpha), radius, center + Offset(radius * 0.08f, radius * 0.14f))
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha).lighten(0.35f), color.copy(alpha = alpha), color.copy(alpha = alpha).darken(0.3f)),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f), radius = radius * 1.4f),
        radius, center,
    )
    drawCircle(Color.Black.copy(alpha = 0.55f * alpha), radius, center, style = Stroke(radius * 0.12f))
    drawGlyph(glyph, center, radius * 0.48f, Color.White.copy(alpha = 0.92f * alpha))
}

fun DrawScope.drawGlyph(glyph: TokenGlyph, c: Offset, r: Float, color: Color) {
    fun poly(n: Int, rot: Double, inner: Float? = null): Path = Path().apply {
        val pts = if (inner == null) n else n * 2
        for (i in 0 until pts) {
            val rad = if (inner != null && i % 2 == 1) r * inner else r
            val a = rot + i * 2 * PI / pts
            val x = c.x + (cos(a) * rad).toFloat(); val y = c.y + (sin(a) * rad).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    when (glyph) {
        TokenGlyph.CIRCLE -> drawCircle(color, r * 0.75f, c)
        TokenGlyph.TRIANGLE -> drawPath(poly(3, -PI / 2), color)
        TokenGlyph.SQUARE -> drawPath(poly(4, PI / 4), color)
        TokenGlyph.DIAMOND -> drawPath(poly(4, 0.0), color)
        TokenGlyph.STAR -> drawPath(poly(5, -PI / 2, inner = 0.45f), color)
        TokenGlyph.HEXAGON -> drawPath(poly(6, 0.0), color)
    }
}

fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, alpha)
fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

/** Five-point star path (safe tiles). */
fun starPath(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = -PI / 2 + i * PI / 5
        val x = c.x + (cos(a) * rad).toFloat(); val y = c.y + (sin(a) * rad).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
