package com.ludosovereign.feature.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.ludosovereign.designsystem.darken
import com.ludosovereign.designsystem.drawPin
import com.ludosovereign.designsystem.lighten
import com.ludosovereign.designsystem.relativeLuminance
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Path Coordinate Resolver for the screen: the pure [BoardGeometry] gives every yard slot, track
 * cell, home-stretch cell and Home point in board units; this adds the view rotation that puts
 * seat 0 in the top-left yard (cross board) or yards above/below the hexagon (hex board).
 *
 * On the cross board it also re-weights the 15×15 grid: the three centre lanes are
 * [LANE_WEIGHT]× as wide as a yard column, so every road tile grows across the lane while the
 * corner yards give up the space. The grid logic is unchanged; only the drawing is weighted.
 */
@Stable
class BoardView(val geo: BoardGeometry) {
    private val rho = if (geo.seats == 4) PI / 2 else PI / 6
    private val cr = cos(rho).toFloat()
    private val sr = sin(rho).toFloat()
    val cross: Boolean = geo.seats == 4

    /** Width of an outer (yard-side) grid column, in board units. */
    val outer: Float = if (cross) 7.5f / (1.5f * LANE_WEIGHT + 6f) else 1f

    /** Width of one of the three centre lanes, in board units. */
    val lane: Float = if (cross) outer * LANE_WEIGHT else 1f

    /** A tile's shorter side; pawns and markers scale with it. */
    val tile: Float = min(outer, lane)

    val extent: Float get() = geo.extent

    /** Half-size of the drawn board including its bevelled frame. */
    val span: Float get() = geo.extent + FRAME

    private fun w(x: Float): Float {
        if (!cross) return x
        val ax = abs(x)
        val r = if (ax <= 1.5f) ax * lane else 1.5f * lane + (ax - 1.5f) * outer
        return if (x < 0) -r else r
    }

    private fun rot(p: Pt) = Offset(p.x * cr - p.y * sr, p.x * sr + p.y * cr)

    fun v(p: Pt): Offset = rot(p).let { Offset(w(it.x), w(it.y)) }

    /** Screen-space (board-unit) rectangle of the tile centred on [p]; cross board only. */
    fun tileRect(p: Pt): Rect = rot(p).let { Rect(w(it.x - 0.5f), w(it.y - 0.5f), w(it.x + 0.5f), w(it.y + 0.5f)) }

    /** The corner square a yard occupies; cross board only. */
    fun yardRect(seat: Int): Rect = rot(geo.yardCenter(seat)).let { Rect(w(it.x - 3f), w(it.y - 3f), w(it.x + 3f), w(it.y + 3f)) }

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

    companion object {
        /** Centre-lane width relative to a yard column on the cross board. */
        const val LANE_WEIGHT = 1.3f

        /** Bevelled frame thickness around the board, in board units. */
        const val FRAME = 0.18f

        /** Pin head radius as a fraction of [tile]; with its white outline a lone pawn spans ~86% of a tile. */
        const val PIN_R = 0.34f
    }
}

internal data class Rest(val pos: Offset, val scale: Float)

/**
 * Resting layout. Tokens that share a tile shrink (0.72× for two, 0.64× for three or four) and sit
 * side by side or in a 2×2 grid inside the tile, so stacks and Fortresses never spill into
 * neighbouring cells. Finished tokens take balanced spots in their Home wedge and stay large.
 */
