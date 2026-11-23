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

    @Test fun rapidDiceTapsRollExactlyOnce() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val session = MatchSession(LudoEngine.newGame(cfg), bots = emptyMap(), dice = ScriptedDice(3, 3, 3, 3, 3),
            aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        assertEquals(TurnPhase.WAITING_FOR_ROLL, session.ui.value.turnPhase)
        repeat(6) { session.roll() } // frantic tapping before the session reacts
        runCurrent()
        assertEquals(TurnPhase.ROLLING_DICE, session.ui.value.turnPhase)
        repeat(6) { session.roll() } // tapping during the tumble is ignored outright
        advanceUntilIdle()
        // A 3 with everything in base passes the turn: Friend must not inherit the queued taps.
        assertEquals(1, session.ui.value.game.current)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, session.ui.value.turnPhase)
        assertEquals(1, session.log.actions.count { it is com.ludosovereign.engine.GameAction.Roll })
        job.cancel()
    }

    @Test fun nextActorWaitsForTheHopAnimation() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, bot(BotTier.CASUAL)))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20; it[4] = 30 }
        val settings = SessionSettings()
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL),
            bots = mapOf(1 to BotFactory.create(BotTier.CASUAL, Random(1))), dice = ScriptedDice(4, 2, 2, 2),
            settings = settings, aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        session.roll(); advanceUntilIdle()
        assertEquals(TurnPhase.SELECTING_TOKEN, session.ui.value.turnPhase)
        session.move(0); runCurrent()
        val s = session.ui.value
        assertEquals(TurnPhase.ANIMATING_TOKEN, s.turnPhase)
        assertEquals(4, s.timings.hops(s.lastEvents), "a 4 is four discrete hops")
        session.move(1); session.roll(); runCurrent() // taps during the animation are dropped
        val budget = s.timings.moveDuration(s.lastEvents)
        testScheduler.advanceTimeBy(budget - 1); runCurrent()
        assertEquals(TurnPhase.ANIMATING_TOKEN, session.ui.value.turnPhase, "bot must not start mid-hop")
        assertEquals(0, session.ui.value.rollingPlayer.coerceAtLeast(0))
        testScheduler.advanceTimeBy(2); runCurrent()
        assertEquals(1, session.ui.value.game.current)
        assertEquals(20, session.ui.value.game.tokens[1], "the dropped tap moved nothing")
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
        testScheduler.advanceTimeBy(10_001 + 1_000); runCurrent() // timer, then the dice tumble
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

    @Test fun autoBotTakesOverAndHandsBack() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20; it[4] = 20 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(3, 2, 4, 1, 3, 2, 4, 1), aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        assertEquals(TurnPhase.WAITING_FOR_ROLL, session.ui.value.turnPhase)
        // You step away: the Auto-Bot rolls and moves for you, then Friend waits for a human tap.
        session.toggleAuto(0); advanceUntilIdle()
        assertTrue(0 in session.ui.value.autoPlayers)
        assertEquals(1, session.ui.value.game.current)
        assertTrue(session.ui.value.awaitingHuman)
        assertEquals(1, session.log.actions.count { it is com.ludosovereign.engine.GameAction.MoveToken })
        // Back from the break: hand control back before your next turn.
        session.toggleAuto(0); advanceUntilIdle()
        assertFalse(0 in session.ui.value.autoPlayers)
        session.roll(); advanceUntilIdle() // Friend rolls a 2 with one token out: auto-move
        assertEquals(0, session.ui.value.game.current)
        assertEquals(TurnPhase.WAITING_FOR_ROLL, session.ui.value.turnPhase, "you are asked to roll again")
        // Bots can't be toggled; neither can unknown players.
        session.toggleAuto(7)
        assertTrue(session.ui.value.autoPlayers.isEmpty())
        job.cancel()
    }

    @Test fun matchContinuesAfterFirstFinisherAndCanEndEarly() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic().copy(playForAllPlacements = true),
            listOf(human, human.copy(name = "B"), human.copy(name = "C")))
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = IntArray(12) { BASE }.also { it[0] = h; it[1] = h; it[2] = h; it[3] = h - 2; it[4] = 10 }
        var result: MatchResult? = null
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(2, 3, 1), aiDispatcher = StandardTestDispatcher(testScheduler), onFinished = { result = it })
        val job = session.start(this)
        runCurrent()
        session.roll(); advanceUntilIdle() // P0's last token goes Home: 1st place, but the match goes on
        val g = session.ui.value.game
        assertEquals(listOf(0), g.finishOrder.toList())
        assertTrue(g.phase != Phase.GAME_OVER)
        assertEquals(1, g.current, "the finished player is skipped")
        session.endNow(); advanceUntilIdle()
        val r = assertNotNull(result)
        assertEquals(listOf(0, 1, 2), r.finishOrder, "unfinished players ranked by progress")
        assertEquals(Phase.GAME_OVER, session.ui.value.game.phase)
        job.cancel()
    }

    @Test fun decisionsFeedAccuracyAndMissedCuts() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        // Red at progress 15 (cell 15) rolling 3 can cut the 2nd player's lone token on cell 18 (seat 2: progress 44).
        val tokens = IntArray(8) { BASE }.also { it[0] = 15; it[1] = 30; it[4] = 44 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(3, 3), aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        session.roll(); advanceUntilIdle()
        assertTrue(com.ludosovereign.ai.MoveAnalysis.captures(session.ui.value.game, 0))
        session.move(1); advanceUntilIdle() // passes up the cut
        val st = session.ui.value.stats.players[0]
        assertEquals(1, st.decisions)
        assertEquals(1, st.missedCuts)
        assertTrue(st.accuracyPercent!! < 100)
        job.cancel()
    }

    @Test fun podiumTitles() {
        assertEquals(listOf("Ultra", "Pro", "Max", "Aspirant", "Aspirant"), (1..5).map(Podium::title))
    }

    @Test fun progressionRanks() {
        assertEquals(Progression.Rank.PAWN, Progression.rankFor(0))
        assertEquals(Progression.Rank.KNIGHT, Progression.rankFor(1000))
        assertEquals(Progression.Rank.PRO_MAX, Progression.rankFor(50_000))
        assertTrue(Progression.xpFor(PlayerMatchStats(captures = 2), 1, 4) > Progression.xpFor(PlayerMatchStats(), 4, 4))
    }
}
