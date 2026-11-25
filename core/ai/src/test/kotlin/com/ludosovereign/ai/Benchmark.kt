package com.ludosovereign.ai

import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.Phase
import kotlin.random.Random

/**
 * `./gradlew :core:ai:benchmark`
 * 1) Fixed-depth cost (no budget) per scenario, to see what depth fits in 80 ms on this CPU.
 * 2) Production setting (80 ms budget): latency percentiles and depth actually reached.
 * 3) Head-to-head strength at the production budget.
 */
fun main() {
    val scenarios = listOf(
        "Classic 2p" to botConfig(listOf(BotTier.UNBEATABLE, BotTier.GRANDMASTER)),
        "Classic 4p" to botConfig(listOf(BotTier.UNBEATABLE, BotTier.GRANDMASTER, BotTier.GRANDMASTER, BotTier.GRANDMASTER)),
        "Hex 6p" to botConfig(List(6) { if (it == 0) BotTier.UNBEATABLE else BotTier.GRANDMASTER }, BoardTopology.HEX_6),
    )
    println("== Fixed depth (unbounded time), median / p95 ms ==")
    for ((name, cfg) in scenarios) {
        val positions = midgamePositions(cfg, 60, 42)
        for (depth in 2..4) {
            val gm = timeIt(positions) { s ->
                val r = LudoEngine.roll(s, 1 + (s.rollCount % 6))
                if (r.phase == Phase.AWAITING_MOVE && LudoEngine.legalMoves(r).size > 1)
                    ExpectimaxSearch().bestMove(r, depth, 60_000).elapsedMicros else null
            }
            val ub = timeIt(positions) { s ->
                ExpectimaxSearch(diceNodeFor = { st, p -> if (p == 0) DiceNode.ORACLE else DiceNode.CHANCE })
                    .bestRollAndMove(s, depth, 60_000).elapsedMicros
            }
            println("%-11s depth %d | Grandmaster move %s | Unbeatable oracle %s".format(name, depth, gm, ub))
        }
    }

    println("\n== Production budget 80 ms ==")
    for ((name, cfg) in scenarios) {
        val positions = midgamePositions(cfg, 120, 7)
        val ub = UnbeatableEngine()
        val gm = GrandmasterBot()
        val ubT = ArrayList<Double>(); val ubD = ArrayList<Int>()
        val gmT = ArrayList<Double>(); val gmD = ArrayList<Int>()
        var nodes = 0L; var micros = 0L
        for (s in positions) {
            val r = LudoEngine.roll(s, 6)
            if (r.phase == Phase.AWAITING_MOVE && LudoEngine.legalMoves(r).size > 1) {
                ub.chooseMove(r); ub.lastResult?.let { ubT += it.elapsedMicros / 1000.0; ubD += it.depthReached; nodes += it.nodes; micros += it.elapsedMicros }
            }
            if (r.phase == Phase.AWAITING_MOVE && LudoEngine.legalMoves(r).size > 1) {
                gm.chooseMove(r); gm.lastResult!!.let { gmT += it.elapsedMicros / 1000.0; gmD += it.depthReached }
            }
        }
        println("%-11s Unbeatable: %s, depth %s | Grandmaster: %s, depth %s | %.1fM nodes/s".format(
            name, pct(ubT), hist(ubD), pct(gmT), hist(gmD), nodes / (micros.toDouble())))
    }

    println("\n== Strength at 80 ms (2-player classic, seats alternate) ==")
    val r = Random(1)
    for ((a, b, games) in listOf(Triple(BotTier.GRANDMASTER, BotTier.CASUAL, 30), Triple(BotTier.UNBEATABLE, BotTier.GRANDMASTER, 20))) {
        var wins = 0
        repeat(games) { g ->
            val order = if (g % 2 == 0) listOf(a, b) else listOf(b, a)
            val bots = order.map { BotFactory.create(it, Random(g)) }
            if (playOut(botConfig(order), bots, r) == order.indexOf(a)) wins++
        }
        println("$a vs $b: $wins/$games")
    }
}

private fun timeIt(positions: List<com.ludosovereign.engine.GameState>, f: (com.ludosovereign.engine.GameState) -> Long?): String {
    positions.take(8).forEach { f(it) } // JIT warm-up
    return pct(positions.mapNotNull(f).map { it / 1000.0 })
}

private fun pct(xs: List<Double>): String {
    if (xs.isEmpty()) return "n/a"
    val s = xs.sorted()
    fun q(p: Double) = s[(p * (s.size - 1)).toInt()]
    return "p50 %.1f / p95 %.1f / max %.1f ms".format(q(0.5), q(0.95), s.last())
}

private fun hist(ds: List<Int>): String = ds.groupingBy { it }.eachCount().toSortedMap().entries.joinToString(" ") { "d${it.key}:${it.value}" }