internal fun restingLayout(g: GameState, view: BoardView): List<Rest> {
    val tpp = g.tokensPerPlayer
    val t = view.tile
    val raw = g.tokens.indices.map { id -> view.tokenPoint(g.seatOf(id / tpp), g.tokens[id], id % tpp) }
    val out = arrayOfNulls<Rest>(raw.size)
    for (player in 0 until g.config.playerCount) {
        val home = (player * tpp until (player + 1) * tpp).filter { g.topology.isHome(g.tokens[it]) }
        val scale = when { home.size <= 2 -> 0.9f; home.size == 3 -> 0.8f; else -> 0.74f }
        home.forEachIndexed { k, id -> out[id] = Rest(view.v(view.geo.homeSlot(g.seatOf(player), k, home.size)), scale) }
    }
    val groups = raw.indices.filter { out[it] == null }.groupBy { (raw[it].x * 20).toInt() to (raw[it].y * 20).toInt() }
    for ((_, ids) in groups) {
        val n = ids.size
        ids.sortedBy { it }.forEachIndexed { k, id ->
            val p = raw[id]
            out[id] = when {
                g.tokens[id] == BASE || n == 1 -> Rest(p, 1f)
                n == 2 -> Rest(p + Offset(if (k == 0) -0.19f * t else 0.19f * t, 0f), 0.72f)
                n <= 4 -> Rest(p + Offset(if (k % 2 == 0) -0.2f * t else 0.2f * t, if (k < 2) -0.15f * t else 0.17f * t), 0.64f)
                else -> Rest(p + Offset(((k % 3) - 1) * 0.26f * t, if (k < 3) -0.15f * t else 0.17f * t), 0.54f)
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
    /** Landing squash: 1 = fully squashed (wide and short), negative = stretch while springing back. */
    val squash = Animatable(0f)
    /** Tap feedback: 1 = pressed down. */
    val press = Animatable(0f)
}

private class Burst(val center: Offset, val colors: List<Color>, val seed: Int) {
    var t by mutableFloatStateOf(0f)
}

private class Flash(val center: Offset, val color: Color, val seed: Int) {
    var t by mutableFloatStateOf(0f)
}

/** Soft ring that spreads from a tile a pawn just landed on. */
private class Ring(val center: Offset, val color: Color) {
    var t by mutableFloatStateOf(0f)
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Hop parabola: offsetY = -sin(PI·t) · tile · 0.45, expressed as lift in tiles. */
private fun hopLift(t: Float) = sin(PI * t).toFloat() * 0.45f

/** Scale at the apex: 1.0 → 1.22 → 1.0. */
private fun hopScale(t: Float, base: Float) = base * (1f + 0.22f * sin(PI * t).toFloat())

private val TICK_GREEN = Color(0xFF16A75C)

/**
 * Board renderer with a decoupled token layer. Tokens live at floating-point positions and are
 * animated by a sequential hop engine: one parabolic hop per tile with an apex scale-up, a
 * squash-and-stretch landing and a spreading ring on the tile, a curved arc out of the yard, an
 * impact burst then a retreat arc for captures, and confetti at Home while a ✓ pops into the
 * player's yard. All lengths come from [timings], the same numbers the session waits on; the
 * squash, rings and ✓ pop run alongside the hops and never lengthen a move.
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
    val rings = remember { mutableStateListOf<Ring>() }
    val tickPop = remember(game.config) { mutableStateMapOf<Int, Float>() }
    var shake by remember { mutableFloatStateOf(0f) }
    var fortressPulse by remember { mutableFloatStateOf(0f) }
    val hop by rememberUpdatedState(onHop)
    val tapScope = rememberCoroutineScope()

    val tpp = game.tokensPerPlayer
    val tile = view.tile
    val homeProgress = game.topology.homeProgress
    val playerColors = game.config.players.map { theme.tokenColor(it.colorIndex, colorblind) }
    val seatColor = { seat: Int ->
        val p = game.config.seats.indexOf(seat)
        if (p >= 0) playerColors[p] else lerp(theme.tokens[seat % theme.tokens.size], Color(0xFFBDBDBD), 0.75f)
    }
    val tap = { id: Int ->
        tapScope.launch { visuals[id].press.snapTo(1f); visuals[id].press.animateTo(0f, spring(0.5f, 700f)) }
        onTokenTap(id)
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
            fun land(v: TokenVisual, at: Offset, color: Color) {
                launch { v.squash.snapTo(1f); v.squash.animateTo(0f, spring(0.42f, 900f)) }
                launch {
                    val r = Ring(at, color); rings += r
                    try { animate(0f, 1f, animationSpec = tween(300, easing = LinearEasing)) { x, _ -> r.t = x } } finally { rings.remove(r) }
                }
            }

            // Everyone not moving this turn eases into the new stacking layout.
            for (id in game.tokens.indices) if (id != moverId && id !in victimIds) launch { glide(visuals[id], layout[id]) }

            if (moverId != null) {
                val v = visuals[moverId]
                val owner = moverId / tpp
                val seat = game.seatOf(owner)
                val from = prevTokens[moverId]
                val to = game.tokens[moverId]
                val color = playerColors[owner]
                val reachedHome = evs.any { it is GameEvent.TokenReachedHome }
                if (reachedHome) tickPop[moverId] = 0f
                if (from == BASE) {
                    arc(v, layout[moverId].copy(scale = 1f), t.exitMs, 0.9f)
                    hop()
                    land(v, v.pos, color)
                } else {
                    val path = view.hopPath(seat, from, to, moverId % tpp).toMutableList()
                    // The last hop of a finishing move lands straight on its spot in the Home wedge.
                    if (reachedHome && path.isNotEmpty()) path[path.lastIndex] = layout[moverId].pos
                    for (target in path) {
                        val a = v.pos
                        animate(0f, 1f, animationSpec = tween(t.hopMs.toInt(), easing = LinearEasing)) { x, _ ->
                            v.pos = lerp(a, target, x); v.lift = hopLift(x); v.scale = hopScale(x, 1f)
                        }
                        v.lift = 0f; v.scale = 1f
                        hop() // one percussive tap per tile landed
                        land(v, target, color)
                    }
                }
                if (victims.isNotEmpty()) {
                    val hit = view.v(view.geo.trackCell(victims.first().cell))
                    val victimColor = playerColors[victims.first().victimTokenId / tpp]
                    val f = Flash(hit, victimColor, eventSeq.toInt()); flashes += f
                    try {
                        animate(0f, 1f, animationSpec = tween(t.impactMs.toInt(), easing = LinearEasing)) { x, _ ->
                            f.t = x; shake = sin(x * PI.toFloat() * 6f) * (1 - x) * 0.12f
                        }
                    } finally { shake = 0f; flashes.remove(f) }
                    coroutineScope {
                        for (vic in victimIds) launch { arc(visuals[vic], layout[vic], t.retreatMs, 1.6f) }
                    }
                }
                if (reachedHome) {
                    val b = Burst(layout[moverId].pos, playerColors + theme.accent, moverId * 7 + eventSeq.toInt())
                    bursts += b
                    launch { glide(v, layout[moverId], 120) }
                    // The ✓ pops into the yard slot while the confetti flies.
                    launch {
                        animate(0f, 1f, animationSpec = keyframes { durationMillis = t.homeBurstMs.toInt(); 1.25f at (t.homeBurstMs * 0.6).toInt() }) { x, _ ->
                            tickPop[moverId] = x
                        }
                        tickPop.remove(moverId)
                    }
                    try {
                        animate(0f, 1f, animationSpec = tween(t.homeBurstMs.toInt(), easing = LinearEasing)) { x, _ -> b.t = x }
                    } finally { bursts.remove(b) }
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
                detectTapGestures { tapAt ->
                    if (selectable.isEmpty()) return@detectTapGestures
                    val unit = size.width / (2 * view.span)
                    val b = Offset((tapAt.x - size.width / 2f) / unit, (tapAt.y - size.height / 2f) / unit)
                    // Generous target: the whole tile plus the raised pawn above it.
                    fun d(id: Int) = hypot(visuals[id].pos.x - b.x, visuals[id].pos.y - 0.25f * tile - b.y)
                    selectable.minByOrNull(::d)?.takeIf { d(it) < 0.95f * tile }?.let(tap)
                }
            },
    ) {
        val unit = size.minDimension / (2 * view.span)
        val origin = Offset(size.width / 2, size.height / 2) + Offset(shake * unit, 0f)
        fun px(o: Offset) = origin + o * unit

        drawBoard(game, view, theme, unit, ::px, seatColor, activeSeat, pulse)

        // Finished pawns: a ✓ badge pops into each emptied yard slot.
        for (id in game.tokens.indices) {
            if (game.tokens[id] != homeProgress) continue
            val k = tickPop[id] ?: if (prevTokens[id] != homeProgress) 0f else 1f
            if (k <= 0f) continue
            val seat = game.seatOf(id / tpp)
            val slotR = unit * (if (view.cross) 0.55f * view.outer else 0.42f)
            drawTick(px(view.v(view.geo.yardSlot(seat, id % tpp))), slotR * 0.86f * k)
        }

        // Fortress indicators: gold tile outline + small shield, inside the tile bounds.
        val seen = HashSet<Pair<Int, Int>>()
        for (id in game.tokens.indices) {
            val cell = game.cellOf(id)
            if (cell < 0 || !game.topology.isOnTrack(game.tokens[id])) continue
            val team = game.teamOf(id / tpp)
            if (!seen.add(cell to team) || !game.isFortress(cell, team)) continue
            val pt = view.geo.trackCell(cell)
            val (arm, _, _) = view.geo.laneDepthOf(cell)
            drawFortress(view, pt, arm, unit, ::px, theme, fortressPulse)
        }

        // Landing rings under the pawns.
        for (r in rings) {
            val c = px(r.center)
            val fade = 1f - r.t
            drawCircle(r.color.copy(alpha = 0.22f * fade), unit * tile * (0.3f + 0.2f * r.t), c)
            drawCircle(r.color.copy(alpha = 0.75f * fade), unit * tile * (0.26f + 0.3f * r.t), c, style = Stroke(unit * tile * 0.07f * fade + 1f))
        }

        // Selection rings under movable pawns.
        for (id in selectable) {
            val g = px(visuals[id].pos)
            rotate(spin, g) {
                drawCircle(theme.accent.copy(alpha = 0.45f + 0.4f * pulse), unit * tile * 0.5f, g,
                    style = Stroke(unit * tile * 0.1f, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * tile * 0.3f, unit * tile * 0.17f))))
            }
            drawCircle(Color.White.copy(alpha = 0.3f * pulse), unit * tile * 0.42f, g)
        }

        // Pawns, back to front; moving / selectable ones on top.
        val order = game.tokens.indices.sortedWith(compareBy({ visuals[it].lift > 0f || it in selectable }, { visuals[it].pos.y }))
        for (id in order) {
            val v = visuals[id]
            val owner = id / tpp
            val r = unit * tile * BoardView.PIN_R * v.scale * (1f - 0.14f * v.press.value)
            val raise = if (id in selectable) 0.18f + 0.08f * pulse else 0f
            val ground = px(v.pos + Offset(0f, 0.36f * tile * v.scale))
            val sq = v.squash.value * (1f - (v.lift / 0.45f).coerceIn(0f, 1f))
            drawPin(
                ground, r, playerColors[owner],
                lift = ((v.lift + raise) * unit * tile) / (r * 2.2f),
                glyph = if (colorblind) BoardThemes.GLYPHS[game.seatOf(owner) % BoardThemes.GLYPHS.size] else null,
                squashX = 1f + 0.12f * sq, squashY = 1f - 0.12f * sq,
            )
        }

        // Capture impact burst.
        for (f in flashes) drawImpact(px(f.center), unit * tile, f)
        // Home confetti.
        for (b in bursts) drawBurst(px(b.center), unit * tile, b)
    }
    // Accessible, oversized tap targets over each selectable pawn (TalkBack can pick pawns too).
    val unitDp = boxW / (2 * view.span)
    for (id in selectable) {
        val p = visuals[id].pos
        Box(
            Modifier
                .offset(unitDp * (view.span + p.x - 0.6f * tile), unitDp * (view.span + p.y - 0.85f * tile))
                .size(unitDp * 1.2f * tile, unitDp * 1.35f * tile)
                .semantics { contentDescription = "Movable pawn ${id + 1}" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tap(id) },
        )
    }
    }
}

/** Emerald badge with a white tick, marking a yard slot whose pawn has finished. */
private fun DrawScope.drawTick(c: Offset, r: Float) {
    drawCircle(Color.Black.copy(alpha = 0.22f), r, c + Offset(0f, r * 0.12f))
    drawCircle(Brush.radialGradient(listOf(TICK_GREEN.lighten(0.25f), TICK_GREEN, TICK_GREEN.darken(0.25f)),
        center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.5f), r, c)
    drawCircle(Color.White, r, c, style = Stroke(r * 0.14f))
    val tick = Path().apply {
        moveTo(c.x - r * 0.42f, c.y + r * 0.02f)
        lineTo(c.x - r * 0.12f, c.y + r * 0.32f)
        lineTo(c.x + r * 0.45f, c.y - r * 0.3f)
    }
    drawPath(tick, Color.White, style = Stroke(r * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawImpact(c: Offset, u: Float, f: Flash) {
    val t = f.t
    val fade = (1 - t).coerceIn(0f, 1f)
    drawCircle(Color.White.copy(alpha = 0.9f * fade), u * (0.3f + 0.6f * t), c)
    drawCircle(Color(0xFFFF5252).copy(alpha = 0.95f * fade), u * (0.36f + 0.8f * t), c, style = Stroke(u * 0.09f))
    val rnd = Random(f.seed)
    for (i in 0 until 10) {
        val a = i * PI.toFloat() / 5 + rnd.nextFloat() * 0.3f
        val dir = Offset(cos(a), sin(a))
        drawLine((if (i % 2 == 0) f.color else Color(0xFFFFD54F)).copy(alpha = fade),
            c + dir * (u * (0.35f + 0.5f * t)), c + dir * (u * (0.6f + 1.0f * t)), strokeWidth = u * 0.09f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawBurst(c: Offset, u: Float, b: Burst) {
    val t = b.t
    val rnd = Random(b.seed)
    val fade = (1 - t * t).coerceIn(0f, 1f)
    for (i in 0 until 12) {
        val a = i * PI.toFloat() / 6 + t * 0.6f
        val r0 = u * (0.2f + 0.4f * t); val r1 = u * (0.5f + 1.3f * t)
        drawLine(b.colors[i % b.colors.size].copy(alpha = fade), c + Offset(cos(a), sin(a)) * r0, c + Offset(cos(a), sin(a)) * r1,
            strokeWidth = u * 0.08f, cap = StrokeCap.Round)
    }
    repeat(26) {
        val a = rnd.nextFloat() * 2 * PI.toFloat()
        val sp = 0.6f + rnd.nextFloat() * 1.6f
        val p = c + Offset(cos(a), sin(a)) * (sp * t * 1.9f * u) + Offset(0f, t * t * u * 0.6f)
        val col = b.colors[it % b.colors.size]
        rotate(rnd.nextFloat() * 360f + t * 540f, p) {
            drawRect(col.copy(alpha = fade), p - Offset(u * 0.07f, u * 0.04f), Size(u * 0.14f, u * 0.08f))
        }
    }
}

private fun DrawScope.drawFortress(view: BoardView, pt: Pt, arm: Int, unit: Float, px: (Offset) -> Offset, theme: BoardTheme, pulse: Float) {
    val brush = { c: Offset -> Brush.sweepGradient(theme.fortressMetal + theme.fortressMetal.first(), c) }
    val stroke = Stroke(unit * view.tile * (0.09f + 0.06f * pulse))
    val c = px(view.v(pt))
    val corner: Offset
    if (view.cross) {
        val r = view.tileRect(pt)
        val inset = view.tile * 0.06f
        val tl = px(r.topLeft + Offset(inset, inset))
        val sz = Size((r.width - 2 * inset) * unit, (r.height - 2 * inset) * unit)
        drawRoundRect(brush(c), tl, sz, CornerRadius(unit * 0.14f), style = stroke)
        corner = Offset(tl.x + sz.width, tl.y)
    } else {
        val s = unit * 0.9f
        rotate(view.armDeg(arm), c) {
            drawRoundRect(brush(c), c - Offset(s / 2, s / 2), Size(s, s), CornerRadius(unit * 0.14f), style = stroke)
        }
        corner = c + Offset(s / 2, -s / 2)
    }
    // Small shield tucked into the tile's top-right corner.
    val w = unit * view.tile * 0.13f
    val sc = corner + Offset(-w * 1.5f, w * 1.6f)
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
    val cross = view.cross
    val e = view.extent
    val f = BoardView.FRAME
    val frameBrush = Brush.verticalGradient(
        listOf(theme.frame.lighten(0.28f), theme.frame, theme.frame.darken(0.4f)),
        startY = px(Offset(0f, -e - f)).y, endY = px(Offset(0f, e + f)).y,
    )
    // Drop shadow, bevelled frame with a lit top edge, then the board slab.
    if (cross) {
        val outerTl = px(Offset(-e - f, -e - f)); val outerSz = Size(2 * (e + f) * unit, 2 * (e + f) * unit)
        drawRoundRect(Color.Black.copy(alpha = 0.28f), outerTl + Offset(0f, unit * 0.14f), outerSz, CornerRadius(unit * 0.5f))
        drawRoundRect(frameBrush, outerTl, outerSz, CornerRadius(unit * 0.5f))
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), startY = outerTl.y, endY = outerTl.y + outerSz.height * 0.5f),
            outerTl + Offset(unit * 0.03f, unit * 0.03f), Size(outerSz.width - unit * 0.06f, outerSz.height - unit * 0.06f),
            CornerRadius(unit * 0.47f), style = Stroke(unit * 0.035f))
        drawRoundRect(theme.boardBase, px(Offset(-e, -e)), Size(2 * e * unit, 2 * e * unit), CornerRadius(unit * 0.22f))
    } else {
        val c0 = px(Offset.Zero)
        drawCircle(Color.Black.copy(alpha = 0.28f), (e + f) * unit, c0 + Offset(0f, unit * 0.14f))
        drawCircle(frameBrush, (e + f) * unit, c0)
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), startY = c0.y - (e + f) * unit, endY = c0.y),
            (e + f) * unit - unit * 0.03f, c0, style = Stroke(unit * 0.035f))
        drawCircle(theme.boardBase, e * unit, c0)
    }

    // Yards: lit colour block, a slim bezel around a recessed white container, four sunken wells.
    for (seat in 0 until topo.seats) {
        val used = seat in game.config.seats
        val col = seatColor(seat)
        val c = px(view.yard(seat))
        val active = seat == activeSeat
        val wellR = unit * (if (cross) 0.55f * view.outer else 0.42f)
        if (cross) {
            val r = view.yardRect(seat)
            val tl = px(r.topLeft); val sz = Size(r.width * unit, r.height * unit)
            drawRoundRect(Brush.linearGradient(listOf(col.lighten(0.16f), col, col.darken(0.14f)), tl, tl + Offset(sz.width, sz.height)),
                tl, sz, CornerRadius(unit * 0.28f))
            drawRoundRect(theme.frame.copy(alpha = 0.55f), tl, sz, CornerRadius(unit * 0.28f), style = Stroke(unit * 0.05f))
            val bz = 0.5f * view.outer * unit
            val itl = tl + Offset(bz, bz); val isz = Size(sz.width - 2 * bz, sz.height - 2 * bz)
            val icr = CornerRadius(unit * 0.5f)
            drawRoundRect(theme.yardInner.copy(alpha = if (used) 1f else 0.6f), itl, isz, icr)
            // Recessed: a soft shadow under the top edge and a darker rim.
            drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent), startY = itl.y, endY = itl.y + unit * 0.5f),
                itl, isz, icr)
            drawRoundRect(col.darken(0.35f).copy(alpha = 0.55f), itl, isz, icr, style = Stroke(unit * 0.045f))
            if (active) {
                drawRoundRect(Color.White.copy(alpha = 0.4f + 0.55f * pulse), tl + Offset(unit * 0.1f, unit * 0.1f),
                    Size(sz.width - unit * 0.2f, sz.height - unit * 0.2f), CornerRadius(unit * 0.24f), style = Stroke(unit * (0.13f + 0.09f * pulse)))
            }
        } else {
            val r = geo.yardRadius * unit
            drawCircle(Brush.linearGradient(listOf(col.lighten(0.16f), col, col.darken(0.14f)), c - Offset(r, r), c + Offset(r, r)), r, c)
            drawCircle(theme.frame.copy(alpha = 0.55f), r, c, style = Stroke(unit * 0.05f))
            val ir = r * 0.9f
            drawCircle(theme.yardInner.copy(alpha = if (used) 1f else 0.6f), ir, c)
            drawCircle(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent), startY = c.y - ir, endY = c.y - ir + unit * 0.5f), ir, c)
            drawCircle(col.darken(0.35f).copy(alpha = 0.55f), ir, c, style = Stroke(unit * 0.045f))
            if (active) drawCircle(Color.White.copy(alpha = 0.4f + 0.55f * pulse), r - unit * 0.1f, c, style = Stroke(unit * (0.13f + 0.09f * pulse)))
        }
        for (k in 0..3) {
            val s = px(view.v(geo.yardSlot(seat, k)))
            drawCircle(col.copy(alpha = 0.2f), wellR, s)
            // Inner shadow, heavier at the top: the well is sunk into the container.
            drawCircle(Brush.radialGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f),
                center = s + Offset(0f, wellR * 0.2f), radius = wellR * 1.12f), wellR, s)
            drawCircle(col, wellR, s, style = Stroke(unit * (if (cross) 0.11f else 0.09f)))
            drawArc(Color.White.copy(alpha = 0.75f), 25f, 130f, false, s - Offset(wellR * 1.08f, wellR * 1.08f), Size(wellR * 2.16f, wellR * 2.16f),
                style = Stroke(unit * 0.035f, cap = StrokeCap.Round))
        }
    }

    // Centre: one lit wedge per seat, separated by crisp lines.
    val poly = geo.centerPolygon().map(view::v)
    val center = px(Offset.Zero)
    for (seat in 0 until topo.seats) {
        val a = px(poly[(seat - 1 + topo.seats) % topo.seats]); val b = px(poly[seat])
        val path = Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
        val col = seatColor(seat)
        drawPath(path, Brush.linearGradient(listOf(col.darken(0.16f), col.lighten(0.1f)), center, (a + b) / 2f))
        drawPath(path, theme.frame, style = Stroke(unit * 0.05f))
    }

    // Track cells: crisp borders, coloured start squares, player-coloured stars on safe squares.
    fun cell(pt: Pt, arm: Int, fill: Color) {
        if (cross) {
            val r = view.tileRect(pt)
            val tl = px(r.topLeft); val sz = Size(r.width * unit, r.height * unit)
            drawRect(fill, tl, sz)
            drawRect(theme.cellBorder, tl, sz, style = Stroke(unit * 0.045f))
        } else {
            val p = px(view.v(pt)); val cs = unit * 0.98f
            rotate(view.armDeg(arm), p) {
                val tl = p - Offset(cs / 2, cs / 2)
                drawRect(fill, tl, Size(cs, cs))
                drawRect(theme.cellBorder, tl, Size(cs, cs), style = Stroke(unit * 0.045f))
            }
        }
    }
    val starR = unit * view.tile * 0.36f
    for (c in 0 until topo.trackLength) {
        val (arm, lane, depth) = geo.laneDepthOf(c)
        val pt = geo.trackCell(c)
        val p = px(view.v(pt))
        val startSeat = (0 until topo.seats).firstOrNull { topo.startCell(it) == c }
        val side = seatColor(arm)
        val safe = topo.isSafeCell(c)
        cell(pt, arm, when {
            startSeat != null -> seatColor(startSeat)
            safe -> lerp(theme.cell, side, 0.13f)
            else -> theme.cell
        })
        if (safe) {
            val star = starPath(p, starR)
            if (startSeat != null) {
                drawPath(star, Color.White)
                drawPath(star, side.darken(0.4f), style = Stroke(unit * 0.045f, join = StrokeJoin.Round))
            } else {
                // Rich star in the colour of the side it sits on; bright yellows get a deeper rim.
                val bright = side.relativeLuminance() > 0.45
                drawPath(star, if (bright) side.darken(0.06f) else side)
                drawPath(star, side.darken(if (bright) 0.5f else 0.38f), style = Stroke(unit * 0.05f, join = StrokeJoin.Round))
            }
        }
        if (lane == 0 && depth == 5) {
            // Entry arrow: points from the tip into this seat's home stretch.
            val d = -view.armDir(arm)
            val n = Offset(-d.y, d.x)
            val k = unit * view.tile
            val tip = p + d * (k * 0.34f)
            val back = p - d * (k * 0.22f)
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(back.x + n.x * k * 0.28f, back.y + n.y * k * 0.28f)
                lineTo(back.x - n.x * k * 0.28f, back.y - n.y * k * 0.28f); close()
            }
            drawPath(arrow, side)
            drawPath(arrow, side.darken(0.4f), style = Stroke(unit * 0.035f, join = StrokeJoin.Round))
        }
    }
    for (seat in 0 until topo.seats) for (i in 0..4) cell(geo.stretchCell(seat, i), seat, seatColor(seat))
}
