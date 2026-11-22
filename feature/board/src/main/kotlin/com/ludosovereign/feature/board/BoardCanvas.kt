package com.ludosovereign.feature.board

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ludosovereign.designsystem.BoardTheme
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.drawPin
import com.ludosovereign.designsystem.starPath
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.geometry.BoardGeometry
import com.ludosovereign.engine.geometry.Pt
import com.ludosovereign.session.AnimationTimings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Path Coordinate Resolver for the screen: the pure [BoardGeometry] gives every yard slot, track
 * cell, home-stretch cell and Home point in board units; this adds the view rotation that puts
 * seat 0 in the top-left yard (cross board) or yards above/below the hexagon (hex board).
 */
@Stable
class BoardView(val geo: BoardGeometry) {
    private val rho = if (geo.seats == 4) PI / 2 else PI / 6
    private val cr = cos(rho).toFloat()
    private val sr = sin(rho).toFloat()
    val extent: Float get() = geo.extent

    fun v(p: Pt): Offset = Offset(p.x * cr - p.y * sr, p.x * sr + p.y * cr)

    /** Rotation (degrees) that aligns a cell with its arm on screen. */
    fun armDeg(seat: Int): Float = Math.toDegrees(geo.armAngle(seat) + rho).toFloat() - 90f

    /** Unit vector pointing from the centre out along [seat]'s arm, on screen. */
    fun armDir(seat: Int): Offset = (geo.armAngle(seat) + rho).let { Offset(cos(it).toFloat(), sin(it).toFloat()) }

    fun yard(seat: Int): Offset = v(geo.yardCenter(seat))

    /** Exact resting point of a token, before stacking offsets. */
    fun tokenPoint(seat: Int, progress: Int, localIndex: Int): Offset = v(geo.tokenPoint(seat, progress, localIndex))

    /** Ordered hop targets [step_1 .. step_N] for a move; empty for releases (they arc instead). */
    fun hopPath(seat: Int, from: Int, to: Int, localIndex: Int): List<Offset> =
        if (from == BASE || to == BASE) emptyList() else geo.path(seat, from, to, localIndex).map(::v)
}

internal data class Rest(val pos: Offset, val scale: Float)

/**
 * Resting layout. Tokens that share a tile shrink (0.72× for two, 0.68× for three or four) and sit
 * side by side or in a 2×2 grid inside the tile, so stacks and Fortresses never spill into
 * neighbouring cells.
 */
internal fun restingLayout(g: GameState, view: BoardView): List<Rest> {
    val tpp = g.tokensPerPlayer
    val raw = g.tokens.indices.map { id -> view.tokenPoint(g.seatOf(id / tpp), g.tokens[id], id % tpp) }
    val out = arrayOfNulls<Rest>(raw.size)
    val groups = raw.indices.groupBy { (raw[it].x * 20).toInt() to (raw[it].y * 20).toInt() }
    for ((_, ids) in groups) {
        val n = ids.size
        ids.sortedBy { it }.forEachIndexed { k, id ->
            val p = raw[id]
            val home = g.topology.isHome(g.tokens[id])
            out[id] = when {
                g.tokens[id] == BASE -> Rest(p, 1f)
                home -> Rest(p, 0.62f)
                n == 1 -> Rest(p, 1f)
                n == 2 -> Rest(p + Offset(if (k == 0) -0.2f else 0.2f, 0f), 0.72f)
                n <= 4 -> Rest(p + Offset(if (k % 2 == 0) -0.2f else 0.2f, if (k < 2) -0.16f else 0.18f), 0.68f)
                else -> Rest(p + Offset(((k % 3) - 1) * 0.26f, if (k < 3) -0.16f else 0.18f), 0.56f)
            }
        }
    }
    return out.map { it!! }
}

/** Mutable visual state of one pawn, driven tile by tile by the hop engine. */
@Stable
private class TokenVisual(start: Rest) {
    var pos by mutableStateOf(start.pos)
    var lift by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(start.scale)
}

private class Burst(val center: Offset, val color: Color, val seed: Int, val star: Boolean) {
    var t by mutableFloatStateOf(0f)
}

