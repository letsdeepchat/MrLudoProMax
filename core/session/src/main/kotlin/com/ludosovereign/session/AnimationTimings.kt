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
    /** One tile-to-tile hop at the default (×1.0) speed: a natural classic Ludo pace. */
    val hopMs: Long = BASE_HOP_MS,
    /** Curved arc from the yard to the start square. */
    val exitMs: Long = 300,
    /** Impact flash/shake on the capture tile. */
    val impactMs: Long = 140,
    /** Captured token flying back to its yard. */
    val retreatMs: Long = 420,
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

    companion object {
        const val BASE_HOP_MS = 235L
        const val SLOWEST_HOP_MS = 425f
        const val FASTEST_HOP_MS = 90f
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f

        /**
         * Per-tile hop length for a user speed in [MIN_SPEED]..[MAX_SPEED]: 425 ms at the slowest,
         * 235 ms at ×1.0 and 90 ms at the fastest, linear in between on each side.
         */
        fun hopMsFor(speed: Float): Float {
            val v = speed.coerceIn(MIN_SPEED, MAX_SPEED)
            return if (v <= 1f) SLOWEST_HOP_MS + (BASE_HOP_MS - SLOWEST_HOP_MS) * (v - MIN_SPEED) / (1f - MIN_SPEED)
            else BASE_HOP_MS + (FASTEST_HOP_MS - BASE_HOP_MS) * (v - 1f) / (MAX_SPEED - 1f)
        }

        /** Multiplier applied to every timing (hops, exit, capture, dice, turn gaps) for a user speed. */
        fun factorFor(speed: Float): Float = hopMsFor(speed) / BASE_HOP_MS

        /** Slider position 0..1 (middle = ×1.0) to speed, and back. */
        fun speedForSlider(p: Float): Float = p.coerceIn(0f, 1f).let { if (it <= 0.5f) MIN_SPEED + it else 1f + (it - 0.5f) * 2f }
        fun sliderForSpeed(v: Float): Float = v.coerceIn(MIN_SPEED, MAX_SPEED).let { if (it <= 1f) it - MIN_SPEED else 0.5f + (it - 1f) / 2f }
    }
}
