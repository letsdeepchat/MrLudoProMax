package com.ludosovereign.feature.board

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludosovereign.session.Podium
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ludosovereign.designsystem.CtaButton
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.relativeLuminance
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.PodDice
import com.ludosovereign.designsystem.RobotIcon
import com.ludosovereign.designsystem.darken
import com.ludosovereign.designsystem.drawPin
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.geometry.BoardGeometry
import com.ludosovereign.session.LudoUiState
import com.ludosovereign.session.TurnPhase
import kotlinx.coroutines.delay

/**
 * In-game screen: the board fills the width, and each seat has a corner dice pod anchored to its
 * yard (above the top yards, below the bottom ones). Only the active pod holds the live die; a
 * bouncing arrow and a pulsing glow mark whose turn it is. Chrome is a single translucent menu
 * button that pauses the match.
 */
@Composable
fun LudoBoardScreen(
    vm: LudoBoardViewModel,
    undoAllowed: Boolean,
    onExit: () -> Unit,
    onAudio: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val game = s.game
    val theme = LocalBoardTheme.current
    val view = remember(game.topology) { BoardView(BoardGeometry(game.topology)) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf<Int?>(null) }
    val selectable = if (s.awaitingHuman && s.turnPhase == TurnPhase.SELECTING_TOKEN) s.legalMoves.toSet() else emptySet()
    val activeSeat = if (s.turnPhase == TurnPhase.GAME_OVER) -1 else game.seatOf(game.current)

    // Seats split into the pods above and below the board, ordered left to right.
    val (topSeats, bottomSeats) = remember(game.topology) {
        val all = (0 until game.topology.seats).sortedBy { view.yard(it).x }
        all.filter { view.yard(it).y < 0 } to all.filter { view.yard(it).y >= 0 }
    }

    val ui = theme.ui
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { menu = true; vm.setPaused(true) },
                    modifier = Modifier.background(ui.pill, CircleShape).border(2.dp, ui.pillBorder, CircleShape),
                ) { Icon(Icons.Filled.Menu, stringResource(R.string.menu), tint = ui.onPill) }
                Spacer(Modifier.weight(1f))
                if (undoAllowed && s.canUndo) {
                    IconButton(onClick = vm::undo, modifier = Modifier.background(ui.pill, CircleShape).border(2.dp, ui.pillBorder, CircleShape)) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.undo), tint = ui.onPill)
                    }
                }
            }
            TimerBar(s.turnDeadlineAtMs, game.rules.turnTimerSeconds)
            Spacer(Modifier.weight(1f))
            PodRow(topSeats, s, view, top = true, onRoll = vm::roll)
            BoardCanvas(
                game = game, events = s.lastEvents, eventSeq = s.eventSeq, selectable = selectable,
                activeSeat = activeSeat, timings = s.timings, onTokenTap = vm::tapToken, onHop = vm::step,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                autoPlayers = s.autoPlayers,
                onAutoToggle = if (s.turnPhase == TurnPhase.GAME_OVER) null else vm::toggleAuto,
            )
            PodRow(bottomSeats, s, view, top = false, onRoll = vm::roll)
            Spacer(Modifier.height(10.dp))
            Text(
                hint(s),
                style = MaterialTheme.typography.bodyMedium,
                color = ui.onBackdrop.copy(alpha = 0.9f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.weight(1f))
        }

        if (menu) PauseMenu(
            undo = undoAllowed && s.canUndo,
            canEndEarly = game.finishOrder.isNotEmpty() && s.result == null,
            onEndEarly = { menu = false; vm.endNow() },
            onResume = { menu = false; vm.setPaused(false) },
            onSave = { slot -> vm.saveToSlot(slot); savedToast = slot },
            onUndo = { vm.undo(); menu = false; vm.setPaused(false) },
            onAudio = onAudio,
            onSettings = onSettings,
            onExit = { vm.setPaused(false); onExit() },
        )
    }

    savedToast?.let { slot ->
        LaunchedEffect(slot) { delay(1500); savedToast = null }
        AlertDialog(onDismissRequest = { savedToast = null }, confirmButton = { TextButton({ savedToast = null }) { Text("OK") } },
            text = { Text(stringResource(R.string.saved_to_slot, slot)) })
    }

    s.result?.let { r -> MatchSummary(r, game, onDone = onExit) }
}

