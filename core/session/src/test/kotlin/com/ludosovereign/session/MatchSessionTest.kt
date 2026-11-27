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
import kotlinx.coroutines.launch
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
        // The boosted Auto-Bot may pick a six or a cut and earn bonus moves, but it always moves at least once.
        assertTrue(session.log.actions.count { it is com.ludosovereign.engine.GameAction.MoveToken } >= 1)
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

    // ---- 1.4.0 -----------------------------------------------------------------------------------

    @Test fun autoBotIsSingleUsePerMatch() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20; it[4] = 20 }
        var last: Checkpoint? = null
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(3, 2, 4, 1, 3, 2, 4, 1), aiDispatcher = StandardTestDispatcher(testScheduler),
            onCheckpoint = { last = it })
        val job = session.start(this)
        runCurrent()
        assertTrue(session.canEnableAuto(0))
        session.toggleAuto(0); advanceUntilIdle()           // first (and only) use
        assertTrue(0 in session.ui.value.autoPlayers)
        assertTrue(session.ui.value.autoSpent.isEmpty(), "still on, so not shown as spent yet")
        session.toggleAuto(0); advanceUntilIdle()           // manual control resumed
        assertFalse(0 in session.ui.value.autoPlayers)
        assertEquals(setOf(0), session.ui.value.autoSpent, "toggle is revoked for the rest of the match")
        assertFalse(session.canEnableAuto(0))
        session.toggleAuto(0); advanceUntilIdle()           // a second tap does nothing
        assertFalse(0 in session.ui.value.autoPlayers)
        assertTrue(session.canEnableAuto(1), "other players keep their own chance")
        job.cancel()
        // The spent chance survives save and resume.
        val restored = MatchSession.restore(assertNotNull(last), { emptyMap() }, SeededDice(1), SessionSettings(), {}, {})
        assertEquals(setOf(0), restored.ui.value.autoSpent)
        assertFalse(restored.canEnableAuto(0))
    }

    @Test fun autoBotUsesCasualLogicButAlwaysTakesTheCut() {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        // Token 0 at 15 cuts Friend's lone pawn on cell 18 with a 3; token 1 at 30 would only advance.
        val tokens = IntArray(8) { BASE }.also { it[0] = 15; it[1] = 30; it[4] = 44 }
        val s = GameState(cfg, tokens, 0, Phase.AWAITING_MOVE, dice = 3)
        repeat(50) { seed -> assertEquals(0, com.ludosovereign.ai.AutoPilotBot(Random(seed)).chooseMove(s), "seed $seed passed up the cut") }
        assertEquals(BotTier.CASUAL, com.ludosovereign.ai.AutoPilotBot().tier)
    }

    @Test fun stackedSixesAddUpInTheSession() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        // One pawn just out on its start square; the other three are already Home, so every roll auto-moves it.
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = IntArray(8) { BASE }.also { it[0] = 0; it[1] = h; it[2] = h; it[3] = h }
        val seen = ArrayList<Int>()
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(6, 6, 3), aiDispatcher = StandardTestDispatcher(testScheduler),
            onCheckpoint = { c -> seen += StateCodec.decodeGame(c.stateJson).tokens[0] })
        val job = session.start(this)
        runCurrent()
        repeat(3) { session.roll(); advanceUntilIdle() }    // only one pawn can move: each roll auto-moves
        val g = session.ui.value.game
        assertEquals(15, g.tokens[0], "6 + 6 + 3 from the start square")
        assertEquals(listOf(0, 6, 6, 12, 12, 15), seen, "every stacked six adds six tiles, never resets or loops back")
        val moves = session.log.actions.filterIsInstance<com.ludosovereign.engine.GameAction.MoveToken>()
        assertEquals(3, moves.size)
        assertEquals(1, g.current)
        job.cancel()
    }

    @Test fun botHoldsTheRolledFaceBeforeMoving() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(bot(BotTier.CASUAL), human))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20; it[4] = 30 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL),
            bots = mapOf(0 to BotFactory.create(BotTier.CASUAL, Random(1))), dice = ScriptedDice(4),
            aiDispatcher = StandardTestDispatcher(testScheduler), clock = { testScheduler.currentTime })
        val t = SessionSettings().timings
        val job = session.start(this)
        runCurrent()
        // The bot waits a beat before it picks up the dice.
        testScheduler.advanceTimeBy(t.botThinkMs - 10); runCurrent()
        assertEquals(-1, session.ui.value.rollingPlayer)
        testScheduler.advanceTimeBy(20); runCurrent()
        assertEquals(0, session.ui.value.rollingPlayer, "now tumbling")
        testScheduler.advanceTimeBy(t.rollMs); runCurrent()
        val rolledAt = testScheduler.currentTime
        assertEquals(Phase.AWAITING_MOVE, session.ui.value.game.phase)
        assertEquals(4, session.ui.value.game.dice, "the rolled number is on screen")
        // The face stays up for the mandatory 1 s reveal before any pawn moves.
        testScheduler.advanceTimeBy(AnimationTimings.DICE_REVEAL_MS - 30); runCurrent()
        assertEquals(Phase.AWAITING_MOVE, session.ui.value.game.phase, "still showing the number at +${testScheduler.currentTime - rolledAt} ms")
        advanceUntilIdle()
        assertEquals(1, session.ui.value.game.current)
        assertEquals(1000L, AnimationTimings.DICE_REVEAL_MS, "a full one-second pause")
        job.cancel()
    }

    @Test fun lastPawnHomeShowsAllFourTicksInTheSameUpdate() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic().copy(playForAllPlacements = true),
            listOf(bot(BotTier.CASUAL), human, human.copy(name = "C")))
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = IntArray(12) { BASE }.also { it[0] = h; it[1] = h; it[2] = h; it[3] = h - 2; it[4] = 10; it[8] = 12 }
        val updates = ArrayList<LudoUiState>()
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL),
            bots = mapOf(0 to BotFactory.create(BotTier.CASUAL, Random(1))), dice = ScriptedDice(2, 3, 1),
            aiDispatcher = StandardTestDispatcher(testScheduler))
        val collector = launch { session.ui.collect { updates += it } }
        val job = session.start(this)
        advanceUntilIdle()
        val landing = updates.first { u -> u.lastEvents.any { it is com.ludosovereign.engine.GameEvent.TokenReachedHome } }
        assertEquals(4, landing.game.tokensHome(0), "all four pawns are Home in the very update that lands the last one")
        assertTrue(landing.lastEvents.any { it is com.ludosovereign.engine.GameEvent.PlayerFinished && it.player == 0 })
        assertEquals(listOf(0), landing.game.finishOrder.toList())
        collector.cancel(); job.cancel()
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

    @Test fun animationSpeedCurve() {
        assertEquals(425f, AnimationTimings.hopMsFor(0.5f))
        assertEquals(235f, AnimationTimings.hopMsFor(1f))
        assertEquals(90f, AnimationTimings.hopMsFor(2f))
        assertEquals(1f, AnimationTimings.speedForSlider(0.5f), "slider middle is ×1.0")
        for (p in listOf(0f, 0.25f, 0.5f, 0.8f, 1f)) assertEquals(p, AnimationTimings.sliderForSpeed(AnimationTimings.speedForSlider(p)), 1e-5f)
        val slow = SessionSettings(animationSpeed = 0.5f).timings
        val fast = SessionSettings(animationSpeed = 2f).timings
        assertEquals(425L, slow.hopMs)
        assertEquals(90L, fast.hopMs)
        assertTrue(slow.exitMs > fast.exitMs && slow.retreatMs > fast.retreatMs && slow.rollMs > fast.rollMs)
    }

    @Test fun speedChangeAppliesToRunningMatch() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val session = MatchSession(LudoEngine.newGame(cfg), bots = emptyMap(), dice = ScriptedDice(3),
            aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        assertEquals(235L, session.ui.value.timings.hopMs)
        session.setAnimationSpeed(0.5f)
        assertEquals(425L, session.ui.value.timings.hopMs)
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

    // ---- 1.5.0: dice pacing, guaranteed sixes, boosted Auto-Bot ----------------------------------

    @Test fun guaranteedSixesFollowTheTurnScheduleInALiveMatch() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(),
            listOf(bot(BotTier.CASUAL), bot(BotTier.CASUAL)))
        val bots = cfg.players.indices.associateWith { BotFactory.create(BotTier.CASUAL, Random(it), budgetMs = 3) }
        val session = MatchSession(LudoEngine.newGame(cfg), bots = bots, dice = SeededDice(11),
            settings = SessionSettings(guaranteedSixes = true), aiDispatcher = StandardTestDispatcher(testScheduler),
            scheduleRandom = Random(5))
        val job = session.start(this)
        // Run long enough for both players to pass turn 8.
        while (session.ui.value.stats.sixSchedule.turns.let { it.size < 2 || it.min() < 9 }) {
            testScheduler.advanceTimeBy(200); runCurrent()
        }
        job.cancel()
        // Rebuild per-player turn numbers from the log and check the rule.
        val replay = session.log.actions.filterIsInstance<com.ludosovereign.engine.GameAction.Roll>().map { it.value }
        var g = LudoEngine.newGame(cfg)
        val turn = IntArray(2); var last = -1
        val sixTurns = Array(2) { ArrayList<Int>() }
        for (a in session.log.actions) {
            if (a is com.ludosovereign.engine.GameAction.Roll) {
                if (g.current != last) { turn[g.current]++; last = g.current }
                if (a.value == 6) sixTurns[g.current] += turn[g.current]
            }
            g = LudoEngine.reduce(g, a)
        }
        assertTrue(replay.isNotEmpty())
        for (p in 0..1) {
            assertTrue(sixTurns[p].none { it <= 5 }, "player $p: six in turns 1-5 ${sixTurns[p]}")
            assertTrue(sixTurns[p].any { it in 6..8 }, "player $p: no six in turns 6-8 ${sixTurns[p]}")
        }
    }

    @Test fun humanRollHoldsTheFaceForOneSecond() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, human.copy(name = "Friend")))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL), bots = emptyMap(),
            dice = ScriptedDice(3), aiDispatcher = StandardTestDispatcher(testScheduler))
        val job = session.start(this)
        runCurrent()
        session.roll(); runCurrent()
        testScheduler.advanceTimeBy(SessionSettings().timings.rollMs + 1); runCurrent()
        assertEquals(3, session.ui.value.game.dice, "the face is on screen")
        testScheduler.advanceTimeBy(AnimationTimings.DICE_REVEAL_MS - 20); runCurrent()
        assertEquals(TurnPhase.NEXT_TURN, session.ui.value.turnPhase, "no pawn may be picked during the 1 s hold")
        testScheduler.advanceTimeBy(40); runCurrent()
        assertEquals(TurnPhase.SELECTING_TOKEN, session.ui.value.turnPhase)
        job.cancel()
    }

    @Test fun autoBotSwitchesOffAfterOneHundredSeconds() = runTest {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, bot(BotTier.CASUAL)))
        val tokens = IntArray(8) { BASE }.also { it[0] = 10; it[1] = 20; it[4] = 20 }
        val session = MatchSession(GameState(cfg, tokens, 0, Phase.AWAITING_ROLL),
            bots = mapOf(1 to BotFactory.create(BotTier.CASUAL, Random(1), budgetMs = 3)), dice = SeededDice(3),
            aiDispatcher = StandardTestDispatcher(testScheduler), clock = { testScheduler.currentTime })
        val job = session.start(this)
        runCurrent()
        session.toggleAuto(0); runCurrent()
        assertEquals(MatchSession.AUTO_BOT_LIMIT_MS, session.ui.value.autoEndsAtMs.getValue(0) - testScheduler.currentTime)
        testScheduler.advanceTimeBy(MatchSession.AUTO_BOT_LIMIT_MS - 5_000); runCurrent()
        assertTrue(0 in session.ui.value.autoPlayers, "still playing before 100 s")
        // Past the limit the Auto-Bot hands control back at the next decision and stays spent.
        var waited = 0L
        while (!session.ui.value.awaitingHuman && waited < 30_000) { testScheduler.advanceTimeBy(100); runCurrent(); waited += 100 }
        assertFalse(0 in session.ui.value.autoPlayers)
        assertTrue(session.ui.value.awaitingHuman)
        assertEquals(setOf(0), session.ui.value.autoSpent)
        assertFalse(session.canEnableAuto(0))
        job.cancel()
    }

    @Test fun boostedAutoBotPicksTheCuttingFaceAgainstAnyOpponent() {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(human, bot(BotTier.CASUAL)))
        // Token 0 on cell 15; the bot's lone pawn sits 4 ahead on cell 19 (seat 2 progress 45 = cell 19).
        val tokens = IntArray(8) { BASE }.also { it[0] = 15; it[4] = 45 }
        val s = GameState(cfg, tokens, 0, Phase.AWAITING_ROLL)
        repeat(20) { seed ->
            assertEquals(4, com.ludosovereign.ai.UnbeatableEngine(random = Random(seed), boosted = true).chooseDice(s))
        }
        // Without the boost the Unbeatable bot only aims at humans, so this bot victim is not targeted on purpose.
        assertTrue((0 until 20).map { com.ludosovereign.ai.UnbeatableEngine(random = Random(it)).chooseDice(s) }.toSet().size > 1)
    }
}
