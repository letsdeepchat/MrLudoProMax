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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ludosovereign.designsystem.RobotIcon
import com.ludosovereign.session.Podium
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
import com.ludosovereign.designsystem.yardColor
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
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Path Coordinate Resolver for the screen: the pure [BoardGeometry] gives every yard slot, track
 * cell, home-stretch cell and Home point in board units; this adds the view rotation that puts
 * seat 0 in the top-left yard (cross board) or yards above/below the hexagon (hex board).
 *
 * The cross board can re-weight the 15×15 grid ([LANE_WEIGHT]); it is 1, so every road tile is a
 * true square, the centre Home box stays a compact 3×3 tiles and the yards sit flush with the
 * track. The yards still give space back through a slim bezel and wider-spread wells.
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

    /** Size pawns and markers scale with: the geometric mean of a road tile's two sides. */
    val tile: Float = if (cross) sqrt(outer * lane) else 1f

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

    /**
     * A yard well. On the cross board the four wells are spread a little wider than the grid
     * suggests, leaving a clear centre gap for the Auto-Bot / podium badge.
     */
    fun yardSlot(seat: Int, localIndex: Int): Offset {
        val raw = v(geo.yardSlot(seat, localIndex))
        if (!cross) return raw
        val c = yard(seat)
        return c + (raw - c) * (WELL_SPREAD / (0.95f * outer))
    }

    /** Radius of a yard well, in board units. */
    val wellRadius: Float = if (cross) 0.48f * tile else 0.42f

    /** Exact resting point of a token, before stacking offsets. */
    fun tokenPoint(seat: Int, progress: Int, localIndex: Int): Offset =
        if (progress == BASE) yardSlot(seat, localIndex) else v(geo.tokenPoint(seat, progress, localIndex))

    /** Ordered hop targets [step_1 .. step_N] for a move; empty for releases (they arc instead). */
    fun hopPath(seat: Int, from: Int, to: Int, localIndex: Int): List<Offset> =
        if (from == BASE || to == BASE) emptyList() else geo.path(seat, from, to, localIndex).map(::v)

    companion object {
        /** Centre-lane width relative to a yard column on the cross board (1 = classic square grid). */
        const val LANE_WEIGHT = 1.0f

        /** Distance (board units, along each axis) from a cross-board yard centre to each well. */
        const val WELL_SPREAD = 1.2f

        /** Thickness of the coloured yard bezel, as a fraction of an outer grid column. */
        const val BEZEL = 0.55f

        /** Margin around the board for its outline and shadow, in board units. */
        const val FRAME = 0.08f

        /** Pin head radius as a fraction of [tile]: a lone pawn spans ~80% of a tile and stands ~1.2 tiles tall. */
        const val PIN_R = 0.41f

        /** How far below a tile's centre a pawn's tip touches down, as a fraction of [tile]. */
        const val TIP_DROP = 0.28f
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
    /** Human players whose turns the Auto-Bot is playing. */
    autoPlayers: Set<Int> = emptySet(),
    /** When set, each unfinished human player's yard gets a centre Auto-Bot toggle. */
    onAutoToggle: ((Int) -> Unit)? = null,
    /** Players whose single Auto-Bot chance is spent: their toggle is shown greyed out and ignores taps. */
    autoSpent: Set<Int> = emptySet(),
) {
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    val view = remember(game.topology) { BoardView(BoardGeometry(game.topology)) }
    val layout = remember(game) { restingLayout(game, view) }
    val visuals = remember(game.config) { layout.map { TokenVisual(it) } }
    val bursts = remember { mutableStateListOf<Burst>() }
    val flashes = remember { mutableStateListOf<Flash>() }
    val rings = remember { mutableStateListOf<Ring>() }
    val tickPop = remember(game.config) { mutableStateMapOf<Int, Float>() }
    // eventSeq whose animation has started; until then a pawn arriving Home keeps its ✓ hidden.
    var startedSeq by remember(game.config) { mutableStateOf(Long.MIN_VALUE) }
    var shake by remember { mutableFloatStateOf(0f) }
    var fortressPulse by remember { mutableFloatStateOf(0f) }
    val hop by rememberUpdatedState(onHop)
    val tapScope = rememberCoroutineScope()

    val tpp = game.tokensPerPlayer
    val tile = view.tile
    val homeProgress = game.topology.homeProgress
    val playerColors = game.config.players.map { theme.tokenColor(it.colorIndex, colorblind) }
    // Unused seats keep a full, vibrant colour of their own (one no player picked).
    val idleColorIndex = remember(game.config) { idleSeatColors(game) }
    val seatColor = { seat: Int ->
        val p = game.config.seats.indexOf(seat)
        if (p >= 0) playerColors[p] else theme.tokenColor(idleColorIndex.getValue(seat), colorblind)
    }
    val yardColor = { seat: Int ->
        val p = game.config.seats.indexOf(seat)
        if (p >= 0) theme.yardColor(game.config.players[p].colorIndex, colorblind)
        else theme.yardColor(idleColorIndex.getValue(seat), colorblind)
    }
    val textMeasurer = rememberTextMeasurer()
    val tap = { id: Int ->
        tapScope.launch { visuals[id].press.snapTo(1f); visuals[id].press.animateTo(0f, spring(0.5f, 700f)) }
        onTokenTap(id)
    }

    // ---- Hop engine -----------------------------------------------------------------------------
    LaunchedEffect(game) {
        val t = timings
        val evs = events
        val plan = planMove(evs)
        val moverId = plan?.tokenId
        val reachedHome = moverId != null && moverId in homeArrivals(evs)
        // A cancelled earlier animation must not leave a ✓ half-popped: start from a clean slate.
        tickPop.clear()
        if (reachedHome) tickPop[moverId!!] = 0f
        startedSeq = eventSeq
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
                val from = plan!!.from
                val to = plan.to
                val color = playerColors[owner]
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
                        try {
                            animate(0f, 1f, animationSpec = keyframes { durationMillis = t.homeBurstMs.toInt(); 1.25f at (t.homeBurstMs * 0.6).toInt() }) { x, _ ->
                                tickPop[moverId] = x
                            }
                        } finally { tickPop.remove(moverId) }
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

        drawBoard(game, view, theme, unit, ::px, seatColor, yardColor, activeSeat, pulse)

        // Podium badge in the centre of each finished player's yard (Team Up has no individual podium).
        if (!game.rules.teamUp) game.finishOrder.forEachIndexed { i, player ->
            drawPodiumBadge(textMeasurer, px(view.yard(game.seatOf(player))), unit * view.tile, i + 1)
        }

        // Finished pawns: a ✓ badge pops into each emptied yard slot.
        val arriving = if (startedSeq == eventSeq) emptySet() else homeArrivals(events)
        for (id in game.tokens.indices) {
            val k = tickScale(game.tokens[id] == homeProgress, tickPop[id], id in arriving)
            if (k <= 0f) continue
            val seat = game.seatOf(id / tpp)
            drawTick(px(view.yardSlot(seat, id % tpp)), unit * view.wellRadius * 0.86f * k)
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

        // Base rings: each pawn out on the board stands on a ring of its own colour; pawns that can
        // move get a rotating dashed ring instead.
        for (id in game.tokens.indices) {
            val v = visuals[id]
            val t = game.tokens[id]
            val g = px(v.pos + Offset(0f, BoardView.TIP_DROP * tile * v.scale))
            if (id in selectable) {
                drawCircle(Color.White.copy(alpha = 0.35f * pulse), unit * tile * 0.36f, g)
                rotate(spin, g) {
                    drawCircle(theme.selectRing.copy(alpha = 0.7f + 0.3f * pulse), unit * tile * 0.4f, g,
                        style = Stroke(unit * tile * 0.09f, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * tile * 0.22f, unit * tile * 0.12f))))
                }
            } else if (t != BASE && t != homeProgress) {
                val fade = 1f - (v.lift / 0.45f).coerceIn(0f, 1f)
                drawCircle(playerColors[id / tpp].darken(0.3f).copy(alpha = 0.85f * fade), unit * tile * 0.3f * v.scale, g,
                    style = Stroke(unit * tile * 0.065f * v.scale))
            }
        }

        // Pawns, back to front; moving / selectable ones on top.
        val order = game.tokens.indices.sortedWith(compareBy({ visuals[it].lift > 0f || it in selectable }, { visuals[it].pos.y }))
        for (id in order) {
            val v = visuals[id]
            val owner = id / tpp
            val r = unit * tile * BoardView.PIN_R * v.scale * (1f - 0.14f * v.press.value)
            val raise = if (id in selectable) 0.18f + 0.08f * pulse else 0f
            val ground = px(v.pos + Offset(0f, BoardView.TIP_DROP * tile * v.scale))
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
    // Auto-Bot toggles in the centre gap of each unfinished human player's yard.
    if (onAutoToggle != null) {
        for (player in game.config.players.indices) {
            val spec = game.config.players[player]
            if (spec.kind != com.ludosovereign.engine.PlayerKind.HUMAN || game.hasFinished(player)) continue
            val c = view.yard(game.seatOf(player))
            val d = unitDp * (if (view.cross) 0.98f else 0.9f) * view.tile
            val on = player in autoPlayers
            AutoBotToggle(
                on = on, spent = !on && player in autoSpent, pulse = pulse, color = playerColors[player],
                name = spec.name, onClick = { onAutoToggle(player) },
                modifier = Modifier.offset(unitDp * (view.span + c.x) - d / 2, unitDp * (view.span + c.y) - d / 2).size(d),
            )
        }
    }
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

/** Colour index for every seat nobody sits in: its own palette colour when free, else the first unused one. */
internal fun idleSeatColors(g: GameState): Map<Int, Int> {
    val used = g.config.players.map { it.colorIndex }.toMutableSet()
    val out = HashMap<Int, Int>()
    for (seat in 0 until g.topology.seats) {
        if (seat in g.config.seats) continue
        val own = BoardThemes.seatColorIndex(seat)
        val pick = if (own !in used) own else (0 until 6).map(BoardThemes::seatColorIndex).firstOrNull { it !in used } ?: own
        used += pick
        out[seat] = pick
    }
    return out
}

/** Gold medal with the podium title (Ultra / Pro / Max / Aspirant) in a finished player's yard. */
private fun DrawScope.drawPodiumBadge(tm: TextMeasurer, c: Offset, u: Float, place: Int) {
    val r = u * 0.62f
    val (hi, lo) = when (place) {
        1 -> Color(0xFFFFE082) to Color(0xFFF9A825)
        2 -> Color(0xFFF5F5F5) to Color(0xFF9E9E9E)
        3 -> Color(0xFFFFCCBC) to Color(0xFFBF6A3A)
        else -> Color(0xFFB3E5FC) to Color(0xFF4F7EA8)
    }
    drawCircle(Color.Black.copy(alpha = 0.25f), r, c + Offset(0f, r * 0.12f))
    drawCircle(Brush.radialGradient(listOf(hi, lo), center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.6f), r, c)
    drawCircle(Color.White, r, c, style = Stroke(r * 0.1f))
    val num = tm.measure("$place", TextStyle(fontSize = (r * 0.9f / density).sp, fontWeight = FontWeight.Black, color = Color(0xFF3E2723)))
    drawText(num, topLeft = c - Offset(num.size.width / 2f, num.size.height / 2f + r * 0.08f))
    // Ribbon with the title under the medal.
    val label = tm.measure(Podium.title(place).uppercase(), TextStyle(fontSize = (u * 0.3f / density).sp, fontWeight = FontWeight.ExtraBold,
        color = Color.White, letterSpacing = 0.5.sp))
    val w = label.size.width + u * 0.3f; val h = label.size.height + u * 0.06f
    val tl = Offset(c.x - w / 2, c.y + r * 0.7f)
    drawRoundRect(lo.darken(0.25f), tl, Size(w, h), CornerRadius(h / 2))
    drawRoundRect(Color.White, tl, Size(w, h), CornerRadius(h / 2), style = Stroke(maxOf(u * 0.03f, 1f)))
    drawText(label, topLeft = Offset(c.x - label.size.width / 2f, tl.y + u * 0.03f))
}

/** Compact circular robot toggle that sits between a human yard's four wells. */
@Composable
private fun AutoBotToggle(on: Boolean, spent: Boolean, pulse: Float, color: Color, name: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer { val k = if (on) 1f + 0.06f * pulse else 1f; scaleX = k; scaleY = k; alpha = if (spent) 0.45f else 1f }
            .background(if (on) color else if (spent) Color(0xFFD5D5D5) else Color.White, CircleShape)
            .border(if (on) 2.5.dp else 1.5.dp, if (on) Color.White else if (spent) Color(0xFF9E9E9E) else color.darken(0.2f), CircleShape)
            .semantics { contentDescription = "Auto-Bot for $name ${if (on) "on" else if (spent) "used" else "off"}" }
            .clickable(enabled = !spent, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (spent) {
            // Single use: the chance is gone for this match. A struck-through robot marks it.
            Icon(RobotIcon, null, tint = Color(0xFF757575), modifier = Modifier.fillMaxSize(0.62f))
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawLine(Color(0xFF616161), center + Offset(-r * 0.62f, r * 0.62f), center + Offset(r * 0.62f, -r * 0.62f),
                    strokeWidth = r * 0.14f, cap = StrokeCap.Round)
            }
            return@Box
        }
        if (on) {
            // Glowing ring + AUTO micro-label.
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = 0.35f + 0.4f * pulse), size.minDimension / 2 * (1.05f + 0.12f * pulse), style = Stroke(2.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(RobotIcon, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.5f))
                Text("AUTO", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, lineHeight = 7.sp)
            }
        } else {
            Icon(RobotIcon, null, tint = color.darken(0.25f), modifier = Modifier.fillMaxSize(0.62f))
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
    yardColor: (Int) -> Color,
    activeSeat: Int,
    pulse: Float,
) {
    val topo = game.topology
    val geo = view.geo
    val cross = view.cross
    val e = view.extent
    val gridW = maxOf(unit * 0.03f, 1f)

    // Board slab: rounded corners and a soft shadow; everything else is clipped to it.
    val slab = Path()
    if (cross) {
        val tl = px(Offset(-e, -e)); val sz = Size(2 * e * unit, 2 * e * unit)
        drawRoundRect(Color.Black.copy(alpha = 0.25f), tl + Offset(0f, unit * 0.12f), sz, CornerRadius(unit * 0.32f))
        slab.addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + sz.width, tl.y + sz.height, CornerRadius(unit * 0.32f)))
    } else {
        val c0 = px(Offset.Zero)
        drawCircle(Color.Black.copy(alpha = 0.25f), e * unit, c0 + Offset(0f, unit * 0.12f))
        slab.addOval(Rect(c0, e * unit))
    }
    drawPath(slab, theme.boardBase)

    clipPath(slab) {
        // Yards: solid colour, a white inner box, four coloured bases.
        for (seat in 0 until topo.seats) {
            val used = seat in game.config.seats
            val col = yardColor(seat)
            val c = px(view.yard(seat))
            val active = seat == activeSeat
            if (cross) {
                val r = view.yardRect(seat)
                val tl = px(r.topLeft); val sz = Size(r.width * unit, r.height * unit)
                drawRect(col, tl, sz)
                // Slim coloured bezel: the yard gives its spare area to the road.
                val bz = BoardView.BEZEL * view.outer * unit
                drawRoundRect(theme.yardInner, tl + Offset(bz, bz), Size(sz.width - 2 * bz, sz.height - 2 * bz), CornerRadius(unit * 0.18f))
                if (active) {
                    // Whose turn: a soft white pulse just outside the white box.
                    val g = unit * (0.05f + 0.05f * pulse)
                    drawRect(Color.White.copy(alpha = 0.3f + 0.45f * pulse), tl + Offset(bz - g * 1.5f, bz - g * 1.5f),
                        Size(sz.width - 2 * bz + g * 3f, sz.height - 2 * bz + g * 3f), style = Stroke(g))
                }
            } else {
                val r = geo.yardRadius * unit
                drawCircle(col, r, c)
                drawCircle(theme.yardInner, r * 0.82f, c)
                if (active) drawCircle(Color.White.copy(alpha = 0.35f + 0.5f * pulse), r - unit * 0.1f, c, style = Stroke(unit * (0.1f + 0.08f * pulse)))
            }
            val baseR = unit * view.wellRadius
            val base = seatColor(seat)
            for (k in 0..3) {
                val s = px(view.yardSlot(seat, k))
                // A shallow coin in the pawn colour: a darker rim and a soft lit centre.
                drawCircle(base, baseR, s)
                drawCircle(Brush.radialGradient(0f to Color.White.copy(alpha = 0.14f), 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f),
                    center = s - Offset(baseR * 0.15f, baseR * 0.2f), radius = baseR * 1.1f), baseR, s)
                drawCircle(base.darken(0.3f), baseR * 0.8f, s, style = Stroke(unit * 0.04f))
                if (!used) {
                    // Seat not in this match: a clean × in each empty well.
                    val a = baseR * 0.42f
                    val sw = maxOf(baseR * 0.2f, 1.5f)
                    drawLine(Color.White.copy(alpha = 0.95f), s + Offset(-a, -a), s + Offset(a, a), sw, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.95f), s + Offset(-a, a), s + Offset(a, -a), sw, StrokeCap.Round)
                }
            }
        }

        // Centre: flat four- (or six-) colour triangles.
        val poly = geo.centerPolygon().map(view::v)
        val center = px(Offset.Zero)
        for (seat in 0 until topo.seats) {
            val a = px(poly[(seat - 1 + topo.seats) % topo.seats]); val b = px(poly[seat])
            val path = Path().apply { moveTo(center.x, center.y); lineTo(a.x, a.y); lineTo(b.x, b.y); close() }
            drawPath(path, seatColor(seat))
        }

        // Track: white tiles with thin grey grid lines; start squares and home lanes are solid colour.
        fun tile(pt: Pt, arm: Int, fill: Color, lines: Boolean, line: Color = theme.grid) {
            if (cross) {
                val r = view.tileRect(pt)
                val tl = px(r.topLeft); val sz = Size(r.width * unit, r.height * unit)
                drawRect(fill, tl, sz)
                if (lines) drawRect(line, tl, sz, style = Stroke(gridW))
            } else {
                val p = px(view.v(pt)); val cs = unit
                rotate(view.armDeg(arm), p) {
                    val tl = p - Offset(cs / 2, cs / 2)
                    drawRect(fill, tl, Size(cs, cs))
                    if (lines) drawRect(line, tl, Size(cs, cs), style = Stroke(gridW))
                }
            }
        }
        val starts = (0 until topo.seats).associateBy { topo.startCell(it) }
        for (c in 0 until topo.trackLength) if (c !in starts) tile(geo.trackCell(c), geo.laneDepthOf(c).first, theme.cell, lines = true)
        for ((c, seat) in starts) tile(geo.trackCell(c), geo.laneDepthOf(c).first, seatColor(seat), lines = false)
        // Home lanes: every step boxed by a crisp divider in a darker shade of the lane colour.
        for (seat in 0 until topo.seats) for (i in 0..4) {
            val c = seatColor(seat)
            tile(geo.stretchCell(seat, i), seat, c, lines = true, line = c.darken(0.42f))
        }

        val k = unit * view.tile
        for (c in 0 until topo.trackLength) {
            val (arm, lane, depth) = geo.laneDepthOf(c)
            val p = px(view.v(geo.trackCell(c)))
            if (topo.isSafeCell(c) && c !in starts) {
                // Star square: a star filled in the colour of the arm it sits on.
                val star = starPath(p, k * 0.36f)
                val sc = seatColor(arm)
                drawPath(star, sc)
                drawPath(star, sc.darken(0.4f), style = Stroke(maxOf(k * 0.04f, 1f), join = StrokeJoin.Round))
            }
            if (lane == 0 && depth == 5) {
                // Thin entry arrow in the seat's colour, pointing from the tip into its home lane.
                val d = -view.armDir(arm)
                val n = Offset(-d.y, d.x)
                val head = p + d * (k * 0.26f)
                val tail = p - d * (k * 0.26f)
                val col = seatColor(arm)
                val sw = maxOf(k * 0.055f, 1.2f)
                drawLine(col, tail, head, sw, StrokeCap.Round)
                val wing = d * (k * 0.15f)
                drawLine(col, head, head - wing + n * (k * 0.13f), sw, StrokeCap.Round)
                drawLine(col, head, head - wing - n * (k * 0.13f), sw, StrokeCap.Round)
            }
        }
    }
    // Thin outline around the slab.
    drawPath(slab, theme.grid.darken(0.1f), style = Stroke(maxOf(unit * 0.04f, 1f)))
}
