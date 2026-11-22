package com.ludosovereign.feature.board

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ludosovereign.designsystem.LocalBoardTheme
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

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(theme) }
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { menu = true; vm.setPaused(true) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.22f), CircleShape),
                ) { Icon(Icons.Filled.Menu, stringResource(R.string.menu), tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (undoAllowed && s.canUndo) {
                    IconButton(onClick = vm::undo, modifier = Modifier.background(Color.Black.copy(alpha = 0.22f), CircleShape)) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.undo), tint = Color.White)
                    }
                }
            }
            TimerBar(s.turnDeadlineAtMs, game.rules.turnTimerSeconds)
            Spacer(Modifier.weight(1f))
            PodRow(topSeats, s, view, top = true, onRoll = vm::roll)
            BoardCanvas(
                game = game, events = s.lastEvents, eventSeq = s.eventSeq, selectable = selectable,
                activeSeat = activeSeat, timings = s.timings, onTokenTap = vm::tapToken, onHop = vm::step,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            )
            PodRow(bottomSeats, s, view, top = false, onRoll = vm::roll)
            Spacer(Modifier.height(10.dp))
            Text(
                hint(s),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.weight(1f))
        }

        if (menu) PauseMenu(
            undo = undoAllowed && s.canUndo,
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

    s.result?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.winner, r.players[r.finishOrder.first()])) },
            text = {
                Column {
                    r.finishOrder.forEachIndexed { place, p ->
                        val st = r.stats?.players?.getOrNull(p)
                        Text("${place + 1}. ${r.players[p]}  ·  ${stringResource(R.string.captures_short, st?.captures ?: 0)}  ·  ${stringResource(R.string.fortresses_short, st?.fortressesBuilt ?: 0)}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.done)) } },
        )
    }
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = if (seats.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
        verticalAlignment = if (top) Alignment.Bottom else Alignment.Top,
    ) {
        seats.forEach { seat ->
            val mirrored = seats.size > 1 && view.yard(seat).x > 0.5f
            CornerPod(seat, s, mirrored, compact = seats.size > 2, onRoll = onRoll)
        }
    }
}

/**
 * One seat's pod: recessed dice box, bouncing turn arrow, colour badge (with a robot mark for bots),
 * name and finished-token pips. Unused seats render as a dimmed empty slot.
 */
@Composable
private fun CornerPod(seat: Int, s: LudoUiState, mirrored: Boolean, compact: Boolean, onRoll: () -> Unit) {
    val game = s.game
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    val player = game.config.seats.indexOf(seat)
    val used = player >= 0
    val active = used && player == game.current && s.turnPhase != TurnPhase.GAME_OVER
    val color = if (used) theme.tokenColor(game.config.players[player].colorIndex, cb) else Color.Gray
    val inf = rememberInfiniteTransition(label = "pod")
    val glow by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "glow")
    val bounce by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "bounce")
    val boxSize = if (compact) 44.dp else 54.dp

    val canRoll = active && s.awaitingHuman && s.turnPhase == TurnPhase.WAITING_FOR_ROLL && !s.paused
    val rolling = active && s.rollingPlayer == player
    val dice = s.lastEvents.filterIsInstance<GameEvent.DiceRolled>().lastOrNull()?.value ?: game.dice.takeIf { it > 0 } ?: 6

    val diceBox: @Composable () -> Unit = {
        Box(
            Modifier.size(boxSize)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.22f))), RoundedCornerShape(14.dp))
                .border(2.dp, if (active) color.copy(alpha = 0.6f + 0.4f * glow) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                PodDice(
                    value = dice, rolling = rolling, rollMs = s.timings.rollMs, enabled = canRoll,
                    accent = theme.accent, contentDescription = stringResource(R.string.dice), onClick = onRoll,
                    modifier = Modifier.size(boxSize - 6.dp),
                )
            }
        }
    }
    val arrow: @Composable () -> Unit = {
        Canvas(Modifier.size(width = 16.dp, height = 22.dp).alpha(if (active) 1f else 0f)) {
            // Points at the dice box (outer side), bouncing towards it.
            val dir = if (mirrored) 1f else -1f
            val dx = dir * bounce * 5.dp.toPx()
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
        Box(Modifier.size(if (compact) 28.dp else 32.dp)) {
            Canvas(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.92f), CircleShape).border(2.dp, color, CircleShape)) {
                drawPin(Offset(size.width / 2, size.height * 0.84f), size.minDimension * 0.22f, color)
            }
            if (used && game.config.players[player].kind == PlayerKind.BOT) {
                Icon(
                    RobotIcon, stringResource(R.string.bot_name),
                    tint = Color.White,
                    modifier = Modifier.size(15.dp).align(Alignment.BottomEnd).offset(4.dp, 3.dp)
                        .background(Color(0xFF263238), CircleShape).padding(2.dp),
                )
            }
        }
    }
    val label: @Composable () -> Unit = {
        Column(
            horizontalAlignment = if (compact) Alignment.CenterHorizontally else if (mirrored) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = if (compact) 100.dp else 96.dp),
        ) {
            Text(
                if (used) game.config.players[player].name else "—",
                style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = theme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (used) {
                val home = game.tokensHome(player)
                val goal = game.rules.tokensToWin
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (i in 0 until goal) Box(Modifier.size(7.dp).background(if (i < home) color else Color.White.copy(alpha = 0.25f), CircleShape))
                    Text("$home/$goal", style = MaterialTheme.typography.labelSmall, color = theme.onSurface.copy(alpha = 0.8f))
                }
            }
        }
    }

    val shape = RoundedCornerShape(18.dp)
    val podModifier = Modifier
            .alpha(if (used) 1f else 0.35f)
            .background(if (active) color.copy(alpha = 0.16f + 0.12f * glow) else Color.Black.copy(alpha = 0.14f), shape)
            .border(if (active) 2.dp else 1.dp, if (active) color.copy(alpha = 0.5f + 0.5f * glow) else Color.White.copy(alpha = 0.12f), shape)
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .semantics { contentDescription = if (used) "Seat ${game.config.players[player].name}${if (active) ", active" else ""}" else "Empty seat" }
    if (compact) {
        // Hex board: three pods per row, so the name sits under the dice instead of beside it.
        Column(podModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (mirrored) { badge(); arrow(); diceBox() } else { diceBox(); arrow(); badge() }
            }
            label()
        }
    } else {
        Row(podModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (mirrored) { label(); badge(); arrow(); diceBox() } else { diceBox(); arrow(); badge(); label() }
        }
    }
}

@Composable
private fun PauseMenu(
    undo: Boolean,
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
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(24.dp).fillMaxWidth().clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(stringResource(R.string.resume)) }
                Text(stringResource(R.string.save_game), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (slot in 1..3) OutlinedButton(
                        onClick = { onSave(slot) }, modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "Save to slot $slot" },
                    ) { Text("$slot", style = MaterialTheme.typography.titleSmall) }
                }
                if (undo) FilledTonalButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.undo)) }
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
    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = if (frac < 0.3f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}
