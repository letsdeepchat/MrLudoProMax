package com.ludosovereign.engine

import kotlinx.serialization.Serializable

/**
 * Per-player bookkeeping for [SixSchedule]. A *turn* starts when a player rolls after someone else
 * rolled (bonus rolls from a six, a capture or a token reaching Home stay inside the same turn).
 */
@Serializable
data class SixScheduleState(
    /** Turns started so far, per player index. */
    val turns: List<Int> = emptyList(),
    /** True once the player rolled a six in turn 6 or 7, so turn 8 needs no forced sixes. */
    val sixInWindow: List<Boolean> = emptyList(),
    /** Player who rolled last, or -1 before the first roll. */
    val lastRoller: Int = -1,
    /** 1-based index of the last roll inside [lastRoller]'s current turn. */
    val rollInTurn: Int = 0,
) {
    fun turnOf(player: Int): Int = turns.getOrElse(player) { 0 }
    fun hadWindowSix(player: Int): Boolean = sixInWindow.getOrElse(player) { false }
}

/**
 * Deterministic six pacing for Ludo, applied to every roll (human, bot, Auto-Bot) on top of the
 * dice source:
 * - turns 1..[NO_SIX_TURNS]: never a six (a raw six is replaced by a uniform 1..5);
 * - turns [GUARANTEE_FROM] and 7: natural dice; any six there satisfies the guarantee;
 * - turn [GUARANTEE_BY]: if no six came in turns 6–7, the first two rolls of the turn are forced to
 *   six ("Twice 6"); the third roll is kept off six when the triple-six forfeit is on, so the
 *   guaranteed pair is never thrown away;
 * - after turn [GUARANTEE_BY]: natural dice again.
 *
 * Pure function of its inputs: the caller supplies the raw roll and the replacement for a banned six.
 */
object SixSchedule {
    const val NO_SIX_TURNS = 5
    const val GUARANTEE_FROM = 6
    const val GUARANTEE_BY = 8

    data class Outcome(val value: Int, val state: SixScheduleState, val turn: Int, val rollInTurn: Int)

    fun roll(
        state: SixScheduleState,
        player: Int,
        playerCount: Int,
        raw: Int,
        tripleSixForfeit: Boolean,
        nonSix: () -> Int,
    ): Outcome {
        require(raw in 1..6) { "dice value $raw" }
        val turns = MutableList(playerCount) { state.turns.getOrElse(it) { 0 } }
        val window = MutableList(playerCount) { state.sixInWindow.getOrElse(it) { false } }
        val newTurn = state.lastRoller != player
        if (newTurn) turns[player] += 1
        val turn = turns[player]
        val rollInTurn = if (newTurn) 1 else state.rollInTurn + 1
        fun notSix(): Int = if (raw != 6) raw else nonSix().also { require(it in 1..5) { "replacement $it is not 1..5" } }

        val value = when {
            turn <= NO_SIX_TURNS -> notSix()
            turn < GUARANTEE_BY -> raw
            turn == GUARANTEE_BY && !window[player] -> when (rollInTurn) {
                1, 2 -> 6
                3 -> if (tripleSixForfeit) notSix() else raw
                else -> raw
            }
            else -> raw
        }
        if (value == 6 && turn in GUARANTEE_FROM until GUARANTEE_BY) {
            window[player] = true
        }
        return Outcome(value, SixScheduleState(turns, window, player, rollInTurn), turn, rollInTurn)
    }
}