private class Flash(val center: Offset) {
    var t by mutableFloatStateOf(0f)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Hop parabola: offsetY = -sin(PI·t) · tile · 0.45, expressed as lift in tiles. */
private fun hopLift(t: Float) = sin(PI * t).toFloat() * 0.45f

/** Scale at the apex: 1.0 → 1.22 → 1.0. */
private fun hopScale(t: Float, base: Float) = base * (1f + 0.22f * sin(PI * t).toFloat())

/**
 * Board renderer with a decoupled token layer. Tokens live at floating-point positions and are
 * animated by a sequential hop engine: one parabolic hop per tile with an apex scale-up and a
 * ground shadow, a curved arc out of the yard, an impact flash then a retreat arc for captures,
 * and a starburst at Home. All lengths come from [timings], the same numbers the session waits on.
 */
@Composable
fun BoardCanvas(
    game: GameState,
    events: List<GameEvent>,
    eventSeq: Long,
    selectable: Set<Int>,
    activeSeat: Int,
    timings: AnimationTimings,
    onTokenTap: (Int) -> Unit,
    onHop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    val view = remember(game.topology) { BoardView(BoardGeometry(game.topology)) }
    val layout = remember(game) { restingLayout(game, view) }
    val visuals = remember(game.config) { layout.map { TokenVisual(it) } }
    var prevTokens by remember(game.config) { mutableStateOf(game.tokens) }
    val bursts = remember { mutableStateListOf<Burst>() }
    val flashes = remember { mutableStateListOf<Flash>() }
    var shake by remember { mutableFloatStateOf(0f) }
    var fortressPulse by remember { mutableFloatStateOf(0f) }
    val hop by rememberUpdatedState(onHop)

    val tpp = game.tokensPerPlayer
    val playerColors = game.config.players.map { theme.tokenColor(it.colorIndex, colorblind) }
    val seatColor = { seat: Int ->
        val p = game.config.seats.indexOf(seat)
        if (p >= 0) playerColors[p] else lerp(theme.tokens[seat % theme.tokens.size], Color(0xFFBDBDBD), 0.75f)
    }

    // ---- Hop engine -----------------------------------------------------------------------------
    LaunchedEffect(game) {
        val t = timings
        val evs = events
        val moverId = evs.firstNotNullOfOrNull {
            when (it) { is GameEvent.TokenMoved -> it.tokenId; is GameEvent.TokenReleased -> it.tokenId; else -> null }
        }
        val victims = evs.filterIsInstance<GameEvent.Captured>()
        val victimIds = victims.map { it.victimTokenId }.toSet()
        val settle = tween<Float>(120, easing = FastOutSlowInEasing)

        suspend fun glide(v: TokenVisual, to: Rest, ms: Int = 160) {
            val from = v.pos; val s0 = v.scale; val l0 = v.lift
            animate(0f, 1f, animationSpec = tween(ms, easing = FastOutSlowInEasing)) { x, _ ->
                v.pos = lerp(from, to.pos, x); v.scale = s0 + (to.scale - s0) * x; v.lift = l0 * (1 - x)
            }
        }
        suspend fun arc(v: TokenVisual, to: Rest, ms: Long, height: Float) {
            val from = v.pos; val s0 = v.scale
            animate(0f, 1f, animationSpec = tween(ms.toInt(), easing = FastOutSlowInEasing)) { x, _ ->
                v.pos = lerp(from, to.pos, x)
                v.lift = sin(PI * x).toFloat() * height
                v.scale = (s0 + (to.scale - s0) * x) * (1f + 0.15f * sin(PI * x).toFloat())
            }
            v.lift = 0f
        }

        coroutineScope {
            // Everyone not moving this turn eases into the new stacking layout.
            for (id in game.tokens.indices) if (id != moverId && id !in victimIds) launch { glide(visuals[id], layout[id]) }

            if (moverId != null) {
                val v = visuals[moverId]
                val owner = moverId / tpp
                val seat = game.seatOf(owner)
                val from = prevTokens[moverId]
                val to = game.tokens[moverId]
                if (from == BASE) {
                    arc(v, layout[moverId].copy(scale = 1f), t.exitMs, 0.9f)
                    hop()
                } else {
                    for (target in view.hopPath(seat, from, to, moverId % tpp)) {
                        val a = v.pos
                        animate(0f, 1f, animationSpec = tween(t.hopMs.toInt(), easing = LinearEasing)) { x, _ ->
                            v.pos = lerp(a, target, x); v.lift = hopLift(x); v.scale = hopScale(x, 1f)
                        }
                        v.lift = 0f; v.scale = 1f
                        hop() // one percussive tap per tile landed
                    }
                }
                if (victims.isNotEmpty()) {
                    val hit = view.v(view.geo.trackCell(victims.first().cell))
                    val f = Flash(hit); flashes += f
                    animate(0f, 1f, animationSpec = tween(t.impactMs.toInt(), easing = LinearEasing)) { x, _ ->
                        f.t = x; shake = sin(x * PI.toFloat() * 6f) * (1 - x) * 0.12f
                    }
                    shake = 0f; flashes.remove(f)
                    coroutineScope {
                        for (vic in victimIds) launch { arc(visuals[vic], layout[vic], t.retreatMs, 1.6f) }
                    }
                }
                if (evs.any { it is GameEvent.TokenReachedHome }) {
                    val b = Burst(layout[moverId].pos, playerColors[owner], moverId * 7 + eventSeq.toInt(), star = true)
                    bursts += b
                    launch { glide(v, layout[moverId], 120) }
                    animate(0f, 1f, animationSpec = tween(t.homeBurstMs.toInt(), easing = LinearEasing)) { x, _ -> b.t = x }
                    bursts.remove(b)
                } else {
                    glide(v, layout[moverId], 110)
                }
            }
            if (evs.any { it is GameEvent.FortressFormed }) {
                launch { animate(1f, 0f, animationSpec = settle) { x, _ -> fortressPulse = x } }
            }
        }
        prevTokens = game.tokens
    }

    val inf = rememberInfiniteTransition(label = "board")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(modifier.aspectRatio(1f)) {
    val boxW = maxWidth
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Ludo board, ${game.config.playerCount} players, ${selectable.size} movable" }
            .pointerInput(selectable) {
                detectTapGestures { tap ->
                    if (selectable.isEmpty()) return@detectTapGestures
                    val unit = size.width / (2 * view.extent)
                    val b = Offset((tap.x - size.width / 2f) / unit, (tap.y - size.height / 2f) / unit)
                    // Generous target: the whole tile plus the raised pawn above it.
                    selectable.minByOrNull { hypot(visuals[it].pos.x - b.x, visuals[it].pos.y - 0.25f - b.y) }
                        ?.takeIf { hypot(visuals[it].pos.x - b.x, visuals[it].pos.y - 0.25f - b.y) < 0.95f }
                        ?.let(onTokenTap)
                }
            },
    ) {
        val unit = size.minDimension / (2 * view.extent)
        val origin = Offset(size.width / 2, size.height / 2) + Offset(shake * unit, 0f)
        fun px(o: Offset) = origin + o * unit

        drawBoard(game, view, theme, unit, ::px, seatColor, activeSeat, pulse)

        // Fortress indicators: gold tile outline + small shield, inside the tile bounds.
        val seen = HashSet<Pair<Int, Int>>()
        for (id in game.tokens.indices) {
            val cell = game.cellOf(id)
            if (cell < 0 || !game.topology.isOnTrack(game.tokens[id])) continue
            val team = game.teamOf(id / tpp)
            if (!seen.add(cell to team) || !game.isFortress(cell, team)) continue
            val c = px(view.v(view.geo.trackCell(cell)))
            val (arm, _, _) = view.geo.laneDepthOf(cell)
            drawFortress(c, unit, view.armDeg(arm), theme, fortressPulse)
        }

        // Selection rings under movable pawns.
        for (id in selectable) {
            val g = px(visuals[id].pos)
            rotate(spin, g) {
                drawCircle(theme.accent.copy(alpha = 0.35f + 0.4f * pulse), unit * 0.46f, g,
                    style = Stroke(unit * 0.09f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.28f, unit * 0.16f))))
            }
            drawCircle(Color.White.copy(alpha = 0.25f * pulse), unit * 0.4f, g)
        }

