package com.ludosovereign.feature.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ludosovereign.designsystem.BoardTheme
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.drawToken
import com.ludosovereign.designsystem.starPath
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.geometry.BoardGeometry
import com.ludosovereign.engine.geometry.Pt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private data class Rest(val pos: Offset, val scale: Float)

/** Resting layout: tokens sharing a point are fanned out in a split-cell arrangement and shrunk. */
private fun restingLayout(g: GameState, geo: BoardGeometry): List<Rest> {
    val tpp = g.tokensPerPlayer
    val raw = g.tokens.indices.map { id ->
        val owner = id / tpp
        geo.tokenPoint(g.seatOf(owner), g.tokens[id], id % tpp)
    }
    val groups = raw.indices.groupBy { raw[it].let { p -> (p.x * 10).toInt() to (p.y * 10).toInt() } }
    val out = arrayOfNulls<Rest>(raw.size)
    for ((_, ids) in groups) {
        val n = ids.size
        ids.forEachIndexed { k, id ->
            val p = raw[id]
            if (n == 1 || g.tokens[id] == BASE) out[id] = Rest(Offset(p.x, p.y), 1f)
            else {
                val a = 2 * PI * k / n - PI / 4
                val r = if (n == 2) 0.2f else 0.24f
                out[id] = Rest(Offset(p.x + (cos(a) * r).toFloat(), p.y + (sin(a) * r).toFloat()), if (n == 2) 0.72f else 0.6f)
            }
        }
    }
    return out.map { it!! }
}

private class Burst(val center: Offset, val color: Color, val seed: Int) {
    val progress = Animatable(0f)
}

/**
 * Custom Compose Canvas renderer for both Ludo topologies. Board geometry comes from the pure
 * [BoardGeometry]; this composable only scales it, animates tokens cell-by-cell along their path,
 * draws Fortress shield rings, and spawns capture particles.
 */
@Composable
fun BoardCanvas(
    game: GameState,
    events: List<GameEvent>,
    eventSeq: Long,
    awaitingHuman: Boolean,
    animationSpeed: Float,
    onTokenTap: (Int) -> Unit,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    val geo = remember(game.topology) { BoardGeometry(game.topology) }
    val layout = remember(game) { restingLayout(game, geo) }
    val anims = remember(game.config) { layout.map { Animatable(it.pos, Offset.VectorConverter) } }
    val scales = remember(game.config) { layout.map { Animatable(it.scale) } }
    var prev by remember(game.config) { mutableStateOf(game.tokens) }
    val bursts = remember { mutableStateListOf<Burst>() }
    val fortressPulse = remember { Animatable(0f) }

    val playerColors = game.config.players.map { theme.tokenColor(it.colorIndex, colorblind) }
    val seatColor = { seat: Int ->
        val p = game.config.seats.indexOf(seat)
        if (p >= 0) playerColors[p] else theme.cell
    }

    LaunchedEffect(game) {
        val stepMs = (110 / animationSpeed).toLong().coerceIn(30, 250)
        val tpp = game.tokensPerPlayer
        val captured = events.filterIsInstance<GameEvent.Captured>().map { it.victimTokenId }.toSet()
        for (id in game.tokens.indices) {
            val from = prev[id]
            val to = game.tokens[id]
            if (from == to) {
                launch { anims[id].animateTo(layout[id].pos, tween(160)) }
                launch { scales[id].animateTo(layout[id].scale, tween(160)) }
                continue
            }
            val owner = id / tpp
            launch {
                if (id in captured) delay(stepMs * 3) // let the attacker land first
                scales[id].animateTo(1f, tween(80))
                val path = geo.path(game.seatOf(owner), from, to, id % tpp)
                val per = if (to == BASE) stepMs * 4 else stepMs
                for ((i, p) in path.withIndex()) {
                    anims[id].animateTo(Offset(p.x, p.y), tween(per.toInt(), easing = LinearEasing))
                    if (to != BASE && i < path.lastIndex) onStep()
                }
                anims[id].animateTo(layout[id].pos, tween(120))
                scales[id].animateTo(layout[id].scale, tween(120))
            }
        }
        prev = game.tokens
    }

    LaunchedEffect(eventSeq) {
        for (e in events) when (e) {
            is GameEvent.Captured -> {
                val p = geo.trackCell(e.cell)
                val b = Burst(Offset(p.x, p.y), playerColors[game.ownerOf(e.victimTokenId)], e.victimTokenId * 31 + eventSeq.toInt())
                bursts += b
                launch { delay(250); b.progress.animateTo(1f, tween(700)); bursts.remove(b) }
            }
            is GameEvent.FortressFormed -> launch { fortressPulse.snapTo(1f); fortressPulse.animateTo(0f, tween(900)) }
            else -> Unit
        }
    }

    val glow by rememberInfiniteTransition(label = "legal").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow",
    )

    val movable = remember(game, awaitingHuman) {
        if (!awaitingHuman || game.phase != Phase.AWAITING_MOVE) emptySet()
        else game.tokens.indices.filter { LudoEngine.isLegal(game, it) }.toSet()
    }

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = "Ludo board, ${game.config.playerCount} players" }
            .pointerInput(movable) {
                detectTapGestures { tap ->
                    if (movable.isEmpty()) return@detectTapGestures
                    val scale = size.width / (2 * geo.extent)
                    val bx = (tap.x - size.width / 2f) / scale
                    val by = (tap.y - size.height / 2f) / scale
                    movable.minByOrNull { hypot(anims[it].value.x - bx, anims[it].value.y - by) }
                        ?.takeIf { hypot(anims[it].value.x - bx, anims[it].value.y - by) < 0.9f }
                        ?.let(onTokenTap)
                }
            },
    ) {
        val unit = size.minDimension / (2 * geo.extent)
        val origin = Offset(size.width / 2, size.height / 2)
        fun px(p: Pt) = origin + Offset(p.x * unit, p.y * unit)
        fun px(o: Offset) = origin + o * unit

        drawRoundRect(theme.boardBase, cornerRadius = CornerRadius(unit * 0.8f))
        drawBoard(game, geo, theme, unit, ::px, seatColor)

        // Fortress shield rings (main track only).
        val tpp = game.tokensPerPlayer
        val cellsSeen = HashSet<Pair<Int, Int>>()
        for (id in game.tokens.indices) {
            val cell = game.cellOf(id)
            if (cell < 0) continue
            val team = game.teamOf(id / tpp)
            if (!cellsSeen.add(cell to team) || !game.isFortress(cell, team)) continue
            val c = px(geo.trackCell(cell))
            val r = unit * (0.58f + 0.12f * fortressPulse.value)
            drawCircle(Brush.sweepGradient(theme.fortressMetal + theme.fortressMetal.first(), c), r, c, style = Stroke(unit * 0.11f))
            drawCircle(Color.Black.copy(alpha = 0.35f), r + unit * 0.06f, c, style = Stroke(unit * 0.02f))
        }

        // Tokens (draw moving/raised tokens last).
        val order = game.tokens.indices.sortedBy { if (it in movable) 1 else 0 }
        for (id in order) {
            val owner = id / tpp
            val glyph = BoardThemes.GLYPHS[game.seatOf(owner) % BoardThemes.GLYPHS.size]
            drawToken(
                px(anims[id].value), unit * 0.36f * scales[id].value, playerColors[owner], glyph,
                highlight = id in movable && glow > 0.6f,
            )
        }

        // Capture particles.
        for (b in bursts) {
            val t = b.progress.value
            val rnd = Random(b.seed)
            repeat(18) {
                val a = rnd.nextFloat() * 2 * PI.toFloat()
                val speed = 0.6f + rnd.nextFloat() * 1.4f
                val p = b.center + Offset(cos(a), sin(a)) * (speed * t * 1.6f)
                drawCircle(b.color.copy(alpha = (1 - t).coerceIn(0f, 1f)), unit * 0.09f * (1 - t * 0.5f), px(p))
            }
        }
    }
}

