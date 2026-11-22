package com.ludosovereign.ai

import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.RuleSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotTest {
    private val topo = BoardTopology.CLASSIC_4
    private fun p1Progress(cell: Int) = ((cell - topo.startCell(2)) % 52 + 52) % 52

    private fun state(tiers: List<BotTier>, vararg placed: Pair<Int, Int>, current: Int = 0, dice: Int = 0): GameState {
        val cfg = botConfig(tiers)
        val tokens = IntArray(cfg.tokenCount) { BASE }
        placed.forEach { (id, p) -> tokens[id] = p }
        val phase = if (dice > 0) Phase.AWAITING_MOVE else Phase.AWAITING_ROLL
        return GameState(cfg, tokens, current, phase, dice)
    }

    @Test fun grandmasterTakesTheCapture() {
        // P0: token 0 at 7 can capture P1's singleton on cell 10 with a 3; token 1 at 20 would just advance.
        val s = state(listOf(BotTier.GRANDMASTER, BotTier.CASUAL), 0 to 7, 1 to 20, 4 to p1Progress(10), dice = 3)
        assertEquals(0, GrandmasterBot(budgetMs = 200).chooseMove(s))
    }

    @Test fun grandmasterEscapesThreatIntoFortress() {
        // Token 0 at 15 is hunted by P1 from 2 cells behind; token 1 sits at 18. Rolling 3 joins them.
        val s = state(listOf(BotTier.GRANDMASTER, BotTier.CASUAL),
            0 to 15, 1 to 18, 2 to 30, 4 to p1Progress(13), dice = 3)
        assertEquals(0, GrandmasterBot(budgetMs = 200).chooseMove(s))
    }

    @Test fun unbeatableCutsTheExposedSingletonThisTurn() {
        // An enemy singleton sits 4 ahead of our only token. The oracle may first roll a 6 to deploy
        // (bonus roll) but must cut the singleton before its turn ends.
        var s = state(listOf(BotTier.UNBEATABLE, BotTier.CASUAL), 0 to 10, 4 to p1Progress(14))
        val bot = UnbeatableEngine(budgetMs = 200)
        var guard = 0
        while (s.current == 0 && s.phase != Phase.GAME_OVER && guard++ < 10) {
            s = LudoEngine.roll(s, bot.chooseDice(s))
            if (s.phase == Phase.AWAITING_MOVE) s = LudoEngine.move(s, bot.chooseMove(s))
            if (s.tokens[4] == BASE) break
        }
        assertEquals(BASE, s.tokens[4])
    }

    @Test fun unbeatableAvoidsTripleSixForfeit() {
        val s = state(listOf(BotTier.UNBEATABLE, BotTier.CASUAL), 0 to 10, 1 to 30, 4 to p1Progress(40)).copy(consecutiveSixes = 2)
        val d = UnbeatableEngine(budgetMs = 100).chooseDice(s)
        assertTrue(d in 1..5, "third six would forfeit; got $d")
    }

    @Test fun botsOnlyMakeLegalMovesAcrossVariants() {
        val setups = listOf(
            botConfig(listOf(BotTier.UNBEATABLE, BotTier.GRANDMASTER, BotTier.CASUAL, BotTier.CASUAL)),
            botConfig(List(6) { BotTier.entries[it % 3] }, BoardTopology.HEX_6),
            botConfig(listOf(BotTier.UNBEATABLE, BotTier.CASUAL, BotTier.GRANDMASTER, BotTier.CASUAL), rules = RuleSet.teamUp()),
            botConfig(listOf(BotTier.GRANDMASTER, BotTier.UNBEATABLE), rules = RuleSet.quick(2)),
            botConfig(listOf(BotTier.GRANDMASTER, BotTier.CASUAL, BotTier.UNBEATABLE), rules = RuleSet.rush()),
        )
        for ((i, cfg) in setups.withIndex()) {
            val bots = cfg.players.map { BotFactory.create(it.botTier!!, Random(i), budgetMs = 5) }
            val winner = playOut(cfg, bots, Random(i))
            assertTrue(winner >= 0, "game $i must finish")
        }
    }

    @Test fun searchRespectsBudget() {
        val cfg = botConfig(listOf(BotTier.UNBEATABLE, BotTier.GRANDMASTER, BotTier.GRANDMASTER, BotTier.GRANDMASTER))
        val positions = midgamePositions(cfg, 40, 3)
        val bot = UnbeatableEngine(budgetMs = 30)
        positions.take(10).forEach { bot.chooseDice(it) } // warm-up JIT
        val times = positions.map { bot.chooseDice(it); bot.lastResult!!.elapsedMicros / 1000.0 }.sorted()
        val p95 = times[(times.size * 0.95).toInt().coerceAtMost(times.size - 1)]
        assertTrue(p95 < 45, "p95 ${p95}ms exceeds budget+slack")
    }

    @Test fun grandmasterBeatsCasual() {
        val r = Random(11)
        var wins = 0
        val games = 40
        repeat(games) { g ->
            val order = if (g % 2 == 0) listOf(BotTier.GRANDMASTER, BotTier.CASUAL) else listOf(BotTier.CASUAL, BotTier.GRANDMASTER)
            val cfg = botConfig(order)
            val bots = order.map { BotFactory.create(it, Random(g), budgetMs = 4) }
            val gmTeam = order.indexOf(BotTier.GRANDMASTER)
            if (playOut(cfg, bots, r) == gmTeam) wins++
        }
        println("Grandmaster vs Casual: $wins/$games")
        assertTrue(wins >= games * 0.6, "Grandmaster won only $wins/$games")
    }

    @Test fun unbeatableBeatsGrandmaster() {
        val r = Random(5)
        var wins = 0
        val games = 20
        repeat(games) { g ->
            val order = if (g % 2 == 0) listOf(BotTier.UNBEATABLE, BotTier.GRANDMASTER) else listOf(BotTier.GRANDMASTER, BotTier.UNBEATABLE)
            val cfg = botConfig(order)
            val bots = order.map { BotFactory.create(it, Random(g), budgetMs = 4) }
            if (playOut(cfg, bots, r) == order.indexOf(BotTier.UNBEATABLE)) wins++
        }
        println("Unbeatable vs Grandmaster: $wins/$games")
        assertTrue(wins >= games * 0.9, "Unbeatable won only $wins/$games")
    }
}
