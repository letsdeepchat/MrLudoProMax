package com.ludosovereign.ai

import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.PlayerSpec
import com.ludosovereign.engine.RuleSet
import kotlin.random.Random

internal fun botConfig(
    tiers: List<BotTier>,
    topology: BoardTopology = BoardTopology.CLASSIC_4,
    rules: RuleSet = RuleSet.classic(),
) = MatchConfig.create(topology, rules, tiers.mapIndexed { i, t -> PlayerSpec("B$i", PlayerKind.BOT, t, i) })

/** Plays one full game; returns the winning team. */
internal fun playOut(cfg: MatchConfig, bots: List<LudoBot>, random: Random, maxRolls: Int = 5000): Int {
    var s = LudoEngine.newGame(cfg)
    var rolls = 0
    while (s.phase != Phase.GAME_OVER && rolls++ < maxRolls) {
        val bot = bots[s.current]
        val d = bot.chooseDice(s) ?: random.nextInt(1, 7)
        s = LudoEngine.roll(s, d)
        if (s.phase == Phase.AWAITING_MOVE) s = LudoEngine.move(s, bot.chooseMove(s))
    }
    return s.winnerTeam
}

/** Random mid-game positions (AWAITING_ROLL) for latency measurements. */
internal fun midgamePositions(cfg: MatchConfig, count: Int, seed: Long): List<GameState> {
    val r = Random(seed)
    val out = ArrayList<GameState>()
    while (out.size < count) {
        var s = LudoEngine.newGame(cfg)
        val stopAt = r.nextInt(40, 200)
        var rolls = 0
        while (s.phase != Phase.GAME_OVER && rolls < stopAt) {
            if (s.phase == Phase.AWAITING_ROLL) { s = LudoEngine.roll(s, r.nextInt(1, 7)); rolls++ }
            else { val m = LudoEngine.legalMoves(s); s = LudoEngine.move(s, m[r.nextInt(m.size)]) }
        }
        if (s.phase == Phase.AWAITING_ROLL) out += s
    }
    return out
}