@Composable
private fun hint(s: LudoUiState): String {
    val p = s.game.config.players[s.game.current]
    return when {
        s.turnPhase == TurnPhase.GAME_OVER -> stringResource(R.string.game_over)
        s.paused -> stringResource(R.string.paused)
        p.kind == PlayerKind.BOT -> "${p.name} · ${stringResource(R.string.bot_thinking)}"
        s.turnPhase == TurnPhase.WAITING_FOR_ROLL -> "${p.name} · ${stringResource(R.string.tap_dice)}"
        s.turnPhase == TurnPhase.SELECTING_TOKEN -> "${p.name} · ${stringResource(R.string.pick_token)}"
        else -> p.name
    }
}

@Composable
private fun PodRow(seats: List<Int>, s: LudoUiState, view: BoardView, top: Boolean, onRoll: () -> Unit) {
    // Pods are pinned to physical yards on the canvas, so their order never flips in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { PodRowLtr(seats, s, view, top, onRoll) }
}

@Composable
private fun PodRowLtr(seats: List<Int>, s: LudoUiState, view: BoardView, top: Boolean, onRoll: () -> Unit) {
    val game = s.game
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (top) Alignment.Bottom else Alignment.Top,
    ) {
        seats.forEach { seat ->
            val player = game.config.seats.indexOf(seat)
            val active = player >= 0 && player == game.current && s.turnPhase != TurnPhase.GAME_OVER
            // Turn-driven sizing: the active pod grows to one uniform size, the others rest compact.
            val grow by animateFloatAsState(if (active) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "podGrow")
            val mirrored = seats.size > 1 && view.yard(seat).x > 0.5f
            CornerPod(
                seat, s, mirrored, hex = seats.size > 2, grow = grow, onRoll = onRoll,
                modifier = Modifier.weight(1f + (if (seats.size > 2) 0.5f else 0.45f) * grow),
            )
        }
    }
}

/**
 * One seat's pod: recessed dice box, bouncing turn arrow, colour badge (with a robot mark for bots
 * and Auto-Bot players), name and finished-token pips, or the podium title once finished. [grow]
 * (0 = resting, 1 = active) drives every size so all seats expand to exactly the same active size.
 */
