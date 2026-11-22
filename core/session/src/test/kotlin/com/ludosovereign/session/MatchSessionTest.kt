package com.ludosovereign.session

import com.ludosovereign.ai.BotFactory
import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.PlayerSpec
import com.ludosovereign.engine.RuleSet
import com.ludosovereign.engine.ScriptedDice
import com.ludosovereign.engine.SeededDice
import com.ludosovereign.engine.StateCodec
import com.ludosovereign.engine.snl.SnakesAndLaddersEngine
import com.ludosovereign.engine.snl.SnlLayouts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MatchSessionTest {
    private val human = PlayerSpec("You", PlayerKind.HUMAN)
    private fun bot(t: BotTier) = PlayerSpec(t.name, PlayerKind.BOT, t)

    @Test fun allBotMatchRunsToCompletionAndCheckpointsEveryAction() = runTest {
        val cfg = MatchConfig.create(BoardTopology.HEX_6, RuleSet.classic(),
            listOf(bot(BotTier.UNBEATABLE), bot(BotTier.GRANDMASTER), bot(BotTier.CASUAL), bot(BotTier.CASUAL)))
        val bots = cfg.players.indices.associateWith { BotFactory.create(cfg.players[it].botTier!!, Random(it), budgetMs = 3) }
        val checkpoints = mutableListOf<Checkpoint>()
        var result: MatchResult? = null
        val session = MatchSession(LudoEngine.newGame(cfg), bots = bots, dice = SeededDice(1),
            aiDispatcher = StandardTestDispatcher(testScheduler),
            onCheckpoint = { checkpoints += it }, onFinished = { result = it })
        val job = session.start(this)
        advanceUntilIdle()
        val r = assertNotNull(result)
        assertEquals(session.log.actions.size, checkpoints.size)
        assertTrue(checkpoints.last().finished)
        // The saved log replays to the saved final state.
        assertEquals(StateCodec.decodeGame(checkpoints.last().stateJson), StateCodec.decodeLog(checkpoints.last().logJson!!).replay())
        assertEquals(cfg.playerCount, r.finishOrder.size)
        val stats = r.stats!!
        assertTrue(stats.players.sumOf { it.captures } == stats.players.sumOf { it.timesCaptured })
        assertEquals(stats.players.sumOf { it.rolls }, session.log.actions.count { it is com.ludosovereign.engine.GameAction.Roll })
        job.cancel()
    }

    @Test fun humanRollAutoMoveAndUndo() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20 }
        val start = GameState(cfg, tokens, 0, Phase.AWAITING_ROLL)
        val session = MatchSession(start, bots = emptyMap(), dice = ScriptedDice(3, 4, 2),
            settings = SessionSettings(undoEnabled = true), aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        assertTrue(session.ui.value.awaitingHuman)
        session.roll(); advanceUntilIdle()
        assertEquals(Phase.AWAITING_MOVE, session.ui.value.game.phase)
        assertEquals(2, session.ui.value.legalMoves.size)
        session.move(1); advanceUntilIdle()
        assertEquals(23, session.ui.value.game.tokens[1])
        assertTrue(session.ui.value.canUndo)
        session.undo(); advanceUntilIdle()
        val g = session.ui.value.game
        assertEquals(Phase.AWAITING_MOVE, g.phase)
        assertEquals(3, g.dice, "undo keeps the same dice so only the decision is revisited")
        assertEquals(20, g.tokens[1])
        session.move(0); advanceUntilIdle()
        assertEquals(13, session.ui.value.game.tokens[0])
        // Friend has everything in base: a 4 means no legal move, turn passes back; undo window closed.
        session.roll(); advanceUntilIdle()
        assertEquals(0, session.ui.value.game.current)
        assertFalse(session.ui.value.canUndo)
        // Auto-move: P0 rolls 2 with two tokens... both movable, so no auto-move; check single-move case below.
        job.cancel()
    }

    @Test fun autoMovePlaysTheOnlyMove() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, bot(BotTier.CASUAL)))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL),
            bots = mapOf(1 to BotFactory.create(BotTier.CASUAL, Random(1))), dice = ScriptedDice(4, 3, 3, 3),
            aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        session.roll(); advanceUntilIdle()
        assertEquals(14, session.ui.value.game.tokens[0])
        job.cancel()
    }

    @Test fun rushTimerPlaysForIdleHuman() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.rush(10), listOf(human, human.copy(name = "B")))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 30 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(5, 2), aiDispatcher = StandardTestDispatcher(testScheduler), clock = { testScheduler.currentTime })
        val job = session.start(this)
        runCurrent()
        assertEquals(10_000L, session.ui.value.turnDeadlineAtMs)
        testScheduler.advanceTimeBy(10_001); runCurrent()
        assertEquals(Phase.AWAITING_MOVE, session.ui.value.game.phase, "timer auto-rolled")
        testScheduler.advanceTimeBy(20_000); runCurrent()
        assertEquals(1, session.ui.value.game.current, "timer auto-moved and passed the turn")
        job.cancel()
    }

    @Test fun restoreFromCheckpointResumesExactly() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.quick(), listOf(human, bot(BotTier.GRANDMASTER)))
        var last: Checkpoint? = null
        val s1 = MatchSession(LudoEngine.newGame(cfg), bots = mapOf(1 to BotFactory.create(BotTier.GRANDMASTER, budgetMs = 2)),
            dice = SeededDice(9), aiDispatcher = StandardTestDispatcher(testScheduler), onCheckpoint = { last = it })
        val job = s1.start(this)
        runCurrent()
        repeat(5) { s1.roll(); advanceUntilIdle(); s1.ui.value.legalMoves.firstOrNull()?.let { s1.move(it); advanceUntilIdle() } }
        job.cancel()
        val saved = assertNotNull(last)
        val s2 = MatchSession.restore(saved, { st -> mapOf(1 to BotFactory.create(BotTier.GRANDMASTER, budgetMs = 2)) },
            SeededDice(2), SessionSettings(), {}, {})
        assertEquals(StateCodec.decodeGame(saved.stateJson), s2.ui.value.game)
        assertEquals(s1.log, s2.log)
    }

    @Test fun snakesAndLaddersBotsFinish() = runTest {
        val players = listOf(bot(BotTier.CASUAL), bot(BotTier.CASUAL), bot(BotTier.CASUAL))
        var result: MatchResult? = null
        val s = SnlSession(SnakesAndLaddersEngine.newGame(SnlLayouts.JUNGLE_RAPIDS, players), SeededDice(4), onFinished = { result = it })
        val job = s.start(this)
        advanceUntilIdle()
        assertEquals(3, assertNotNull(result).finishOrder.size)
        job.cancel()
    }

    @Test fun progressionRanks() {
        assertEquals(Progression.Rank.PAWN, Progression.rankFor(0))
        assertEquals(Progression.Rank.KNIGHT, Progression.rankFor(1000))
        assertEquals(Progression.Rank.SOVEREIGN, Progression.rankFor(50_000))
        assertTrue(Progression.xpFor(PlayerMatchStats(captures = 2), 1, 4) > Progression.xpFor(PlayerMatchStats(), 4, 4))
    }
}
