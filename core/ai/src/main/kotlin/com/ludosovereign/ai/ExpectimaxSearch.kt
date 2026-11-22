package com.ludosovereign.ai

import com.ludosovereign.ai.HeuristicEvaluator.Companion.WIN
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase

/** How a player's dice behave inside the search tree. */
enum class DiceNode {
    /** Fair die: average over the six outcomes (Expectimax chance node). */
    CHANCE,
    /** The player picks its own outcome (Unbeatable oracle). */
    ORACLE,
}

data class SearchResult(
    /** Chosen dice value (oracle roots only; otherwise the pending dice). */
    val dice: Int,
    /** Token id to move, or -1 when the chosen roll leaves no move (e.g. forfeit or no legal move). */
    val tokenId: Int,
    val score: Int,
    val depthReached: Int,
    val nodes: Long,
    val ttHits: Long,
    val elapsedMicros: Long,
)

/**
 * Paranoid Expectimax with alpha-beta at decision nodes, Star1 pruning at chance nodes, a Zobrist
 * transposition table, captures-first move ordering, and iterative deepening under a hard time budget.
 *
 * Depth counts dice rolls: each chance/oracle layer consumes one ply of depth. Bonus turns (six,
 * capture, home) are therefore chained naturally: the same player simply rolls again in the tree.
 *
 * Not thread-safe; give each bot its own instance.
 */
