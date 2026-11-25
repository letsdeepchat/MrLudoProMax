package com.ludosovereign.feature.board

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameEvent

/** The one pawn a move batch animates: where it starts and where it ends, in track progress. */
data class MovePlan(val tokenId: Int, val from: Int, val to: Int)

/**
 * Resolves the hop of a move batch from the engine's own [GameEvent.TokenMoved] / [GameEvent.TokenReleased]
 * facts, never from the board's last-drawn snapshot. A snapshot goes stale whenever an animation is
 * cut short (a bonus roll after a six arrives while the pawn is still landing); building the second
 * six's path from it made pawns hop back to the old origin and replay, or stall. With the event's
 * `fromProgress` every stacked six simply adds its six tiles: 0 → 6 → 12 → 18.
 */
fun planMove(events: List<GameEvent>): MovePlan? {
    for (e in events) when (e) {
        is GameEvent.TokenMoved -> return MovePlan(e.tokenId, e.fromProgress, e.toProgress)
        is GameEvent.TokenReleased -> return MovePlan(e.tokenId, BASE, 0)
        else -> Unit
    }
    return null
}

/**
 * Scale of the ✓ badge for a pawn in its yard slot (0 = hidden, 1 = full).
 * - [popping]: the pop-in animation's current value while it runs.
 * - [arrivingNow]: the pawn reached Home in the batch on screen and its hop has not started yet, so
 *   the badge waits for the landing instead of appearing before the pawn moves.
 * Every other finished pawn shows its full ✓, so a player's last pawn landing shows all four at once
 * even if a following state change cancels the pop animation.
 */
fun tickScale(finished: Boolean, popping: Float?, arrivingNow: Boolean): Float = when {
    !finished -> 0f
    popping != null -> popping
    arrivingNow -> 0f
    else -> 1f
}

/** Pawns that reached Home in [events]. */
fun homeArrivals(events: List<GameEvent>): Set<Int> =
    events.mapNotNullTo(HashSet()) { (it as? GameEvent.TokenReachedHome)?.tokenId }
