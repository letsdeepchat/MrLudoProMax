package com.ludosovereign.app

import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.geometry.BoardGeometry
import com.ludosovereign.feature.board.BoardView
import com.ludosovereign.session.AnimationTimings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** The hop engine's path resolver: a roll of N is N discrete, adjacent tiles, never a teleport. */
class HopPathTest {
    @Test fun rollingAFourIsFourAdjacentHops() {
        for (topo in BoardTopology.entries) {
            val view = BoardView(BoardGeometry(topo))
            for (seat in 0 until topo.seats) for (from in listOf(0, 10, 25, topo.lastTrackProgress - 2)) {
                val to = (from + 4).coerceAtMost(topo.homeProgress)
                val path = view.hopPath(seat, from, to, 0)
                assertEquals("$topo seat $seat from $from", to - from, path.size)
                var prev = view.tokenPoint(seat, from, 0)
                for (p in path) {
                    val d = hypot(p.x - prev.x, p.y - prev.y)
                    assertTrue("$topo seat $seat hop $from→$to spans $d tiles", d in 0.5f..1.8f)
                    prev = p
                }
            }
        }
    }

    @Test fun animationBudgetCoversEveryHop() {
        val t = AnimationTimings()
        val events = listOf(GameEvent.DiceRolled(0, 4), GameEvent.TokenMoved(0, 10, 14))
        assertEquals(4, t.hops(events))
        assertTrue(t.moveDuration(events) >= 4 * t.hopMs)
        assertTrue("default hop length within 220–250 ms (classic pace)", t.hopMs in 220..250)
        assertTrue("roll length within 350–420 ms", t.rollMs in 350..420)
    }
}
