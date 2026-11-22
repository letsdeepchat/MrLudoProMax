package com.ludosovereign.session

import com.ludosovereign.ai.LudoBot
import com.ludosovereign.ai.MoveScorer
import com.ludosovereign.engine.DiceSource
import com.ludosovereign.engine.GameAction
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchLog
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.StateCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class SessionSettings(
    /** Play the only legal move automatically. */
    val autoMove: Boolean = true,
    /** Allow undoing the last human move (Casual Pass & Play). */
    val undoEnabled: Boolean = false,
    /** Dice tumble length; bot computation runs concurrently and is hidden behind it. */
    val rollAnimationMs: Long = 450,
    /** Pause after a move so the token animation can finish before the next actor. */
    val moveAnimationMs: Long = 350,
    /** Rush mode halves the animation timings. */
    val fastAnimations: Boolean = false,
) {
    val rollMs get() = if (fastAnimations) rollAnimationMs / 2 else rollAnimationMs
    val moveMs get() = if (fastAnimations) moveAnimationMs / 2 else moveAnimationMs
}

data class LudoUiState(
    val game: GameState,
    /** Events produced by the most recent action; [eventSeq] changes with every batch. */
    val lastEvents: List<GameEvent> = emptyList(),
    val eventSeq: Long = 0,
    /** Player whose dice is tumbling right now (bots), or -1. */
    val rollingPlayer: Int = -1,
    val awaitingHuman: Boolean = false,
    val legalMoves: IntArray = IntArray(0),
    val canUndo: Boolean = false,
    /** Wall-clock deadline of the Rush turn timer, or null. */
    val turnDeadlineAtMs: Long? = null,
    val stats: MatchStats,
    val result: MatchResult? = null,
)

/**
 * Orchestrates one Ludo match: human intents, bot turns (computed on [aiDispatcher] while the dice
 * animates), auto-move, undo, the Rush turn timer, stats, and an auto-save checkpoint after every
 * applied action. All game rules stay in [LudoEngine]; this class only sequences them.
 */
