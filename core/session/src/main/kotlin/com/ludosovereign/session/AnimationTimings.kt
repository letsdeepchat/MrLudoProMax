package com.ludosovereign.session

import com.ludosovereign.engine.GameEvent

/**
 * Per-turn finite state machine shown to the UI. The session is the only writer, so the board can
 * gate input on it: the dice only accepts a tap in [WAITING_FOR_ROLL], tokens only in
 * [SELECTING_TOKEN], and nothing is accepted while the dice tumbles or a token hops.
 */
enum class TurnPhase { WAITING_FOR_ROLL, ROLLING_DICE, SELECTING_TOKEN, ANIMATING_TOKEN, NEXT_TURN, GAME_OVER }

/**
 * One source of truth for animation lengths. The session waits exactly [moveDuration] after a move
 * before the next actor may act, and the board animator plays its hops, capture retreat and home
 * burst inside the same budget, so a bot can never start its turn while a token is still in the air.
 */
data class AnimationTimings(
    /** Dice tumble (350–420 ms). Bot search runs concurrently and is hidden behind it. */
    val rollMs: Long = 380,
    /** Pause on the settled face (highlight pulse) before selecting / auto-moving. */
    val settleMs: Long = 140,
    /** Extra beat before auto-playing the only legal move, so the player sees the number. */
    val autoMovePauseMs: Long = 180,
    /** One tile-to-tile hop. */
    val hopMs: Long = 125,
    /** Curved arc from the yard to the start square. */
    val exitMs: Long = 240,
    /** Impact flash/shake on the capture tile. */
    val impactMs: Long = 140,
    /** Captured token flying back to its yard. */
    val retreatMs: Long = 350,
    /** Starburst when a token reaches Home. */
    val homeBurstMs: Long = 320,
    /** Gap before the next turn's indicator moves. */
    val turnGapMs: Long = 120,
) {
    fun scaled(k: Float): AnimationTimings = if (k == 1f) this else AnimationTimings(
        s(rollMs, k), s(settleMs, k), s(autoMovePauseMs, k), s(hopMs, k), s(exitMs, k), s(impactMs, k),
        s(retreatMs, k), s(homeBurstMs, k), s(turnGapMs, k),
    )

    /** Number of hops the mover makes for this event batch (0 if no token moved along the track). */
    fun hops(events: List<GameEvent>): Int =
        events.sumOf { if (it is GameEvent.TokenMoved) (it.toProgress - it.fromProgress).coerceAtLeast(0) else 0 }

    /** How long the board needs to animate one move's events: exit or hops, then capture, then home burst. */
    fun moveDuration(events: List<GameEvent>): Long {
        var t = 0L
        if (events.any { it is GameEvent.TokenReleased }) t += exitMs
        t += hopMs * hops(events)
        if (events.any { it is GameEvent.Captured }) t += impactMs + retreatMs
        if (events.any { it is GameEvent.TokenReachedHome }) t += homeBurstMs
        return t + turnGapMs
    }

    private fun s(v: Long, k: Float) = (v * k).toLong().coerceAtLeast(1)
}
