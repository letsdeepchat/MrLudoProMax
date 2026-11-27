package com.ludosovereign.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SixScheduleTest {
    private val replacement = { 3 }

    /** Plays [turns] single-roll turns for player 0 of 2 (player 1 rolls a 2 in between). */
    private fun play(raws: List<Int>, forfeit: Boolean = true): Pair<List<Int>, SixScheduleState> {
        var s = SixScheduleState()
        val values = ArrayList<Int>()
        for (raw in raws) {
            val o = SixSchedule.roll(s, 0, 2, raw, forfeit, replacement)
            values += o.value
            s = SixSchedule.roll(o.state, 1, 2, 2, forfeit, replacement).state
        }
        return values to s
    }

    @Test fun noSixInTheFirstFiveTurns() {
        val (values, s) = play(List(5) { 6 })
        assertEquals(List(5) { 3 }, values, "every raw six in turns 1-5 is replaced")
        assertEquals(5, s.turnOf(0))
        // Non-six rolls pass straight through.
        assertEquals(listOf(1, 2, 3, 4, 5), play(listOf(1, 2, 3, 4, 5)).first)
    }

    @Test fun bonusRollsStayInsideTheSameTurn() {
        var s = SixScheduleState()
        // A capture bonus in turn 1 is a second roll of the same turn, still six-free.
        s = SixSchedule.roll(s, 0, 2, 4, true, replacement).state
        val bonus = SixSchedule.roll(s, 0, 2, 6, true, replacement)
        assertEquals(1, bonus.turn)
        assertEquals(2, bonus.rollInTurn)
        assertEquals(3, bonus.value)
        val next = SixSchedule.roll(bonus.state, 1, 2, 5, true, replacement)
        assertEquals(1, next.turn, "player 1's first turn")
    }

    @Test fun naturalSixOnTurnSixOrSevenSkipsTheForcedPair() {
        for (sixTurn in listOf(6, 7)) {
            val raws = MutableList(8) { 2 }.also { it[sixTurn - 1] = 6 }
            // Turn 8 raw is 4: with the guarantee already met it stays 4.
            raws[7] = 4
            val (values, s) = play(raws)
            assertEquals(6, values[sixTurn - 1], "turn $sixTurn keeps its natural six")
            assertEquals(4, values[7], "turn 8 is natural once a six came on turn $sixTurn")
            assertTrue(s.hadWindowSix(0))
        }
    }

    @Test fun noSixOnTurnsSixAndSevenForcesTwiceSixOnTurnEight() {
        var s = play(List(7) { 1 }).second
        assertEquals(7, s.turnOf(0))
        val first = SixSchedule.roll(s, 0, 2, 1, true, replacement)
        val second = SixSchedule.roll(first.state, 0, 2, 2, true, replacement)
        val third = SixSchedule.roll(second.state, 0, 2, 6, true, replacement)
        assertEquals(8, first.turn)
        assertEquals(6, first.value)
        assertEquals(6, second.value, "two consecutive sixes on turn 8")
        assertNotEquals(6, third.value, "the third roll never throws the pair away to the triple-six forfeit")
        s = third.state
        // Turn 9 onward: natural dice.
        s = SixSchedule.roll(s, 1, 2, 3, true, replacement).state
        val ninth = SixSchedule.roll(s, 0, 2, 6, true, replacement)
        assertEquals(9, ninth.turn)
        assertEquals(6, ninth.value)
    }

    @Test fun withoutTripleSixForfeitTheThirdRollIsNatural() {
        val s = play(List(7) { 5 }, forfeit = false).second
        val a = SixSchedule.roll(s, 0, 2, 1, false, replacement)
        val b = SixSchedule.roll(a.state, 0, 2, 1, false, replacement)
        val c = SixSchedule.roll(b.state, 0, 2, 6, false, replacement)
        assertEquals(listOf(6, 6, 6), listOf(a.value, b.value, c.value))
    }

    @Test fun everyPlayerIsTrackedSeparately() {
        var s = SixScheduleState()
        val rng = Random(7)
        val sixTurns = Array(4) { ArrayList<Int>() }
        repeat(12) {
            for (p in 0 until 4) {
                var o = SixSchedule.roll(s, p, 4, rng.nextInt(1, 7), true) { rng.nextInt(1, 6) }
                s = o.state
                if (o.value == 6) sixTurns[p] += o.turn
                // Follow sixes with their bonus rolls, as the engine does.
                while (o.value == 6 && o.rollInTurn < 3) {
                    o = SixSchedule.roll(s, p, 4, rng.nextInt(1, 7), true) { rng.nextInt(1, 6) }
                    s = o.state
                    if (o.value == 6) sixTurns[p] += o.turn
                }
            }
        }
        for (p in 0 until 4) {
            assertEquals(12, s.turnOf(p))
            assertTrue(sixTurns[p].none { it <= 5 }, "player $p rolled a six in turns 1-5: ${sixTurns[p]}")
            assertTrue(sixTurns[p].any { it in 6..8 }, "player $p had no six in turns 6-8: ${sixTurns[p]}")
        }
    }

    @Test fun fuzzGuaranteeHoldsForAnyDice() {
        repeat(500) { seed ->
            val rng = Random(seed)
            var s = SixScheduleState()
            val sixes = HashMap<Int, Int>()
            for (turn in 1..10) {
                var o = SixSchedule.roll(s, 0, 2, rng.nextInt(1, 7), true) { rng.nextInt(1, 6) }
                s = o.state
                if (o.value == 6) sixes.merge(o.turn, 1, Int::plus)
                while (o.value == 6) {
                    o = SixSchedule.roll(s, 0, 2, rng.nextInt(1, 7), true) { rng.nextInt(1, 6) }
                    s = o.state
                    if (o.value == 6) sixes.merge(o.turn, 1, Int::plus)
                    if (o.rollInTurn == 3) break
                }
                s = SixSchedule.roll(s, 1, 2, 1, true) { 1 }.state
            }
            assertTrue((1..5).none { it in sixes }, "seed $seed: $sixes")
            val window = (6..8).sumOf { sixes[it] ?: 0 }
            assertTrue(window >= 1, "seed $seed: $sixes")
            if ((sixes[6] ?: 0) + (sixes[7] ?: 0) == 0) assertEquals(2, sixes[8], "seed $seed: forced pair on turn 8")
        }
    }
}
