package com.ludosovereign.session

import com.ludosovereign.engine.DiceSource
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.StateCodec
import com.ludosovereign.engine.snl.SnakesAndLaddersEngine
import com.ludosovereign.engine.snl.SnlEvent
import com.ludosovereign.engine.snl.SnlState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SnlUiState(
    val game: SnlState,
    val lastEvents: List<SnlEvent> = emptyList(),
    val eventSeq: Long = 0,
    val rollingPlayer: Int = -1,
    val awaitingHuman: Boolean = false,
    val result: MatchResult? = null,
)

/** Snakes & Ladders has no decisions, so bots simply roll fair dice on their turn. */
class SnlSession(
    initial: SnlState,
    private val dice: DiceSource,
    private val settings: SessionSettings = SessionSettings(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCheckpoint: suspend (Checkpoint) -> Unit = {},
    private val onFinished: suspend (MatchResult) -> Unit = {},
) {
    private var game = initial
    private var seq = 0L
    private val startedAt = clock()
    private val rolls = Channel<Unit>(Channel.CONFLATED)
    private val _ui = MutableStateFlow(SnlUiState(initial))
    val ui: StateFlow<SnlUiState> = _ui.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope): Job = scope.launch { loop() }.also { job = it }
    fun stop() { job?.cancel() }
    fun roll() { rolls.trySend(Unit) }

    private suspend fun loop() {
        while (!game.gameOver) {
            val p = game.current
            if (game.players[p].kind == PlayerKind.BOT) {
                _ui.update { it.copy(rollingPlayer = p, awaitingHuman = false) }
                delay(settings.rollMs)
            } else {
                _ui.update { it.copy(awaitingHuman = true, rollingPlayer = -1) }
                rolls.receive()
                _ui.update { it.copy(rollingPlayer = p, awaitingHuman = false) }
            }
            val events = ArrayList<SnlEvent>()
            game = SnakesAndLaddersEngine.roll(game, dice.roll(), events)
            _ui.update { it.copy(game = game, lastEvents = events, eventSeq = ++seq, rollingPlayer = -1) }
            onCheckpoint(Checkpoint(GameKind.SNAKES_AND_LADDERS, StateCodec.encode(game), null, null,
                "${game.layout.displayName} · ${game.players.size}P", game.gameOver))
            val travelled = events.sumOf { e ->
                when (e) {
                    is SnlEvent.Moved -> e.to - e.from
                    is SnlEvent.LadderClimb, is SnlEvent.SnakeSlide -> 4
                    else -> 0
                }
            }
            delay(settings.moveMs / 4 * travelled.coerceAtMost(12) + settings.moveMs)
        }
        val result = MatchResult(
            GameKind.SNAKES_AND_LADDERS, "SNAKES_AND_LADDERS", game.layoutId,
            game.players.map { it.name }, game.players.map { it.kind == PlayerKind.HUMAN },
            game.players.map { it.profileId }, game.finishOrder.toList(), game.finishOrder.first(),
            null, null, clock() - startedAt,
        )
        _ui.update { it.copy(result = result, awaitingHuman = false) }
        onFinished(result)
    }
}
