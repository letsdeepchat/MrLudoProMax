package com.ludosovereign.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ludosovereign.designsystem.IsometricDice3D
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LudoBoardScreen(
    vm: LudoBoardViewModel,
    animationSpeed: Float,
    undoAllowed: Boolean,
    onExit: () -> Unit,
) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val game = s.game
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    var menu by remember { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf<String?>(null) }
    val current = game.config.players[game.current]
    val currentColor = theme.tokenColor(current.colorIndex, colorblind)
    val lastDice = s.lastEvents.filterIsInstance<GameEvent.DiceRolled>().lastOrNull()?.value
        ?: game.dice.takeIf { it > 0 } ?: 1

    Scaffold(
        containerColor = theme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.board_title)) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (undoAllowed && s.canUndo) {
                        IconButton(onClick = vm::undo) { Icon(Icons.Filled.Refresh, stringResource(R.string.undo)) }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.menu)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            for (slot in 1..3) DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_to_slot, slot)) },
                                onClick = { vm.saveToSlot(slot); menu = false; savedToast = "$slot" },
                            )
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerStrip(s.game, s.stats.players.map { it.captures })
            TimerBar(s.turnDeadlineAtMs, game.rules.turnTimerSeconds)
            Spacer(Modifier.height(8.dp))
            BoardCanvas(
                game = game, events = s.lastEvents, eventSeq = s.eventSeq, awaitingHuman = s.awaitingHuman,
                animationSpeed = animationSpeed, onTokenTap = vm::tapToken, onStep = vm::step,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(current.name, style = MaterialTheme.typography.titleLarge, color = currentColor, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            game.phase == Phase.GAME_OVER -> stringResource(R.string.game_over)
                            current.kind == PlayerKind.BOT -> stringResource(R.string.bot_thinking)
                            game.phase == Phase.AWAITING_ROLL -> stringResource(R.string.tap_dice)
                            else -> stringResource(R.string.pick_token)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IsometricDice3D(
                    value = lastDice,
                    rolling = s.rollingPlayer >= 0,
                    color = currentColor,
                    enabled = s.awaitingHuman && game.phase == Phase.AWAITING_ROLL,
                    contentDescription = stringResource(R.string.dice),
                    onClick = vm::roll,
                    modifier = Modifier.size(96.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    savedToast?.let { slot ->
        LaunchedEffect(slot) { delay(1500); savedToast = null }
        AlertDialog(onDismissRequest = { savedToast = null }, confirmButton = { TextButton({ savedToast = null }) { Text("OK") } },
            text = { Text(stringResource(R.string.saved_to_slot, slot.toInt())) })
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
private fun PlayerStrip(game: com.ludosovereign.engine.GameState, captures: List<Int>) {
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(game.config.players) { i, p ->
            val active = i == game.current && game.phase != Phase.GAME_OVER
            val color = theme.tokenColor(p.colorIndex, colorblind)
            Row(
                Modifier
                    .background(if (active) color.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(14.dp))
                    .border(if (active) 2.dp else 1.dp, color.copy(alpha = if (active) 1f else 0.4f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(p.name, style = MaterialTheme.typography.labelLarge)
                    Text("${game.tokensHome(i)}/${game.rules.tokensToWin} ⌂ · ✂ ${captures.getOrElse(i) { 0 }}", style = MaterialTheme.typography.labelSmall)
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
    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        color = if (frac < 0.3f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}