private fun DrawScope.drawBoard(
    game: GameState,
    geo: BoardGeometry,
    theme: BoardTheme,
    unit: Float,
    px: (Pt) -> Offset,
    seatColor: (Int) -> Color,
) {
    val topo = game.topology
    // Yards
    for (seat in 0 until topo.seats) {
        val c = px(geo.yardCenter(seat))
        val col = seatColor(seat)
        drawCircle(col.copy(alpha = 0.28f), geo.yardRadius * unit, c)
        drawCircle(col.copy(alpha = 0.9f), geo.yardRadius * unit, c, style = Stroke(unit * 0.08f))
        for (k in 0..3) drawCircle(theme.cell.copy(alpha = 0.85f), unit * 0.42f, px(geo.yardSlot(seat, k)))
    }
    // Centre (Home): one wedge per seat.
    val poly = geo.centerPolygon()
    val center = px(Pt(0f, 0f))
    for (seat in 0 until topo.seats) {
        val a = px(poly[(seat - 1 + topo.seats) % topo.seats]); val b = px(poly[seat])
        val path = Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
        drawPath(path, seatColor(seat).copy(alpha = 0.75f))
        drawPath(path, theme.cellBorder.copy(alpha = 0.6f), style = Stroke(unit * 0.03f))
    }
    // Track and stretches, each cell rotated to its arm.
    val cellSize = Size(unit * 0.96f, unit * 0.96f)
    fun cellRect(p: Offset, arm: Int, fill: Color) {
        val deg = Math.toDegrees(geo.armAngle(arm)).toFloat() - 90f
        rotate(deg, p) {
            drawRoundRect(fill, topLeft = p - Offset(cellSize.width / 2, cellSize.height / 2), size = cellSize, cornerRadius = CornerRadius(unit * 0.12f))
            drawRoundRect(theme.cellBorder.copy(alpha = 0.55f), topLeft = p - Offset(cellSize.width / 2, cellSize.height / 2), size = cellSize,
                cornerRadius = CornerRadius(unit * 0.12f), style = Stroke(unit * 0.03f))
        }
    }
    for (cell in 0 until topo.trackLength) {
        val (arm, _, _) = geo.laneDepthOf(cell)
        val p = px(geo.trackCell(cell))
        val startSeat = (0 until topo.seats).firstOrNull { topo.startCell(it) == cell }
        val fill = when {
            startSeat != null -> seatColor(startSeat).copy(alpha = 0.85f)
            topo.isSafeCell(cell) -> theme.safeCell
            else -> theme.cell
        }
        cellRect(p, arm, fill)
        if (topo.isSafeCell(cell)) drawPath(starPath(p, unit * 0.3f), theme.cellBorder.copy(alpha = 0.55f))
    }
    for (seat in 0 until topo.seats) for (i in 0..4) cellRect(px(geo.stretchCell(seat, i)), seat, seatColor(seat).copy(alpha = 0.6f))
}