        // Pawns, back to front; moving / selectable ones on top.
        val order = game.tokens.indices.sortedWith(compareBy({ visuals[it].lift > 0f || it in selectable }, { visuals[it].pos.y }))
        for (id in order) {
            val v = visuals[id]
            val owner = id / tpp
            val r = unit * 0.3f * v.scale
            val raise = if (id in selectable) 0.18f + 0.08f * pulse else 0f
            val ground = px(v.pos + Offset(0f, 0.34f * v.scale))
            drawPin(
                ground, r, playerColors[owner],
                lift = ((v.lift + raise) * unit) / (r * 2.2f),
                glyph = if (colorblind) BoardThemes.GLYPHS[game.seatOf(owner) % BoardThemes.GLYPHS.size] else null,
            )
        }

        // Capture impact flash.
        for (f in flashes) {
            val c = px(f.center)
            drawCircle(Color.White.copy(alpha = 0.85f * (1 - f.t)), unit * (0.3f + 0.7f * f.t), c)
            drawCircle(Color(0xFFFF5252).copy(alpha = 0.9f * (1 - f.t)), unit * (0.35f + 0.8f * f.t), c, style = Stroke(unit * 0.08f))
        }
        // Home starburst.
        for (b in bursts) drawBurst(px(b.center), unit, b, theme)
    }
    // Accessible, oversized tap targets over each selectable pawn (TalkBack can pick pawns too).
    val unitDp = boxW / (2 * view.extent)
    for (id in selectable) {
        val p = visuals[id].pos
        Box(
            Modifier
                .offset(unitDp * (view.extent + p.x - 0.6f), unitDp * (view.extent + p.y - 0.85f))
                .size(unitDp * 1.2f, unitDp * 1.35f)
                .semantics { contentDescription = "Movable pawn ${id + 1}" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTokenTap(id) },
        )
    }
    }
}

