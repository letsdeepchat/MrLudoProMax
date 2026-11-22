package com.ludosovereign.ai

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine

/**
 * One-ply tactical score of a move, used for alpha-beta move ordering and by the Casual bot.
 * Mirrors the spec's weights: lethal capture (+1200 + victimProgress × 18), Home (+1500),
 * home-stretch entry (+600), Fortress (+450), deployment (+320), threat evasion (+700 × risk).
 */
object MoveScorer {
    private val w = EvalWeights()

    fun score(s: GameState, tokenId: Int): Int {
        if (tokenId < 0) return -10_000
        val topo = s.topology
        val owner = s.ownerOf(tokenId)
        val seat = s.seatOf(owner)
        val team = s.teamOf(owner)
        val from = s.tokens[tokenId]
        val to = LudoEngine.targetProgress(s, tokenId, s.dice) ?: return -10_000
        var score = (to - from.coerceAtLeast(0)) * 2

        if (from == BASE) score += w.deployed
        if (to == topo.homeProgress) score += w.home
        else if (!topo.isInHomeStretch(from) && topo.isInHomeStretch(to)) score += w.homeStretch

        val cells = IntArray(s.tokens.size) { s.cellOf(it) }
        val fromCell = cells[tokenId]
        val toCell = topo.absoluteCell(seat, to)

        // Risk currently faced at the origin (escaping it is good) ...
        if (fromCell >= 0 && !topo.isSafeCell(fromCell)) {
            val friendsHere = cells.indices.count { it != tokenId && cells[it] == fromCell && s.teamOf(s.ownerOf(it)) == team }
            if (friendsHere == 0) score += (w.exposure * ThreatMap.hitProbability(s, fromCell, team, cells)).toInt()
            else if (friendsHere == 1 && s.rules.fortressEnabled) score -= w.fortress / 2 // breaking a Fortress
        }
        if (toCell >= 0) {
            cells[tokenId] = toCell
            val safe = topo.isSafeCell(toCell)
            var friends = 0
            for (o in cells.indices) {
                if (o == tokenId || cells[o] != toCell) continue
                val oTeam = s.teamOf(s.ownerOf(o))
                if (oTeam == team) friends++
            }
            if (!safe) {
                // Enemy singletons on the target get captured.
                val enemyCounts = HashMap<Int, Int>()
                for (o in cells.indices) {
                    if (o == tokenId || cells[o] != toCell) continue
                    val oTeam = s.teamOf(s.ownerOf(o))
                    if (oTeam != team) enemyCounts[oTeam] = (enemyCounts[oTeam] ?: 0) + 1
                }
                for (o in cells.indices) {
                    if (o == tokenId || cells[o] != toCell) continue
                    val oTeam = s.teamOf(s.ownerOf(o))
                    if (oTeam != team && (!s.rules.fortressEnabled || enemyCounts[oTeam] == 1)) {
                        score += w.lethalCapture + s.tokens[o] * w.progressPerCell
                    }
                }
            }
            if (friends >= 1 && s.rules.fortressEnabled) score += if (safe) w.fortressOnSafe else w.fortress
            else if (safe) score += w.safeTile * 2
            else score -= (w.exposure * ThreatMap.hitProbability(s, toCell, team, cells)).toInt()
        }
        return score
    }
}
