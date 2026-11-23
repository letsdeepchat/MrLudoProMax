package com.ludosovereign.ai

import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase

/**
 * Post-match analysis of a single decision: how close the chosen move was to the engine's favourite
 * (by the local [MoveScorer] heuristic, which weighs captures, entering Home, reaching safe stars,
 * escaping threats, advancing and forming Fortresses) and whether a capture was passed up.
 */
object MoveAnalysis {
    data class Verdict(val accuracy: Double, val missedCut: Boolean)

    /** Moves scoring within this many points of the best one count as best (near-equal choices). */
    private const val TOLERANCE = 30

    /** Null when the turn was not a real decision (fewer than two distinct legal moves). */
    fun analyse(state: GameState, chosen: Int): Verdict? {
        if (state.phase != Phase.AWAITING_MOVE) return null
        val legal = LudoEngine.legalMoves(state)
        if (legal.size < 2) return null
        val scores = legal.map { MoveScorer.score(state, it) }
        val best = scores.max()
        val worst = scores.min()
        val mine = MoveScorer.score(state, chosen)
        val accuracy = when {
            best - mine <= TOLERANCE -> 1.0
            best == worst -> 1.0
            else -> ((mine - worst).toDouble() / (best - worst)).coerceIn(0.0, 1.0)
        }
        val chosenCaptures = captures(state, chosen)
        val missed = !chosenCaptures && legal.any { it != chosen && captures(state, it) }
        return Verdict(accuracy, missed)
    }

    fun captures(state: GameState, tokenId: Int): Boolean {
        if (!LudoEngine.isLegal(state, tokenId)) return false
        val events = ArrayList<GameEvent>(8)
        LudoEngine.move(state, tokenId, events)
        return events.any { it is GameEvent.Captured }
    }
}
