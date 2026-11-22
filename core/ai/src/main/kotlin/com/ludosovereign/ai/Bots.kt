package com.ludosovereign.ai

import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase
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
            BotTier.UNBEATABLE -> UnbeatableEngine(budgetMs = budgetMs)
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
 * UNBEATABLE ENGINE: an adaptive local dice oracle. Before its die settles it enumerates every
 * `(dice, move)` pair and searches each with depth-3/4 Expectimax, modelling its *own* future rolls
 * as oracle (max) nodes and everyone else's as fair chance nodes. This is intentionally not a
 * fair-dice opponent and the setup UI must say so.
 */
class UnbeatableEngine(
    private val maxDepth: Int = 4,
    private val budgetMs: Long = BotFactory.BUDGET_MS,
    evaluator: HeuristicEvaluator = HeuristicEvaluator(),
) : LudoBot {
    override val tier = BotTier.UNBEATABLE
    private var pendingMove = -1
    private var pendingFor: GameState? = null

    private val search = ExpectimaxSearch(evaluator, diceNodeFor = { s, player ->
        val spec = s.config.players[player]
        if (spec.botTier == BotTier.UNBEATABLE) DiceNode.ORACLE else DiceNode.CHANCE
    })
    override var lastResult: SearchResult? = null; private set

    override fun chooseDice(state: GameState): Int {
        require(state.phase == Phase.AWAITING_ROLL)
        val r = search.bestRollAndMove(state, maxDepth, budgetMs)
        lastResult = r
        pendingMove = r.tokenId
        pendingFor = LudoEngine.roll(state, r.dice)
        return r.dice
    }

    override fun chooseMove(state: GameState): Int {
        val moves = LudoEngine.legalMoves(state)
        if (moves.size == 1) return moves[0]
        // Reuse the move planned together with the dice when the state is the one we planned for.
        if (pendingMove >= 0 && state == pendingFor && pendingMove in moves) return pendingMove
        val r = search.bestMove(state, maxDepth, budgetMs)
        lastResult = r
        return r.tokenId
    }
}