class ExpectimaxSearch(
    private val evaluator: HeuristicEvaluator = HeuristicEvaluator(),
    private val diceNodeFor: (GameState, Int) -> DiceNode = { _, _ -> DiceNode.CHANCE },
    ttBits: Int = 17,
) {
    private val zobrist = ZobristHash()
    private val tt = TranspositionTable(ttBits)
    private var rootTeam = 0
    private var nodes = 0L
    private var deadline = 0L
    private val abort = AbortSearch()
    private val perspectiveKeys = LongArray(6) { kotlin.random.Random(it + 99L).nextLong() }

    /** Picks the best move for a state in [Phase.AWAITING_MOVE]. */
    fun bestMove(s: GameState, maxDepth: Int, budgetMillis: Long): SearchResult {
        require(s.phase == Phase.AWAITING_MOVE)
        val choices = LudoEngine.legalMoves(s).map { Choice(s.dice, it, LudoEngine.move(s, it)) }
        return iterate(s, choices, maxDepth, budgetMillis, consumesRoll = false)
    }

    /**
     * Dice oracle: for a state in [Phase.AWAITING_ROLL], evaluates every `(dice, move)` pair and
     * returns the best. The triple-six forfeit is not special-cased: rolling the third six simply
     * produces the forfeited position, which scores badly unless forfeiting is genuinely neutral.
     */
    fun bestRollAndMove(s: GameState, maxDepth: Int, budgetMillis: Long): SearchResult {
        require(s.phase == Phase.AWAITING_ROLL)
        val choices = ArrayList<Choice>()
        for (d in 1..6) {
            val rolled = LudoEngine.roll(s, d)
            if (rolled.phase == Phase.AWAITING_MOVE) {
                for (m in LudoEngine.legalMoves(rolled)) choices += Choice(d, m, LudoEngine.move(rolled, m))
            } else choices += Choice(d, -1, rolled)
        }
        return iterate(s, choices, maxDepth, budgetMillis, consumesRoll = true)
    }

    private class Choice(val dice: Int, val tokenId: Int, val child: GameState) {
        var score = 0
    }

    private fun iterate(
        root: GameState, choices: List<Choice>, maxDepth: Int, budgetMillis: Long, consumesRoll: Boolean,
    ): SearchResult {
        val start = System.nanoTime()
        deadline = start + budgetMillis * 1_000_000
        rootTeam = root.teamOf(root.current)
        nodes = 0
        val hits0 = tt.hits
        // Seed ordering with the cheap tactical score so depth 1 already looks at captures first.
        var ordered = choices.sortedByDescending { MoveScorer.score(root.copy(dice = it.dice, phase = Phase.AWAITING_MOVE), it.tokenId) }
        var best = ordered.first()
        var bestScore = Int.MIN_VALUE
        var reached = 0
        if (ordered.size > 1) {
            for (depth in 1..maxDepth) {
                val childDepth = if (consumesRoll) depth - 1 else depth
                try {
                    var alpha = -WIN - 1
                    var iterBest = ordered.first()
                    for (c in ordered) {
                        c.score = search(c.child, childDepth, 1, alpha, WIN + 1)
                        if (c.score > alpha) { alpha = c.score; iterBest = c }
                    }
                    best = iterBest; bestScore = alpha; reached = depth
                    ordered = ordered.sortedByDescending { it.score }
                    if (alpha >= WIN - 100) break // forced win found
                } catch (_: AbortSearch) {
                    break
                }
            }
            if (reached == 0) { // budget too small even for depth 1: fall back to static eval
                best = ordered.maxBy { evaluator.evaluate(it.child, rootTeam) }
                bestScore = evaluator.evaluate(best.child, rootTeam)
            }
        } else {
            bestScore = evaluator.evaluate(best.child, rootTeam)
        }
        val elapsed = (System.nanoTime() - start) / 1000
        return SearchResult(best.dice, best.tokenId, bestScore, reached, nodes, tt.hits - hits0, elapsed)
    }

    private fun checkTime() {
        if ((++nodes and 255L) == 0L && System.nanoTime() > deadline) throw abort
    }

    private fun search(s: GameState, depth: Int, ply: Int, alphaIn: Int, betaIn: Int): Int {
        checkTime()
        if (s.phase == Phase.GAME_OVER) return if (s.winnerTeam == rootTeam) WIN - ply else -WIN + ply
        if (depth <= 0 && s.phase == Phase.AWAITING_ROLL) return evaluator.evaluate(s, rootTeam)

        val key = zobrist.hash(s) xor perspectiveKeys[rootTeam]
        var alpha = alphaIn
        var beta = betaIn
        val hit = tt.probe(key, depth)
        if (hit >= 0) {
            val v = tt.value(hit)
            when (tt.flag(hit)) {
                TranspositionTable.EXACT -> return v
                TranspositionTable.LOWER -> if (v > alpha) alpha = v
                TranspositionTable.UPPER -> if (v < beta) beta = v
            }
            if (alpha >= beta) return v
        }

        val value: Int
        var bestMove = -1
        if (s.phase == Phase.AWAITING_ROLL) {
            value = if (diceNodeFor(s, s.current) == DiceNode.ORACLE) oracleNode(s, depth, ply, alpha, beta)
            else chanceNode(s, depth, ply, alpha, beta)
        } else {
            val maximizing = s.teamOf(s.current) == rootTeam
            val moves = orderedMoves(s, tt.bestMove(key))
            var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
            var a = alpha
            var b = beta
            for (m in moves) {
                val v = search(LudoEngine.move(s, m), depth, ply + 1, a, b)
                if (maximizing) {
                    if (v > best) { best = v; bestMove = m }
                    if (best > a) a = best
                } else {
                    if (v < best) { best = v; bestMove = m }
                    if (best < b) b = best
                }
                if (a >= b) break
            }
            value = best
        }

        val flag = when {
            value <= alphaIn -> TranspositionTable.UPPER
            value >= betaIn -> TranspositionTable.LOWER
            else -> TranspositionTable.EXACT
        }
        tt.store(key, depth, value, flag, bestMove)
        return value
    }

    /** Star1-pruned expectation over six equiprobable outcomes. */
    private fun chanceNode(s: GameState, depth: Int, ply: Int, alpha: Int, beta: Int): Int {
        val lo = -WIN.toLong()
        val hi = WIN.toLong()
        var sum = 0L
        for (d in 1..6) {
            val remaining = (6 - d).toLong()
            val a = maxOf(lo, 6L * alpha - sum - remaining * hi)
            val b = minOf(hi, 6L * beta - sum - remaining * lo)
            val v = search(LudoEngine.roll(s, d), depth - 1, ply + 1, a.toInt(), b.toInt())
            sum += v
            if (sum + remaining * lo >= 6L * beta) return ((sum + remaining * lo) / 6).toInt()
            if (sum + remaining * hi <= 6L * alpha) return ((sum + remaining * hi) / 6).toInt()
        }
        return (sum / 6).toInt()
    }

    /** Oracle dice: the roller chooses, so this is a max (friendly) or min (hostile) node. */
    private fun oracleNode(s: GameState, depth: Int, ply: Int, alphaIn: Int, betaIn: Int): Int {
        val maximizing = s.teamOf(s.current) == rootTeam
        var a = alphaIn
        var b = betaIn
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (d in intArrayOf(6, 5, 4, 3, 2, 1)) {
            val v = search(LudoEngine.roll(s, d), depth - 1, ply + 1, a, b)
            if (maximizing) { if (v > best) best = v; if (best > a) a = best }
            else { if (v < best) best = v; if (best < b) b = best }
            if (a >= b) break
        }
        return best
    }

    private fun orderedMoves(s: GameState, ttMove: Int): IntArray {
        val moves = LudoEngine.legalMoves(s)
        if (moves.size <= 1) return moves
        val scores = IntArray(moves.size) { i ->
            if (moves[i] == ttMove) Int.MAX_VALUE else MoveScorer.score(s, moves[i])
        }
        // Insertion sort, descending (tiny arrays).
        for (i in 1 until moves.size) {
            val m = moves[i]; val sc = scores[i]
            var j = i - 1
            while (j >= 0 && scores[j] < sc) { moves[j + 1] = moves[j]; scores[j + 1] = scores[j]; j-- }
            moves[j + 1] = m; scores[j + 1] = sc
        }
        return moves
    }

    private class AbortSearch : RuntimeException(null, null, false, false)
}