private fun DrawScope.drawBurst(c: Offset, unit: Float, b: Burst, theme: BoardTheme) {
    val t = b.t
    val rnd = Random(b.seed)
    val fade = (1 - t).coerceIn(0f, 1f)
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6 + t * 0.6f
        val r0 = unit * (0.2f + 0.4f * t); val r1 = unit * (0.5f + 1.4f * t)
        drawLine(theme.accent.copy(alpha = fade), c + Offset(cos(a), sin(a)) * r0, c + Offset(cos(a), sin(a)) * r1,
            strokeWidth = unit * 0.07f, cap = StrokeCap.Round)
    }
    repeat(22) {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val sp = 0.6f + rnd.nextFloat() * 1.6f
        val p = c + Offset(cos(a), sin(a)) * (sp * t * 1.8f * unit)
        drawCircle((if (it % 2 == 0) b.color else Color.White).copy(alpha = fade), unit * 0.08f * (1 - 0.4f * t), p)
    }
}

private fun DrawScope.drawFortress(c: Offset, unit: Float, deg: Float, theme: BoardTheme, pulse: Float) {
    val s = unit * 0.9f
    rotate(deg, c) {
        drawRoundRect(
            Brush.sweepGradient(theme.fortressMetal + theme.fortressMetal.first(), c),
            topLeft = c - Offset(s / 2, s / 2), size = Size(s, s), cornerRadius = CornerRadius(unit * 0.14f),
            style = Stroke(unit * (0.09f + 0.06f * pulse)),
        )
    }
    // Small shield in the tile's top-right corner.
    val sc = c + Offset(unit * 0.3f, -unit * 0.3f)
    val w = unit * 0.13f
    val shield = Path().apply {
        moveTo(sc.x - w, sc.y - w * 1.1f); lineTo(sc.x + w, sc.y - w * 1.1f); lineTo(sc.x + w, sc.y + w * 0.1f)
        quadraticTo(sc.x + w * 0.9f, sc.y + w * 1.0f, sc.x, sc.y + w * 1.4f)
        quadraticTo(sc.x - w * 0.9f, sc.y + w * 1.0f, sc.x - w, sc.y + w * 0.1f); close()
    }
    drawPath(shield, theme.fortressMetal[2])
    drawPath(shield, theme.fortressMetal[3], style = Stroke(unit * 0.03f))
}