@Composable
private fun CornerPod(seat: Int, s: LudoUiState, mirrored: Boolean, hex: Boolean, grow: Float, onRoll: () -> Unit, modifier: Modifier) {
    val game = s.game
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    val player = game.config.seats.indexOf(seat)
    val used = player >= 0
    val active = used && player == game.current && s.turnPhase != TurnPhase.GAME_OVER
    val idle = remember(game.config) { idleSeatColors(game) }
    val color = if (used) theme.tokenColor(game.config.players[player].colorIndex, cb) else theme.tokenColor(idle[seat] ?: seat, cb)
    val inf = rememberInfiniteTransition(label = "pod")
    val glow by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow")
    val bounce by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "bounce")
    val boxSize = if (hex) lerpDp(34.dp, 50.dp, grow) else lerpDp(40.dp, 60.dp, grow)

    val canRoll = active && s.awaitingHuman && s.turnPhase == TurnPhase.WAITING_FOR_ROLL && !s.paused
    val rolling = active && s.rollingPlayer == player
    val dice = s.lastEvents.filterIsInstance<GameEvent.DiceRolled>().lastOrNull()?.value ?: game.dice.takeIf { it > 0 } ?: 6
    val place = if (used) game.finishOrder.indexOf(player) + 1 else 0
    val auto = used && player in s.autoPlayers

    val diceBox: @Composable () -> Unit = {
        Box(
            Modifier.size(boxSize)
                .background(Brush.verticalGradient(if (active) listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.22f))
                    else listOf(Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.08f))), RoundedCornerShape(lerpDp(11.dp, 15.dp, grow)))
                .border(2.dp, if (active) color.copy(alpha = 0.6f + 0.4f * glow) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(lerpDp(11.dp, 15.dp, grow))),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                PodDice(
                    value = dice, rolling = rolling, rollMs = s.timings.rollMs, enabled = canRoll,
                    accent = theme.accent, contentDescription = stringResource(R.string.dice), onClick = onRoll,
                    modifier = Modifier.size(boxSize - 5.dp),
                )
            }
        }
    }
    val arrow: @Composable () -> Unit = {
        Canvas(Modifier.size(width = 14.dp * grow, height = 22.dp).alpha(grow)) {
            // Points at the dice box (outer side), bouncing towards it.
            val dir = if (mirrored) 1f else -1f
            val dx = dir * bounce * 4.dp.toPx()
            val tipX = if (mirrored) size.width else 0f
            val baseX = if (mirrored) size.width * 0.15f else size.width * 0.85f
            val p = Path().apply {
                moveTo(tipX + dx, size.height / 2)
                lineTo(baseX + dx, size.height * 0.1f)
                lineTo(baseX + dx, size.height * 0.9f); close()
            }
            drawPath(p, theme.accent)
            drawPath(p, theme.accent.darken(0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f.dp.toPx()))
        }
    }
    val badge: @Composable () -> Unit = {
        val d = if (hex) lerpDp(24.dp, 28.dp, grow) else lerpDp(26.dp, 34.dp, grow)
        Box(Modifier.size(d)) {
            Canvas(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.92f), CircleShape).border(2.dp, color, CircleShape)) {
                drawPin(Offset(size.width / 2, size.height * 0.84f), size.minDimension * 0.22f, color)
            }
            if (used && (game.config.players[player].kind == PlayerKind.BOT || auto)) {
                Icon(
                    RobotIcon, stringResource(R.string.bot_name),
                    tint = Color.White,
                    modifier = Modifier.size(14.dp).align(Alignment.BottomEnd).offset(4.dp, 3.dp)
                        .background(if (auto) color.darken(0.2f) else Color(0xFF263238), CircleShape).padding(2.dp),
                )
            }
        }
    }
    val label: @Composable (Modifier) -> Unit = { m ->
        Column(
            horizontalAlignment = if (hex) Alignment.CenterHorizontally else if (mirrored) Alignment.End else Alignment.Start,
            modifier = m,
        ) {
            Text(
                if (used) game.config.players[player].name else "—",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = (12.5f + 2.5f * grow).sp),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = theme.ui.onBackdrop, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (used && place > 0) {
                RankChip(place, small = grow < 0.5f)
            } else if (used) {
                val home = game.tokensHome(player)
                val goal = game.rules.tokensToWin
                val dot = lerpDp(6.dp, 8.dp, grow)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0 until goal) Box(
                        Modifier.size(dot).background(if (i < home) color else theme.ui.onBackdrop.copy(alpha = 0.22f), CircleShape)
                            .border(1.dp, if (i < home) Color.White else Color.Transparent, CircleShape),
                    )
                    // The compact resting pod keeps just the dots; the active pod adds the X/4 count.
                    if (grow > 0.5f) {
                        Spacer(Modifier.width(1.dp))
                        Text("$home/$goal", style = MaterialTheme.typography.labelSmall, color = theme.ui.onBackdrop.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
                if (auto) Text("AUTO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = theme.accent, maxLines = 1)
            }
        }
    }

    val shape = RoundedCornerShape(18.dp)
    // Dimensional plate: lit from above, with a soft lip underneath; the active seat glows in its colour.
    val lightBackdrop = theme.ui.backdropBottom.relativeLuminance() > 0.4
    val plateTop = if (lightBackdrop) Color.White.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.16f)
    val plateBottom = if (lightBackdrop) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.2f)
    val podModifier = modifier
            .alpha(if (used) 1f else 0.35f)
            .drawBehind {
                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(0f, 3.dp.toPx()), size, CornerRadius(18.dp.toPx()))
                if (active) drawRoundRect(color.copy(alpha = 0.28f * glow), Offset(-3.dp.toPx(), -3.dp.toPx()),
                    Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(21.dp.toPx()))
            }
            .background(Brush.verticalGradient(listOf(plateTop, plateBottom)), shape)
            .background(if (active) color.copy(alpha = 0.18f + 0.14f * glow) else Color.Transparent, shape)
            .border(if (active) 2.dp else 1.dp, if (active) color.copy(alpha = 0.6f + 0.4f * glow) else Color.White.copy(alpha = 0.3f), shape)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics { contentDescription = if (used) "Seat ${game.config.players[player].name}${if (active) ", active" else ""}" else "Empty seat" }
    if (hex) {
        // Hex board: three pods per row, so the name sits under the dice instead of beside it.
        Column(podModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (mirrored) { badge(); arrow(); diceBox() } else { diceBox(); arrow(); badge() }
            }
            label(Modifier.fillMaxWidth())
        }
    } else {
        Row(podModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mirrored) { label(Modifier.weight(1f)); badge(); arrow(); diceBox() } else { diceBox(); arrow(); badge(); label(Modifier.weight(1f)) }
        }
    }
}

