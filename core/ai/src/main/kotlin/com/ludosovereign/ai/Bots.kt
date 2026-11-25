package com.ludosovereign.ai

import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * A computer opponent. The session layer calls [chooseDice] before every roll by this bot: `null`
 * means "use the fair dice", a value means the bot (Unbeatable only) picked its own outcome. After a
 * roll that leaves several legal moves, [chooseMove] picks one. Both are pure CPU work meant for
 * `Dispatchers.Default`; neither touches Android.
 */
interface LudoBot {
    val tier: BotTier
    fun chooseDice(state: GameState): Int? = null
    fun chooseMove(state: GameState): Int
    /** Stats of the last search, for the debug overlay and benchmarks. */
    val lastResult: SearchResult? get() = null
}

object BotFactory {
    /** Default per-decision budget. The dice animation is ~450 ms; we keep well inside it. */
    const val BUDGET_MS = 80L

    fun create(tier: BotTier, random: Random = SecureRandom().asKotlinRandom(), budgetMs: Long = BUDGET_MS): LudoBot =
        when (tier) {
            BotTier.CASUAL -> CasualBot(random)
            BotTier.GRANDMASTER -> GrandmasterBot(budgetMs = budgetMs)
            BotTier.UNBEATABLE -> UnbeatableEngine(budgetMs = budgetMs, random = random)
        }
}

/** Fair dice + one-ply heuristic with deliberate noise, so newcomers can win. */
class CasualBot(private val random: Random, private val blunderRate: Double = 0.3) : LudoBot {
    override val tier = BotTier.CASUAL
    override fun chooseMove(state: GameState): Int {
        val moves = LudoEngine.legalMoves(state)
        if (moves.size == 1 || random.nextDouble() < blunderRate) return moves[random.nextInt(moves.size)]
        return moves.maxBy { MoveScorer.score(state, it) + random.nextInt(0, 200) }
    }
}

/** Fair dice + depth-4 Expectimax with alpha-beta / Star1 pruning under the time budget. */
class GrandmasterBot(
    private val maxDepth: Int = 4,
    private val budgetMs: Long = BotFactory.BUDGET_MS,
    evaluator: HeuristicEvaluator = HeuristicEvaluator(),
) : LudoBot {
    override val tier = BotTier.GRANDMASTER
    private val search = ExpectimaxSearch(evaluator)
    override var lastResult: SearchResult? = null; private set

    override fun chooseMove(state: GameState): Int {
        val moves = LudoEngine.legalMoves(state)
        if (moves.size == 1) return moves[0]
        val r = search.bestMove(state, maxDepth, budgetMs)
        lastResult = r
        return r.tokenId
    }
}

/**
 * UNBEATABLE ENGINE. Its dice are fair most of the time: it rolls like everyone else and plays the
 * result with the Grandmaster's depth-4 Expectimax. Its edge is a *strike* calculation: before the die
 * settles it checks whether some face would let one of its pawns land exactly on a human pawn that
 * can be cut, and if so it takes that face, aiming at the human pawn that is furthest along. It never
 * rolls more than [MAX_CONSECUTIVE_SIXES] sixes in one turn: a third roll is always forced to 1..5.
 * This is still not a fair-dice opponent and the setup UI says so.
 */
class UnbeatableEngine(
    private val maxDepth: Int = 4,
    private val budgetMs: Long = BotFactory.BUDGET_MS,
    evaluator: HeuristicEvaluator = HeuristicEvaluator(),
    private val random: Random = SecureRandom().asKotlinRandom(),
) : LudoBot {
    override val tier = BotTier.UNBEATABLE
    private val search = ExpectimaxSearch(evaluator)
    override var lastResult: SearchResult? = null; private set

    override fun chooseDice(state: GameState): Int {
        require(state.phase == Phase.AWAITING_ROLL)
        val strike = StrikeFinder.bestStrike(state, humansOnly = true)
            ?: StrikeFinder.bestStrike(state, humansOnly = false).takeIf { !state.config.players.any { it.kind == PlayerKind.HUMAN } }
        val value = strike?.dice ?: random.nextInt(1, 7)
        return capSixes(state, value, random)
    }

    override fun chooseMove(state: GameState): Int {
        val moves = LudoEngine.legalMoves(state)
        if (moves.size == 1) return moves[0]
        // A strike roll is followed through: play the cutting move it was rolled for.
        StrikeFinder.capturingMoves(state, humansOnly = false).maxByOrNull { it.victimProgress }?.let { return it.tokenId }
        val r = search.bestMove(state, maxDepth, budgetMs)
        lastResult = r
        return r.tokenId
    }

    companion object {
        const val MAX_CONSECUTIVE_SIXES = 2

        /** Forces a non-six once [MAX_CONSECUTIVE_SIXES] sixes have already been rolled this turn. */
        fun capSixes(state: GameState, value: Int, random: Random): Int =
            if (value == 6 && state.consecutiveSixes >= MAX_CONSECUTIVE_SIXES) random.nextInt(1, 6) else value
    }
}

/**
 * Casual-strength Auto-Bot for a human on a break: the Casual bot's noisy one-ply play, except that
 * a capture is never passed up. When several moves cut, it takes the victim that is furthest along.
 */
class AutoPilotBot(private val random: Random = SecureRandom().asKotlinRandom()) : LudoBot {
    override val tier = BotTier.CASUAL
    private val casual = CasualBot(random)
    override fun chooseMove(state: GameState): Int {
        val cuts = StrikeFinder.capturingMoves(state, humansOnly = false)
        if (cuts.isNotEmpty()) return cuts.maxBy { it.victimProgress }.tokenId
        return casual.chooseMove(state)
    }
}

/** Finds moves (and, for the Unbeatable dice, faces) that cut an opponent's pawn. */
object StrikeFinder {
    data class Cut(val tokenId: Int, val dice: Int, val victimProgress: Int)

    /** Legal moves with the pending dice that capture something; [humansOnly] keeps human victims only. */
    fun capturingMoves(state: GameState, humansOnly: Boolean): List<Cut> {
        if (state.phase != Phase.AWAITING_MOVE) return emptyList()
        val out = ArrayList<Cut>()
        for (id in LudoEngine.legalMoves(state)) {
            val events = ArrayList<GameEvent>()
            LudoEngine.move(state, id, events)
            val victims = events.filterIsInstance<GameEvent.Captured>()
                .filter { !humansOnly || state.config.players[state.ownerOf(it.victimTokenId)].kind == PlayerKind.HUMAN }
            if (victims.isNotEmpty()) out += Cut(id, state.dice, victims.maxOf { it.victimProgress })
        }
        return out
    }

    /**
     * The face that lets the roller cut the most advanced reachable pawn this roll, or null when no
     * pawn is within striking distance. A six is skipped when it would be the third of the turn.
     */
    fun bestStrike(state: GameState, humansOnly: Boolean): Cut? {
        if (state.phase != Phase.AWAITING_ROLL) return null
        var best: Cut? = null
        for (d in 1..6) {
            if (d == 6 && state.consecutiveSixes >= UnbeatableEngine.MAX_CONSECUTIVE_SIXES) continue
            val rolled = LudoEngine.roll(state, d)
            if (rolled.phase != Phase.AWAITING_MOVE || rolled.current != state.current) continue
            val cut = capturingMoves(rolled, humansOnly).maxByOrNull { it.victimProgress } ?: continue
            if (best == null || cut.victimProgress > best.victimProgress) best = cut
        }
        return best
    }
}
