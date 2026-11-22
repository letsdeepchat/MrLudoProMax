package com.ludosovereign.ai

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.Phase

/**
 * Weights for the tactical evaluation E(s). Defaults follow the design spec; the progress weight
 * (18/cell) is what makes a capture worth `deploy + victimProgress × 18` in the static score, and the
 * search adds the bonus-roll follow-ups on top.
 */
data class EvalWeights(
    val progressPerCell: Int = 18,
    val deployed: Int = 320,
    /** A captured token loses its deployment and must wait for a release roll again. */
    val capturedTempo: Int = 880,
    val homeStretch: Int = 600,
    val home: Int = 1500,
    val fortress: Int = 450,
    val fortressOnSafe: Int = 90,
    val exposure: Int = 700,
    val safeTile: Int = 60,
    /** Captures are ordered first: lethal-capture weight from the spec (+1200 + progress × 18). */
    val lethalCapture: Int = 1200,
)

/**
 * Static evaluation from one team's point of view: `team score − strongest opposing team score`.
 * Using the strongest opponent (not the sum) is what drives "opponent starvation": the engine spends
 * captures on whoever is closest to winning.
 */
class HeuristicEvaluator(val w: EvalWeights = EvalWeights()) {

    fun evaluate(s: GameState, team: Int): Int {
        if (s.phase == Phase.GAME_OVER) return if (s.winnerTeam == team) WIN else -WIN
        val teams = s.config.teams
        val teamScores = IntArray(s.config.playerCount)
        val cells = IntArray(s.tokens.size)
        for (id in s.tokens.indices) cells[id] = s.cellOf(id)
        for (id in s.tokens.indices) {
            val owner = s.ownerOf(id)
            teamScores[teams[owner]] += tokenValue(s, id, cells)
        }
        val mine = teamScores[team]
        var bestOpp = Int.MIN_VALUE
        for (t in teamScores.indices) if (t != team && teamPresent(s, t)) bestOpp = maxOf(bestOpp, teamScores[t])
        return mine - bestOpp
    }

    private fun teamPresent(s: GameState, team: Int): Boolean = s.config.teams.any { it == team }

    /** Value of a single token, including fortress/exposure context. */
    fun tokenValue(s: GameState, id: Int, cells: IntArray): Int {
        val topo = s.topology
        val p = s.tokens[id]
        if (p == BASE) return -w.capturedTempo
        if (p == topo.homeProgress) return w.deployed + topo.homeProgress * w.progressPerCell + w.homeStretch + w.home
        var v = w.deployed + p * w.progressPerCell
        if (topo.isInHomeStretch(p)) return v + w.homeStretch
        val cell = cells[id]
        val owner = s.ownerOf(id)
        val team = s.config.teams[owner]
        val safe = topo.isSafeCell(cell)
        var friends = 0
        var lowestFriend = id
        for (o in cells.indices) {
            if (o != id && cells[o] == cell && s.config.teams[s.ownerOf(o)] == team) {
                friends++
                if (o < lowestFriend) lowestFriend = o
            }
        }
        if (friends > 0 && s.rules.fortressEnabled) {
            // Credit the fortress bonus once per stack (to its lowest token id).
            if (lowestFriend == id) v += if (safe) w.fortressOnSafe else w.fortress
            return v
        }
        if (safe) return v + w.safeTile
        val hit = ThreatMap.hitProbability(s, cell, team, cells)
        if (hit > 0.0) {
            val stake = 1.0 + p.toDouble() / topo.homeProgress
            v -= (w.exposure * hit * stake).toInt()
        }
        return v
    }

    companion object {
        const val WIN = 1_000_000
    }
}

/** Backward danger zones: who can land on a cell with a single roll. */
object ThreatMap {
    /**
     * Probability that some enemy of [team] lands exactly on [cell] with their next roll, treating each
     * enemy player's roll as independent. Only hunters still on the main track count (a hunter about to
     * turn into its home stretch cannot reach cells beyond its own last track cell).
     */
    fun hitProbability(s: GameState, cell: Int, team: Int, cells: IntArray): Double {
        val topo = s.topology
        val L = topo.trackLength
        val n = s.config.playerCount
        var miss = 1.0
        for (enemy in 0 until n) {
            if (s.config.teams[enemy] == team) continue
            var mask = 0
            val base = enemy * s.tokensPerPlayer
            for (id in base until base + s.tokensPerPlayer) {
                val c = cells[id]
                if (c < 0) continue
                val d = (cell - c + L) % L
                if (d in 1..6 && s.tokens[id] + d <= topo.lastTrackProgress) mask = mask or (1 shl d)
            }
            if (mask != 0) miss *= 1.0 - Integer.bitCount(mask) / 6.0
        }
        return 1.0 - miss
    }

    /** Enemy tokens that could land on [cell] with dice value [d]. */
    fun huntersAt(s: GameState, cell: Int, team: Int, d: Int): List<Int> =
        s.tokens.indices.filter { id ->
            val owner = s.ownerOf(id)
            val c = s.cellOf(id)
            s.config.teams[owner] != team && c >= 0 &&
                (cell - c + s.topology.trackLength) % s.topology.trackLength == d &&
                s.tokens[id] + d <= s.topology.lastTrackProgress
        }
}