private fun lerpDp(a: Dp, b: Dp, t: Float): Dp = a + (b - a) * t

/** Podium title chip: Ultra (gold), Pro (silver), Max (bronze), Aspirant (blue). */
@Composable
internal fun RankChip(place: Int, small: Boolean = false) {
    val (bg, fg) = podiumColors(place)
    Box(
        Modifier.background(bg, RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(50))
            .padding(horizontal = if (small) 6.dp else 8.dp, vertical = 1.dp),
    ) {
        Text("${ordinal(place)} · ${Podium.title(place)}", color = fg, fontWeight = FontWeight.ExtraBold, maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (small) 10.sp else 11.sp))
    }
}

internal fun podiumColors(place: Int): Pair<Color, Color> = when (place) {
    1 -> Color(0xFFFFC62F) to Color(0xFF3E2723)
    2 -> Color(0xFFCFD8DC) to Color(0xFF263238)
    3 -> Color(0xFFD08B5B) to Color.White
    else -> Color(0xFF5C8DBF) to Color.White
}

internal fun ordinal(n: Int): String = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

@Composable
private fun PauseMenu(
    undo: Boolean,
    canEndEarly: Boolean,
    onEndEarly: () -> Unit,
    onResume: () -> Unit,
    onSave: (Int) -> Unit,
    onUndo: () -> Unit,
    onAudio: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onResume),
        contentAlignment = Alignment.Center,
    ) {
        val ui = LocalBoardTheme.current.ui
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ui.card, contentColor = ui.onCard),
            border = BorderStroke(2.dp, ui.cardBorder),
            modifier = Modifier.padding(24.dp).fillMaxWidth().clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CtaButton(stringResource(R.string.resume), onResume, Modifier.fillMaxWidth(), icon = Icons.Filled.PlayArrow)
                Text(stringResource(R.string.save_game), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (slot in 1..3) OutlinedButton(
                        onClick = { onSave(slot) }, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "Save to slot $slot" },
                    ) { Text("$slot", style = MaterialTheme.typography.titleSmall) }
                }
                if (undo) FilledTonalButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.undo)) }
                if (canEndEarly) FilledTonalButton(onClick = onEndEarly, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.end_match_results))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onAudio, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.audio_studio)) }
                    FilledTonalButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings)) }
                }
                TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.exit_match))
                }
            }
        }
    }
}

@Composable
private fun TimerBar(deadline: Long?, seconds: Int) {
    if (deadline == null || seconds <= 0) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(deadline) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val frac = ((deadline - now).toFloat() / (seconds * 1000f)).coerceIn(0f, 1f)
    val ui = LocalBoardTheme.current.ui
    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = if (frac < 0.3f) Color(0xFFFF5252) else ui.cta, trackColor = ui.onBackdrop.copy(alpha = 0.25f))
}
