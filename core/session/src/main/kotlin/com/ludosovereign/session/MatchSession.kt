package com.ludosovereign.session

import com.ludosovereign.ai.UnbeatableEngine
import com.ludosovereign.ai.LudoBot
import com.ludosovereign.ai.MoveAnalysis
import com.ludosovereign.ai.MoveScorer
import com.ludosovereign.engine.DiceSource
import com.ludosovereign.engine.GameAction
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchLog
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.SixSchedule
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

data class SessionSettings(
    /** Play the only legal move automatically. */
    val autoMove: Boolean = true,
    /** Allow undoing the last human move (Casual Pass & Play). */
    val undoEnabled: Boolean = false,
    /** Dice tumble length for Snakes & Ladders; Ludo uses [timings]. */
    val rollAnimationMs: Long = 380,
    /** Snakes & Ladders move pause; Ludo uses [timings]. */
    val moveAnimationMs: Long = 350,
    /** Rush mode halves the animation timings. */
    val fastAnimations: Boolean = false,
    /** User animation speed: 1 = normal, 2 = twice as fast. */
    val animationSpeed: Float = 1f,
    /**
     * Ludo six pacing ([SixSchedule]): no six in a player's turns 1–5, a guaranteed six in turns 6–8.
     * On in the app; off by default so engine-level tests can script raw dice.
     */
    val guaranteedSixes: Boolean = false,
) {
    val rollMs get() = if (fastAnimations) rollAnimationMs / 2 else rollAnimationMs
    val moveMs get() = if (fastAnimations) moveAnimationMs / 2 else moveAnimationMs
    val timings: AnimationTimings
        get() = AnimationTimings().scaled((if (fastAnimations) 0.6f else 1f) * AnimationTimings.factorFor(animationSpeed))
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
    /** Where the current turn is in the roll → select → animate cycle; the UI gates input on it. */
    val turnPhase: TurnPhase = TurnPhase.NEXT_TURN,
    /** Animation lengths the board must use so it finishes inside the session's wait. */
    val timings: AnimationTimings = AnimationTimings(),
    /** True while the match is paused from the in-game menu. */
    val paused: Boolean = false,
    /** Human players whose turns the Auto-Bot is currently playing (AFK / break mode). */
    val autoPlayers: Set<Int> = emptySet(),
    /** [MatchSession] clock time at which each active Auto-Bot hands control back (100 s after enabling). */
    val autoEndsAtMs: Map<Int, Long> = emptyMap(),
    /** Human players who already used their one Auto-Bot chance and have taken control back. */
    val autoSpent: Set<Int> = emptySet(),
    val stats: MatchStats,
    val result: MatchResult? = null,
    /** Player whose settled dice face is on show during the post-roll hold, or -1. */
    val revealingPlayer: Int = -1,
) {
    /**
     * Whose turn the board should show: the roller while their number is on show (even when the
     * engine already passed the turn because the roll had no move), otherwise [GameState.current].
     */
    val turnPlayer: Int get() = if (revealingPlayer >= 0) revealingPlayer else game.current
}

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
    private var settings: SessionSettings = SessionSettings(),
    private val aiDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCheckpoint: suspend (Checkpoint) -> Unit = {},
    private val onFinished: suspend (MatchResult) -> Unit = {},
    /** Casual-strength, capture-first bot that plays for a human on a break. */
    /**
     * Unbeatable-strength bot with boosted dice that plays for a human on a break. It is single-use
     * and switches itself off [AUTO_BOT_LIMIT_MS] after it was enabled.
     */
    private val autoBotFactory: () -> LudoBot = { UnbeatableEngine(boosted = true) },
    /** Replaces a banned six with a uniform 1..5 under [SessionSettings.guaranteedSixes]. */
    private val scheduleRandom: Random = Random.Default,
) {
    private sealed interface Intent {
        data object Roll : Intent
        data class Move(val tokenId: Int) : Intent
        data object Undo : Intent
        /** Something outside the turn changed (Auto-Bot toggled, match ended early): re-check who plays. */
        data object Wake : Intent
    }

    private var game = initial
    private var matchLog = initialLog
    private var stats = initialStats
    private val startedAt = clock()
    private var undoPoint: Triple<GameState, MatchLog, MatchStats>? = null
    private val intents = Channel<Intent>(Channel.UNLIMITED)
    private var seq = 0L
    private var job: Job? = null
    private val autoPlayers = LinkedHashSet<Int>()
    /** [clock] time at which each active Auto-Bot switches itself off. */
    private val autoEndsAt = HashMap<Int, Long>()
    private val autoBot: LudoBot by lazy { autoBotFactory() }
    private var endRequested = false

    private val _ui = MutableStateFlow(LudoUiState(game = initial, stats = initialStats, autoSpent = spentFrom(initialStats)))
    val ui: StateFlow<LudoUiState> = _ui.asStateFlow()

    val log: MatchLog get() = matchLog

    fun start(scope: CoroutineScope): Job = scope.launch { runLoop() }.also { job = it }
    fun stop() { job?.cancel() }

    /** Accepted only while the session is waiting for that exact input; taps at any other time are dropped. */
    fun roll() { if (acceptsHuman(TurnPhase.WAITING_FOR_ROLL)) intents.trySend(Intent.Roll) }
    fun move(tokenId: Int) { if (acceptsHuman(TurnPhase.SELECTING_TOKEN)) intents.trySend(Intent.Move(tokenId)) }
    fun undo() { if (_ui.value.awaitingHuman) intents.trySend(Intent.Undo) }
    fun setPaused(value: Boolean) { pausedFlow.value = value; _ui.update { it.copy(paused = value) } }

    /** Applies a new user animation speed immediately, including to a match already in progress. */
    fun setAnimationSpeed(speed: Float) {
        if (speed == settings.animationSpeed) return
        settings = settings.copy(animationSpeed = speed)
        _ui.update { it.copy(timings = t) }
    }

    /**
     * Turns the Auto-Bot on or off for one human player; takes effect on the current or next decision.
     * It is single-use: a player may switch it on once per match, and once they switch it off again
     * the toggle stays revoked until the match ends (also across save and resume).
     */
    fun toggleAuto(player: Int) {
        if (player !in game.config.players.indices || game.config.players[player].kind != PlayerKind.HUMAN) return
        if (player in autoPlayers) {
            autoPlayers.remove(player)
            autoEndsAt.remove(player)
        } else {
            if (stats.players[player].autoBotUsed) return
            autoPlayers.add(player)
            autoEndsAt[player] = clock() + AUTO_BOT_LIMIT_MS
            stats = stats.markAutoBotUsed(player)
        }
        publishAuto()
        intents.trySend(Intent.Wake)
    }

    private fun publishAuto() =
        _ui.update { it.copy(autoPlayers = autoPlayers.toSet(), autoSpent = spentFrom(stats) - autoPlayers, stats = stats,
            autoEndsAtMs = autoEndsAt.toMap()) }

    /** Hands control back to every player whose Auto-Bot has run for [AUTO_BOT_LIMIT_MS]. */
    private fun expireAutoBots() {
        val now = clock()
        val expired = autoEndsAt.filterValues { now >= it }.keys
        if (expired.isEmpty()) return
        autoPlayers.removeAll(expired); expired.forEach(autoEndsAt::remove)
        publishAuto()
    }

    /** True while [player] may still switch the Auto-Bot on. */
    fun canEnableAuto(player: Int): Boolean = player !in autoPlayers && !stats.players[player].autoBotUsed

    private fun spentFrom(s: MatchStats): Set<Int> = s.players.indices.filter { s.players[it].autoBotUsed }.toSet()

    /** Ends the match now with standings ranked by progress (offered once someone has finished). */
    fun endNow() {
        if (game.phase == Phase.GAME_OVER) return
        endRequested = true
        intents.trySend(Intent.Wake)
        setPaused(false)
    }

    private fun acceptsHuman(phase: TurnPhase): Boolean = _ui.value.let { it.awaitingHuman && it.turnPhase == phase && !it.paused }

    private val pausedFlow = MutableStateFlow(false)
    private suspend fun awaitUnpaused() { pausedFlow.first { !it } }
    private val t: AnimationTimings get() = settings.timings

    private fun isBot(player: Int) = game.config.players[player].kind == PlayerKind.BOT && bots.containsKey(player)
    private fun botFor(player: Int): LudoBot? = when {
        isBot(player) -> bots.getValue(player)
        player in autoPlayers -> autoBot
        else -> null
    }

    private fun setPhase(phase: TurnPhase, awaitingHuman: Boolean = false) =
        _ui.update { it.copy(turnPhase = phase, awaitingHuman = awaitingHuman, timings = t) }

    /** Drops taps that arrived while we were not waiting (double taps, taps during animations). */
    private fun drainIntents() { while (intents.tryReceive().isSuccess) Unit }

    private suspend fun runLoop() {
        publish(emptyList(), TurnPhase.NEXT_TURN)
        while (game.phase != Phase.GAME_OVER && !endRequested) {
            awaitUnpaused()
            if (endRequested) break
            expireAutoBots()
            val bot = botFor(game.current)
            if (bot != null) {
                if (_ui.value.awaitingHuman) setPhase(_ui.value.turnPhase)
                botStep(bot)
            } else humanStep()
        }
        if (game.phase != Phase.GAME_OVER) {
            val events = ArrayList<GameEvent>()
            game = LudoEngine.endNow(game, events)
            publish(events, TurnPhase.GAME_OVER)
            checkpoint()
        }
        finish()
    }

    /** Applies a move and holds the turn until the board's hop / capture / home animation has finished. */
    private suspend fun applyMove(tokenId: Int) {
        val snapshot = game
        MoveAnalysis.analyse(snapshot, tokenId)?.let { v ->
            stats = stats.recordDecision(snapshot.current, v.accuracy, v.missedCut)
        }
        val events = apply(GameAction.MoveToken(tokenId), TurnPhase.ANIMATING_TOKEN)
        delay(t.moveDuration(events))
        if (game.phase != Phase.GAME_OVER) setPhase(TurnPhase.NEXT_TURN)
    }

    /**
     * Tumbles the dice for [rollMs], applies the roll (shaped by [SixSchedule] when enabled), then
     * holds the settled face for [AnimationTimings.DICE_REVEAL_MS] (1.2 s, every player, every phase, every speed)
     * before any pawn may move.
     */
    private suspend fun rollDice(player: Int, value: suspend () -> Int) {
        _ui.update {
            it.copy(rollingPlayer = player, awaitingHuman = false, legalMoves = IntArray(0), turnDeadlineAtMs = null,
                turnPhase = TurnPhase.ROLLING_DICE, timings = t)
        }
        val raw = coroutineScope {
            val pick = async { value() }
            delay(t.rollMs)
            pick.await()
        }
        val v = if (settings.guaranteedSixes) {
            val out = SixSchedule.roll(stats.sixSchedule, player, game.config.playerCount, raw,
                game.rules.tripleSixForfeit) { scheduleRandom.nextInt(1, 6) }
            stats = stats.copy(sixSchedule = out.state)
            out.value
        } else raw
        val events = apply(GameAction.Roll(v), TurnPhase.NEXT_TURN)
        // Keep the roller's pod and number up for the whole hold, including a roll with no move.
        _ui.update { it.copy(revealingPlayer = player) }
        delay(maxOf(AnimationTimings.DICE_REVEAL_MS, t.settleMs))
        _ui.update { it.copy(revealingPlayer = -1) }
        if (game.phase != Phase.AWAITING_MOVE) delay(t.turnGapMs + if (events.any { it is GameEvent.TripleSixForfeit }) t.impactMs * 3 else 0)
    }

    private suspend fun botStep(bot: LudoBot) {
        val player = game.current
        when (game.phase) {
            Phase.AWAITING_ROLL -> {
                val snapshot = game
                // A visible beat before the bot picks up the dice.
                delay(t.botThinkMs)
                awaitUnpaused()
                // rollDice holds the settled face for a full second before the pawn moves.
                rollDice(player) { withContext(aiDispatcher) { bot.chooseDice(snapshot) } ?: dice.roll() }
            }
            Phase.AWAITING_MOVE -> {
                val snapshot = game
                val token = withContext(aiDispatcher) { bot.chooseMove(snapshot) }
                awaitUnpaused()
                applyMove(token)
            }
            Phase.GAME_OVER -> Unit
        }
    }

    private suspend fun humanStep() {
        val timerMs = game.rules.turnTimerSeconds * 1000L
        when (game.phase) {
            Phase.AWAITING_ROLL -> {
                val deadline = if (timerMs > 0) clock() + timerMs else null
                drainIntents()
                _ui.update {
                    it.copy(awaitingHuman = true, rollingPlayer = -1, legalMoves = IntArray(0),
                        canUndo = undoPoint != null, turnDeadlineAtMs = deadline, turnPhase = TurnPhase.WAITING_FOR_ROLL, timings = t)
                }
                when (awaitIntent(timerMs)) {
                    Intent.Undo -> { performUndo(); return }
                    Intent.Wake -> return
                    is Intent.Move -> return // not expecting a move; ignore
                    Intent.Roll, null -> {
                        awaitUnpaused()
                        undoPoint = null
                        _ui.update { it.copy(canUndo = false) }
                        rollDice(game.current) { dice.roll() }
                    }
                }
            }
            Phase.AWAITING_MOVE -> {
                val legal = LudoEngine.legalMoves(game)
                if (settings.autoMove && legal.size == 1) {
                    delay(t.autoMovePauseMs)
                    rememberUndo()
                    applyMove(legal[0])
                    return
                }
                val deadline = if (timerMs > 0) clock() + timerMs else null
                drainIntents()
                _ui.update {
                    it.copy(awaitingHuman = true, rollingPlayer = -1, legalMoves = legal, turnDeadlineAtMs = deadline,
                        turnPhase = TurnPhase.SELECTING_TOKEN, timings = t)
                }
                while (true) {
                    when (val intent = awaitIntent(timerMs)) {
                        null -> { // timer expired: play the best-looking move
                            awaitUnpaused()
                            rememberUndo()
                            applyMove(legal.maxBy { MoveScorer.score(game, it) })
                            return
                        }
                        is Intent.Move -> {
                            val id = resolveTap(intent.tokenId) ?: continue
                            rememberUndo()
                            applyMove(id)
                            return
                        }
                        Intent.Undo -> { if (undoPoint != null) { performUndo(); return } }
                        Intent.Wake -> return
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
        // Undo rewinds the move, never the spent Auto-Bot chance.
        val spent = stats
        game = point.first; matchLog = point.second
        // Rolls are never undone either, so the six-pacing ledger stays where it is.
        stats = MatchStats(point.third.players.mapIndexed { i, p -> p.copy(autoBotUsed = p.autoBotUsed || spent.players[i].autoBotUsed) },
            spent.sixSchedule)
        publish(emptyList(), TurnPhase.NEXT_TURN)
        checkpoint()
    }

    private suspend fun apply(action: GameAction, phase: TurnPhase): List<GameEvent> {
        val events = ArrayList<GameEvent>()
        val before = game
        game = withContext(aiDispatcher) { LudoEngine.reduce(before, action, events) }
        matchLog = matchLog.append(action)
        stats = stats.fold(before, events)
        publish(events, phase)
        checkpoint()
        return events
    }

    private fun publish(events: List<GameEvent>, phase: TurnPhase) {
        val g = game
        _ui.update {
            it.copy(
                game = g, lastEvents = events, eventSeq = ++seq,
                rollingPlayer = -1,
                awaitingHuman = false,
                turnPhase = if (g.phase == Phase.GAME_OVER) TurnPhase.GAME_OVER else phase,
                timings = t,
                legalMoves = if (g.phase == Phase.AWAITING_MOVE) LudoEngine.legalMoves(g) else IntArray(0),
                canUndo = undoPoint != null,
                stats = stats,
                autoSpent = spentFrom(stats) - autoPlayers,
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
        _ui.update { it.copy(result = result, awaitingHuman = false, turnDeadlineAtMs = null, turnPhase = TurnPhase.GAME_OVER) }
        onFinished(result)
    }

    companion object {
        /** The one-time Auto-Bot (Unbeatable moves + boosted dice) runs for at most 100 seconds. */
        const val AUTO_BOT_LIMIT_MS = 100_000L

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
