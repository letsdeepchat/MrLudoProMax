package com.ludosovereign.engine

import com.ludosovereign.engine.snl.SnakesAndLaddersEngine
import com.ludosovereign.engine.snl.SnlEvent
import com.ludosovereign.engine.snl.SnlLayout
import com.ludosovereign.engine.snl.SnlLayouts
import com.ludosovereign.engine.snl.SnlRules
import com.ludosovereign.engine.snl.SnlState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SnakesAndLaddersTest {
    @Test fun sevenDistinctValidLayouts() {
        assertEquals(7, SnlLayouts.ALL.size)
        assertEquals(7, SnlLayouts.ALL.map { it.id }.toSet().size)
        assertEquals(64, SnlLayouts.SPEED_64.size)
        SnlLayouts.ALL.forEach { assertTrue(it.ladders.isNotEmpty() && it.snakes.isNotEmpty()) }
    }

    @Test fun invalidLayoutsRejected() {
        assertFailsWith<IllegalArgumentException> { SnlLayout("x", "x", 100, mapOf(5 to 3), emptyMap()) }
        assertFailsWith<IllegalArgumentException> { SnlLayout("x", "x", 100, mapOf(5 to 20), mapOf(20 to 2)) }
        assertFailsWith<IllegalArgumentException> { SnlLayout("x", "x", 90, emptyMap(), emptyMap()) }
    }

    @Test fun ladderAndSnake() {
        val start = SnakesAndLaddersEngine.newGame(SnlLayouts.CLASSIC_100, players(2))
        val ev = mutableListOf<SnlEvent>()
        val s = SnakesAndLaddersEngine.roll(start, 4, ev)
        assertEquals(25, s.positions[0])
        assertTrue(ev.contains(SnlEvent.LadderClimb(0, 4, 25)))
        val atSnake = SnlState(start.layoutId, start.players, start.rules, intArrayOf(14, 0))
        val bitten = SnakesAndLaddersEngine.roll(atSnake, 3, ev)
        assertEquals(7, bitten.positions[0])
        assertTrue(ev.contains(SnlEvent.SnakeSlide(0, 17, 7)))
    }

    @Test fun exactFinishAndBounce() {
        val base = SnakesAndLaddersEngine.newGame(SnlLayouts.SPEED_64, players(2))
        val near = base.copy(positions = intArrayOf(61, 0))
        assertEquals(61, SnakesAndLaddersEngine.roll(near, 5).positions[0])
        val bounce = SnlState(base.layoutId, base.players, SnlRules(exactFinish = false), intArrayOf(61, 0))
        // 61 + 5 = 66 -> bounces to 62, which is a snake head -> 45.
        assertEquals(45, SnakesAndLaddersEngine.roll(bounce, 5).positions[0])
        val win = SnakesAndLaddersEngine.roll(near, 3)
        assertTrue(win.gameOver)
        assertEquals(listOf(0, 1), win.finishOrder.toList())
    }

    @Test fun sixGivesBonusAndTripleForfeits() {
        var s = SnakesAndLaddersEngine.newGame(SnlLayouts.SPEED_64, players(2)).copy(positions = intArrayOf(11, 0))
        s = SnakesAndLaddersEngine.roll(s, 6); assertEquals(0, s.current)
        s = SnakesAndLaddersEngine.roll(s, 6); assertEquals(0, s.current)
        val pos = s.positions[0]
        s = SnakesAndLaddersEngine.roll(s, 6)
        assertEquals(1, s.current); assertEquals(pos, s.positions[0])
    }

    @Test fun randomGamesTerminateOnEveryLayout() {
        for (layout in SnlLayouts.ALL) for (n in 2..6) repeat(20) { g ->
            val r = Random(g * 31L + n)
            var s = SnakesAndLaddersEngine.newGame(layout, players(n), SnlRules(playForAllPlacements = g % 2 == 0))
            var steps = 0
            while (!s.gameOver) {
                s = SnakesAndLaddersEngine.roll(s, r.nextInt(1, 7))
                assertTrue(++steps < 100_000)
                s.positions.forEach { assertTrue(it in 0..layout.size) }
            }
            assertEquals(n, s.finishOrder.size)
        }
    }

    @Test fun codecRoundTrip() {
        val s = SnakesAndLaddersEngine.roll(SnakesAndLaddersEngine.newGame(SnlLayouts.MYSTIC_CAVES, players(3)), 5)
        assertEquals(s, StateCodec.decodeSnl(StateCodec.encode(s)))
    }

    @Test fun gridIsBoustrophedon() {
        val l = SnlLayouts.CLASSIC_100
        assertEquals(0 to 0, l.gridOf(1))
        assertEquals(0 to 9, l.gridOf(10))
        assertEquals(1 to 9, l.gridOf(11))
        assertEquals(9 to 0, l.gridOf(100))
    }
}
