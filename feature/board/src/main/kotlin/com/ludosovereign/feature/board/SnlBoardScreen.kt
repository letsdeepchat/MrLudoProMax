package com.ludosovereign.feature.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ludosovereign.data.RoomSaveRepository
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.PaletteCard
import com.ludosovereign.designsystem.backdropTopBarColors
import com.ludosovereign.designsystem.darken
import androidx.compose.foundation.shape.CircleShape
import com.ludosovereign.designsystem.PodDice
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.drawPin
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.FairDice
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.StateCodec
import com.ludosovereign.engine.snl.SnlEvent
import com.ludosovereign.engine.snl.SnlLayout
import com.ludosovereign.engine.snl.SnlState
import com.ludosovereign.session.Checkpoint
import com.ludosovereign.session.HistoryRepository
import com.ludosovereign.session.SaveRepository
import com.ludosovereign.session.SessionSettings
import com.ludosovereign.session.SnlSession
import com.ludosovereign.sound.GameFeedback
import kotlinx.coroutines.launch

class SnlViewModel(
    initial: SnlState,
    settings: SessionSettings,
    private val saves: RoomSaveRepository,
    history: HistoryRepository,
    private val feedback: GameFeedback,
) : ViewModel() {
    private val session = SnlSession(initial, FairDice(), settings,
        onCheckpoint = ::autoSave, onFinished = { history.record(it) })
    val ui = session.ui

    init {
        session.start(viewModelScope)
        viewModelScope.launch {
            var seq = -1L
            var rolling = -1
            session.ui.collect { s ->
                if (s.rollingPlayer >= 0 && s.rollingPlayer != rolling) feedback.diceTumble()
                rolling = s.rollingPlayer
                if (s.eventSeq != seq) { seq = s.eventSeq; feedback.onSnlEvents(s.lastEvents) }
            }
        }
    }

    private suspend fun autoSave(c: Checkpoint) {
        if (c.finished) saves.clear(SaveRepository.AUTO_SLOT) else saves.save(SaveRepository.AUTO_SLOT, c)
    }

    fun roll() = session.roll()
    fun step() = feedback.step()
    override fun onCleared() = session.stop()

    class Factory(
        private val initial: SnlState,
        private val settings: SessionSettings,
        private val saves: RoomSaveRepository,
        private val history: HistoryRepository,
        private val feedback: GameFeedback,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SnlViewModel(initial, settings, saves, history, feedback) as T
    }

    companion object {
        fun resume(c: Checkpoint): SnlState = StateCodec.decodeSnl(c.stateJson)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnlBoardScreen(vm: SnlViewModel, onExit: () -> Unit) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    val game = s.game
    val current = game.players[game.current]
    val color = theme.tokenColor(current.colorIndex, colorblind)
    val lastRoll = s.lastEvents.filterIsInstance<SnlEvent.Rolled>().lastOrNull()?.value ?: 1

    val ui = theme.ui
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = ui.onBackdrop,
        topBar = {
            TopAppBar(
                title = { Text(game.layout.displayName, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = backdropTopBarColors(),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SnlCanvas(game, s.eventSeq, s.lastEvents, vm::step,
                Modifier.fillMaxWidth().weight(1f, fill = false)
                    .border(3.dp, theme.frame, RoundedCornerShape(6.dp)).padding(3.dp))
            Spacer(Modifier.height(12.dp))
            PaletteCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(14.dp).background(color, CircleShape).border(2.dp, color.darken(0.35f), CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text(if (current.kind == PlayerKind.BOT) stringResource(R.string.bot_thinking) else stringResource(R.string.tap_dice),
                            color = ui.onCard.copy(alpha = 0.75f))
                    }
                    Box(
                        Modifier.size(64.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.2f))), RoundedCornerShape(16.dp))
                            .border(2.dp, color, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PodDice(lastRoll, s.rollingPlayer >= 0, Modifier.size(56.dp), enabled = s.awaitingHuman,
                            accent = theme.accent, contentDescription = stringResource(R.string.dice), onClick = vm::roll)
                    }
                }
            }
        }
    }
    s.result?.let { r ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.winner, r.players[r.finishOrder.first()])) },
            text = { Column { r.finishOrder.forEachIndexed { i, p -> Text("${i + 1}. ${r.players[p]}") } } },
            confirmButton = { TextButton(onClick = onExit) { Text(stringResource(R.string.done)) } },
        )
    }
}

private fun cellCenter(layout: SnlLayout, cell: Int, unit: Float): Offset {
    if (cell <= 0) return Offset(-unit * 0.5f, layout.side * unit - unit * 0.5f)
    val (row, col) = layout.gridOf(cell)
    return Offset(col * unit + unit / 2, (layout.side - 1 - row) * unit + unit / 2)
}