private fun DrawScope.drawBoard(
    game: GameState,
    view: BoardView,
    theme: BoardTheme,
    unit: Float,
    px: (Offset) -> Offset,
    seatColor: (Int) -> Color,
    activeSeat: Int,
    pulse: Float,
) {
    val topo = game.topology
    val geo = view.geo
    val cross = topo.seats == 4
    val e = view.extent
    // Board slab and frame.
    if (cross) {
        drawRoundRect(theme.boardBase, px(Offset(-e, -e)), Size(2 * e * unit, 2 * e * unit), CornerRadius(unit * 0.35f))
        drawRoundRect(theme.frame, px(Offset(-e, -e)), Size(2 * e * unit, 2 * e * unit), CornerRadius(unit * 0.35f), style = Stroke(unit * 0.16f))
    } else {
        drawCircle(theme.boardBase, e * unit * 0.99f, px(Offset.Zero))
        drawCircle(theme.frame, e * unit * 0.99f, px(Offset.Zero), style = Stroke(unit * 0.14f))
    }

    // Yards: vivid colour, white inner container, four colour-ringed spawn bases.
    for (seat in 0 until topo.seats) {
        val used = seat in game.config.seats
        val col = seatColor(seat)
        val c = px(view.yard(seat))
        val active = seat == activeSeat
        if (cross) {
            val half = 3f * unit
            val tl = c - Offset(half, half)
            drawRoundRect(col, tl, Size(2 * half, 2 * half), CornerRadius(unit * 0.3f))
            drawRoundRect(theme.frame.copy(alpha = 0.8f), tl, Size(2 * half, 2 * half), CornerRadius(unit * 0.3f), style = Stroke(unit * 0.07f))
            val inner = 2.1f * unit
            drawRoundRect(theme.yardInner.copy(alpha = if (used) 1f else 0.6f), c - Offset(inner, inner), Size(2 * inner, 2 * inner), CornerRadius(unit * 0.55f))
            if (active) {
                drawRoundRect(Color.White.copy(alpha = 0.35f + 0.55f * pulse), tl + Offset(unit * 0.12f, unit * 0.12f),
                    Size(2 * half - unit * 0.24f, 2 * half - unit * 0.24f), CornerRadius(unit * 0.25f), style = Stroke(unit * (0.14f + 0.1f * pulse)))
            }
        } else {
            val r = geo.yardRadius * unit
            drawCircle(col, r, c)
            drawCircle(theme.frame.copy(alpha = 0.8f), r, c, style = Stroke(unit * 0.07f))
            drawCircle(theme.yardInner.copy(alpha = if (used) 1f else 0.6f), r * 0.9f, c)
            if (active) drawCircle(Color.White.copy(alpha = 0.35f + 0.55f * pulse), r - unit * 0.1f, c, style = Stroke(unit * (0.14f + 0.1f * pulse)))
        }
        for (k in 0..3) {
            val s = px(view.v(geo.yardSlot(seat, k)))
            val sr = unit * (if (cross) 0.5f else 0.36f)
            drawCircle(col.copy(alpha = 0.22f), sr, s)
            drawCircle(col, sr, s, style = Stroke(unit * (if (cross) 0.12f else 0.09f)))
        }
    }

    // Centre: one wedge per seat, separated by crisp white lines.
    val poly = geo.centerPolygon().map(view::v)
    val center = px(Offset.Zero)
    for (seat in 0 until topo.seats) {
        val a = px(poly[(seat - 1 + topo.seats) % topo.seats]); val b = px(poly[seat])
        val path = Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
        drawPath(path, seatColor(seat))
        drawPath(path, theme.frame, style = Stroke(unit * 0.05f))
    }

    // Track cells: sharp borders, coloured start squares, outlined stars on every safe square.
    val cs = unit * 0.98f
    fun cell(p: Offset, arm: Int, fill: Color) {
        rotate(view.armDeg(arm), p) {
            val tl = p - Offset(cs / 2, cs / 2)
            drawRect(fill, tl, Size(cs, cs))
            drawRect(theme.cellBorder, tl, Size(cs, cs), style = Stroke(unit * 0.045f))
        }
    }
    for (c in 0 until topo.trackLength) {
        val (arm, lane, depth) = geo.laneDepthOf(c)
        val p = px(view.v(geo.trackCell(c)))
        val startSeat = (0 until topo.seats).firstOrNull { topo.startCell(it) == c }
        cell(p, arm, if (startSeat != null) seatColor(startSeat) else theme.cell)
        if (topo.isSafeCell(c)) {
            val star = starPath(p, unit * 0.32f)
            val onColour = startSeat != null
            drawPath(star, (if (onColour) Color.White else seatColor(arm)).copy(alpha = if (onColour) 0.95f else 0.22f))
            drawPath(star, if (onColour) theme.frame.copy(alpha = 0.6f) else theme.cellBorder.copy(alpha = 0.8f), style = Stroke(unit * 0.045f))
        }
        if (lane == 0 && depth == 5) {
            // Entry arrow: points from the tip into this seat's home stretch.
            val d = -view.armDir(arm)
            val n = Offset(-d.y, d.x)
            val tip = p + d * (unit * 0.34f)
            val back = p - d * (unit * 0.22f)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(back.x + n.x * unit * 0.28f, back.y + n.y * unit * 0.28f)
                lineTo(back.x - n.x * unit * 0.28f, back.y - n.y * unit * 0.28f); close()
            }
            drawPath(arrow, seatColor(arm))
            drawPath(arrow, theme.frame.copy(alpha = 0.7f), style = Stroke(unit * 0.03f))
        }
    }
    for (seat in 0 until topo.seats) for (i in 0..4) cell(px(view.v(geo.stretchCell(seat, i))), seat, seatColor(seat))
}

/** Background for the board screen: plain or a subtle geometric lattice (Classic Royal). */
fun DrawScope.drawBackdrop(theme: BoardTheme) {
    drawRect(theme.background)
    if (!theme.patterned) return
    val step = 38f * density
    val line = Color.White.copy(alpha = 0.05f)
    var x = -size.height
    while (x < size.width + size.height) {
        drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = density)
        drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = density)
        x += step
    }
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
        center = Offset(size.width / 2, size.height * 0.45f), radius = size.maxDimension * 0.6f))
}
