package com.ludosovereign.engine

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays thousands of random games across every topology/variant and checks the Fortress and
 * capture invariants against an independent oracle on every single move.
 */
class InvariantFuzzTest {

    private val setups: List<MatchConfig> = buildList {
        for (n in 2..4) {
            add(config(n))
            add(config(n, rules = RuleSet.quick(1)))
            add(config(n, rules = RuleSet.quick(2)))
            add(config(n, rules = RuleSet.rush()))
            add(config(n, rules = RuleSet(playForAllPlacements = true)))
        }
        for (n in 2..6) {
            add(config(n, BoardTopology.HEX_6))
            add(config(n, BoardTopology.HEX_6, RuleSet.rush()))
        }
        add(config(4, rules = RuleSet.teamUp()))
        add(config(4, BoardTopology.HEX_6, RuleSet.teamUp()))
        add(config(6, BoardTopology.HEX_6, RuleSet.teamUp()))
        add(config(6, BoardTopology.HEX_6, RuleSet.teamUp().copy(tokensToWin = 2)))
        add(config(4, rules = RuleSet(fortressEnabled = false)))
    }

    @Test fun randomGamesRespectInvariants() {
        var games = 0
        var captures = 0
        var standoffs = 0
        for ((i, cfg) in setups.withIndex()) {
            repeat(60) { g ->
                val r = Random(i * 1000L + g)
                val stats = playChecked(cfg, r)
                captures += stats.first
                standoffs += stats.second
                games++
            }
        }
        assertTrue(captures > 1000, "fuzz should exercise captures ($captures)")
        assertTrue(standoffs > 50, "fuzz should exercise fortress standoffs ($standoffs)")
        println("fuzz: $games games, $captures captures, $standoffs fortress standoffs")
    }

    private fun playChecked(cfg: MatchConfig, r: Random): Pair<Int, Int> {
        var s = LudoEngine.newGame(cfg)
        var captures = 0
        var standoffs = 0
        var steps = 0
        while (s.phase != Phase.GAME_OVER) {
            if (++steps > 20_000) fail("game did not terminate: $cfg")
            when (s.phase) {
                Phase.AWAITING_ROLL -> s = LudoEngine.roll(s, r.nextInt(1, 7))
                Phase.AWAITING_MOVE -> {
                    val moves = LudoEngine.legalMoves(s)
                    assertTrue(moves.isNotEmpty(), "AWAITING_MOVE must have moves")
                    val id = moves[r.nextInt(moves.size)]
                    val expectedVictims = oracleVictims(s, id)
                    val ev = mutableListOf<GameEvent>()
                    val next = LudoEngine.move(s, id, ev)
                    val victims = ev.filterIsInstance<GameEvent.Captured>().map { it.victimTokenId }.toSet()
                    assertEquals(expectedVictims, victims, "capture oracle mismatch for move $id in $s")
                    victims.forEach { assertEquals(BASE, next.tokens[it]) }
                    captures += victims.size
                    standoffs += ev.count { it is GameEvent.FortressStandoff }
                    checkWellFormed(next)
                    s = next
                }
                Phase.GAME_OVER -> Unit
            }
        }
        assertEquals(cfg.playerCount, s.finishOrder.size, "final standings list everyone")
        return captures to standoffs
    }

    /** Independent statement of Custom Rule #1. */
    private fun oracleVictims(s: GameState, mover: Int): Set<Int> {
        val owner = s.ownerOf(mover)
        val to = LudoEngine.targetProgress(s, mover, s.dice)!!
        val cell = s.topology.absoluteCell(s.seatOf(owner), to)
        if (cell < 0 || s.topology.isSafeCell(cell)) return emptySet()
        val myTeam = s.teamOf(owner)
        val byTeam = s.tokens.indices
            .filter { it != mover && s.cellOf(it) == cell && s.teamOf(s.ownerOf(it)) != myTeam }
            .groupBy { s.teamOf(s.ownerOf(it)) }
        return byTeam.values
            .filter { group -> !s.rules.fortressEnabled || group.size == 1 }
            .flatten().toSet()
    }

    private fun checkWellFormed(s: GameState) {
        val topo = s.topology
        s.tokens.forEach { assertTrue(it == BASE || it in 0..topo.homeProgress) }
        if (s.phase == Phase.GAME_OVER) return // final standings also rank unfinished players
        for (p in 0 until s.config.playerCount) {
            val home = s.tokensHome(p)
            if (s.hasFinished(p)) assertTrue(home >= s.rules.tokensToWin)
        }
    }
}