@Composable
private fun SnlCanvas(game: SnlState, seq: Long, events: List<SnlEvent>, onStep: () -> Unit, modifier: Modifier) {
    val theme = LocalBoardTheme.current
    val colorblind = LocalColorblind.current
    val layout = game.layout
    val measurer = rememberTextMeasurer()
    // Positions are animated in "cell units" so they scale with the canvas.
    val anims = remember(game.layoutId, game.players.size) {
        game.positions.map { Animatable(cellCenter(layout, it, 1f), Offset.VectorConverter) }
    }
    LaunchedEffect(seq) {
        for (e in events) when (e) {
            is SnlEvent.Moved -> for (c in (e.from + 1)..e.to) {
                anims[e.player].animateTo(cellCenter(layout, c, 1f), tween(110, easing = LinearEasing)); onStep()
            }
            is SnlEvent.LadderClimb -> anims[e.player].animateTo(cellCenter(layout, e.to, 1f), tween(650, easing = FastOutSlowInEasing))
            is SnlEvent.SnakeSlide -> anims[e.player].animateTo(cellCenter(layout, e.to, 1f), tween(850, easing = FastOutSlowInEasing))
            else -> Unit
        }
        game.positions.forEachIndexed { i, p -> anims[i].snapTo(cellCenter(layout, p, 1f)) }
    }
    Canvas(modifier.aspectRatio(1f)) {
        val unit = size.minDimension / layout.side
        for (cell in 1..layout.size) {
            val (row, col) = layout.gridOf(cell)
            val tl = Offset(col * unit, (layout.side - 1 - row) * unit)
            val alt = if (theme.safeCell == theme.cell) androidx.compose.ui.graphics.lerp(theme.cell, theme.accent, 0.22f) else theme.safeCell
            val fill = if ((row + col) % 2 == 0) theme.cell else alt
            drawRect(fill, tl, Size(unit, unit))
            drawRect(theme.cellBorder.copy(alpha = 0.25f), tl, Size(unit, unit), style = Stroke(1f))
            drawNumber(measurer, cell.toString(), tl + Offset(unit * 0.08f, unit * 0.04f), theme.cellBorder.copy(alpha = 0.6f), unit)
        }
        layout.ladders.forEach { (b, t) ->
            val a = cellCenter(layout, b, unit); val z = cellCenter(layout, t, unit)
            val dir = (z - a); val len = dir.getDistance(); val n = Offset(-dir.y / len, dir.x / len) * (unit * 0.18f)
            val rail = Color(0xFF8D6E63)
            drawLine(rail, a + n, z + n, unit * 0.08f, StrokeCap.Round)
            drawLine(rail, a - n, z - n, unit * 0.08f, StrokeCap.Round)
            val rungs = (len / (unit * 0.4f)).toInt().coerceAtLeast(2)
            for (k in 1 until rungs) {
                val p = a + dir * (k / rungs.toFloat())
                drawLine(rail.copy(alpha = 0.9f), p + n, p - n, unit * 0.05f)
            }
        }
        layout.snakes.forEach { (h, t) ->
            val head = cellCenter(layout, h, unit); val tail = cellCenter(layout, t, unit)
            val mid = (head + tail) / 2f
            val dir = tail - head; val perp = Offset(-dir.y, dir.x) * 0.25f
            val path = Path().apply {
                moveTo(head.x, head.y)
                cubicTo(head.x + perp.x + dir.x * 0.2f, head.y + perp.y + dir.y * 0.2f, mid.x - perp.x, mid.y - perp.y, mid.x, mid.y)
                cubicTo(mid.x + perp.x, mid.y + perp.y, tail.x - perp.x * 0.5f, tail.y - perp.y * 0.5f, tail.x, tail.y)
            }
            drawPath(path, Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF9CCC65)), head, tail), style = Stroke(unit * 0.2f, cap = StrokeCap.Round))
            drawCircle(Color(0xFF1B5E20), unit * 0.17f, head)
            drawCircle(Color.White, unit * 0.04f, head + Offset(-unit * 0.06f, -unit * 0.04f))
            drawCircle(Color.White, unit * 0.04f, head + Offset(unit * 0.06f, -unit * 0.04f))
        }
        game.players.forEachIndexed { i, p ->
            val jitter = Offset(((i % 3) - 1) * unit * 0.18f, ((i / 3) - 0.5f) * unit * 0.2f)
            drawPin(anims[i].value * unit + jitter + Offset(0f, unit * 0.18f), unit * 0.2f, theme.tokenColor(p.colorIndex, colorblind),
                glyph = if (colorblind) BoardThemes.GLYPHS[i % BoardThemes.GLYPHS.size] else null)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNumber(m: TextMeasurer, s: String, at: Offset, color: Color, unit: Float) {
    drawText(m, s, at, style = TextStyle(color = color, fontSize = (unit / density * 0.22f).sp))
}