class MatchSession(
    initial: GameState,
    initialLog: MatchLog = MatchLog(initial.config),
    initialStats: MatchStats = MatchStats.empty(initial.config),
    private val bots: Map<Int, LudoBot>,
    private val dice: DiceSource,
    private val settings: SessionSettings = SessionSettings(),
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCheckpoint: suspend (Checkpoint) -> Unit = {},
    private val onFinished: suspend (MatchResult) -> Unit = {},
) {
    private sealed interface Intent {
        data object Roll : Intent
        data class Move(val tokenId: Int) : Intent
        data object Undo : Intent
    }

    private var game = initial
    private var matchLog = initialLog
    private var stats = initialStats
    private val startedAt = clock()
    private var undoPoint: Triple<GameState, MatchLog, MatchStats>? = null
    private val intents = Channel<Intent>(Channel.UNLIMITED)
    private var seq = 0L
    private var job: Job? = null

    private val _ui = MutableStateFlow(LudoUiState(game = initial, stats = initialStats))
    val ui: StateFlow<LudoUiState> = _ui.asStateFlow()

    val log: MatchLog get() = matchLog

    fun start(scope: CoroutineScope): Job = scope.launch { runLoop() }.also { job = it }
    fun stop() { job?.cancel() }

    fun roll() { intents.trySend(Intent.Roll) }
    fun move(tokenId: Int) { intents.trySend(Intent.Move(tokenId)) }
    fun undo() { intents.trySend(Intent.Undo) }

    private fun isBot(player: Int) = game.config.players[player].kind == PlayerKind.BOT && bots.containsKey(player)

    private suspend fun runLoop() {
        publish(emptyList())
        while (game.phase != Phase.GAME_OVER) {
            if (isBot(game.current)) botStep(bots.getValue(game.current)) else humanStep()
        }
        finish()
    }

    private suspend fun botStep(bot: LudoBot) {
        val player = game.current
        when (game.phase) {
            Phase.AWAITING_ROLL -> {
                _ui.update { it.copy(rollingPlayer = player, awaitingHuman = false, legalMoves = IntArray(0), turnDeadlineAtMs = null) }
                val snapshot = game
                val value = coroutineScope {
                    val pick = async(aiDispatcher) { bot.chooseDice(snapshot) }
                    delay(settings.rollMs)
                    pick.await() ?: dice.roll()
                }
                apply(GameAction.Roll(value))
            }
            Phase.AWAITING_MOVE -> {
                val snapshot = game
                val token = coroutineScope {
                    val pick = async(aiDispatcher) { bot.chooseMove(snapshot) }
                    delay(settings.moveMs / 2)
                    pick.await()
                }
                apply(GameAction.MoveToken(token))
                delay(settings.moveMs)
            }
            Phase.GAME_OVER -> Unit
        }
    }

    private suspend fun humanStep() {
        val timerMs = game.rules.turnTimerSeconds * 1000L
        when (game.phase) {
            Phase.AWAITING_ROLL -> {
                val deadline = if (timerMs > 0) clock() + timerMs else null
                _ui.update {
                    it.copy(awaitingHuman = true, rollingPlayer = -1, legalMoves = IntArray(0),
                        canUndo = undoPoint != null, turnDeadlineAtMs = deadline)
                }
                when (val intent = awaitIntent(timerMs)) {
                    Intent.Undo -> { performUndo(); return }
                    is Intent.Move -> return // not expecting a move; ignore
                    Intent.Roll, null -> {
                        undoPoint = null
                        _ui.update { it.copy(rollingPlayer = game.current, awaitingHuman = false, canUndo = false) }
                        apply(GameAction.Roll(dice.roll()))
                        if (game.phase == Phase.AWAITING_MOVE) delay(settings.rollMs)
                    }
                }
            }
            Phase.AWAITING_MOVE -> {
                val legal = LudoEngine.legalMoves(game)
                if (settings.autoMove && legal.size == 1) {
                    rememberUndo()
                    apply(GameAction.MoveToken(legal[0]))
                    delay(settings.moveMs)
                    return
                }
                val deadline = if (timerMs > 0) clock() + timerMs else null
                _ui.update { it.copy(awaitingHuman = true, rollingPlayer = -1, legalMoves = legal, turnDeadlineAtMs = deadline) }
                while (true) {
                    when (val intent = awaitIntent(timerMs)) {
                        null -> { // timer expired: play the best-looking move
                            rememberUndo()
                            apply(GameAction.MoveToken(legal.maxBy { MoveScorer.score(game, it) }))
                            return
                        }
                        is Intent.Move -> {
                            val id = resolveTap(intent.tokenId) ?: continue
                            rememberUndo()
                            apply(GameAction.MoveToken(id))
                            delay(settings.moveMs)
                            return
                        }
                        else -> continue
                    }
                }
            }
            Phase.GAME_OVER -> Unit
        }
    }

    /** Maps a tapped token (possibly a stacked twin) to a legal move. */
    private fun resolveTap(tokenId: Int): Int? {
        if (tokenId !in game.tokens.indices || !LudoEngine.isLegal(game, tokenId)) return null
        return tokenId
    }

    private suspend fun awaitIntent(timerMs: Long): Intent? =
        if (timerMs > 0) withTimeoutOrNull(timerMs) { intents.receive() } else intents.receive()

    private fun rememberUndo() {
        undoPoint = if (settings.undoEnabled) Triple(game, matchLog, stats) else null
    }

    private suspend fun performUndo() {
        val point = undoPoint ?: return
        undoPoint = null
        game = point.first; matchLog = point.second; stats = point.third
        publish(emptyList())
        checkpoint()
    }

    private suspend fun apply(action: GameAction) {
        val events = ArrayList<GameEvent>()
        val before = game
        game = withContext(aiDispatcher) { LudoEngine.reduce(before, action, events) }
        matchLog = matchLog.append(action)
        stats = stats.fold(before, events)
        publish(events)
        checkpoint()
    }

    private fun publish(events: List<GameEvent>) {
        val g = game
        _ui.update {
            it.copy(
                game = g, lastEvents = events, eventSeq = ++seq,
                rollingPlayer = -1,
                awaitingHuman = false,
                legalMoves = if (g.phase == Phase.AWAITING_MOVE) LudoEngine.legalMoves(g) else IntArray(0),
                canUndo = undoPoint != null,
                stats = stats,
            )
        }
    }

    private suspend fun checkpoint() {
        onCheckpoint(
            Checkpoint(
                kind = GameKind.LUDO,
                stateJson = StateCodec.encode(game),
                logJson = StateCodec.encode(matchLog),
                statsJson = StateCodec.json.encodeToString(MatchStats.serializer(), stats),
                summary = summary(),
                finished = game.phase == Phase.GAME_OVER,
            ),
        )
    }

    private fun summary(): String {
        val cfg = game.config
        val leader = (0 until cfg.playerCount).maxBy { LudoEngine.totalProgress(game, it) }
        return "${cfg.rules.variant.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
            "${cfg.playerCount}P · turn ${game.rollCount} · ${cfg.players[leader].name} leads"
    }

    private suspend fun finish() {
        val cfg = game.config
        val result = MatchResult(
            kind = GameKind.LUDO,
            variant = cfg.rules.variant.name,
            board = cfg.topology.name,
            players = cfg.players.map { it.name },
            humanFlags = cfg.players.map { it.kind == PlayerKind.HUMAN },
            profileIds = cfg.players.map { it.profileId },
            finishOrder = game.finishOrder.toList(),
            winnerTeam = game.winnerTeam,
            stats = stats,
            log = matchLog,
            durationMillis = clock() - startedAt,
            teams = cfg.teams.toList(),
            botTiers = cfg.players.map { it.botTier?.name },
        )
        _ui.update { it.copy(result = result, awaitingHuman = false, turnDeadlineAtMs = null) }
        onFinished(result)
    }

    companion object {
        /** Rebuilds a session from an auto-save checkpoint. */
        fun restore(
            checkpoint: Checkpoint,
            bots: (GameState) -> Map<Int, LudoBot>,
            dice: DiceSource,
            settings: SessionSettings,
            onCheckpoint: suspend (Checkpoint) -> Unit,
            onFinished: suspend (MatchResult) -> Unit,
        ): MatchSession {
            val state = StateCodec.decodeGame(checkpoint.stateJson)
            val log = checkpoint.logJson?.let(StateCodec::decodeLog) ?: MatchLog(state.config)
            val stats = checkpoint.statsJson?.let { StateCodec.json.decodeFromString(MatchStats.serializer(), it) }
                ?: MatchStats.empty(state.config)
            return MatchSession(state, log, stats, bots(state), dice, settings,
                onCheckpoint = onCheckpoint, onFinished = onFinished)
        }
    }
}
