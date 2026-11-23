package com.ludosovereign.engine

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import kotlin.test.Test
import kotlin.test.assertEquals

class EndNowTest {
    @Test fun endNowRanksUnfinishedPlayersByProgress() {
        val players = List(4) { PlayerSpec("P$it") }
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet(playForAllPlacements = true), players)
        val tokens = IntArray(16) { BASE }.also { it[4] = 3; it[8] = 30; it[12] = 12 }
        val s = GameState(cfg, tokens, 1, Phase.AWAITING_ROLL, finishOrder = intArrayOf(0))
        val events = ArrayList<GameEvent>()
        val end = LudoEngine.endNow(s, events)
        assertEquals(Phase.GAME_OVER, end.phase)
        assertEquals(listOf(0, 2, 3, 1), end.finishOrder.toList())
        assertEquals(0, end.winnerTeam)
        assertEquals(GameEvent.GameOver(0, listOf(0, 2, 3, 1)), events.single())
    }
}
