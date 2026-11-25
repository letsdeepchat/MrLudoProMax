package com.ludosovereign.engine

import com.ludosovereign.engine.BoardTopology.Companion.BASE
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

internal fun players(n: Int) = List(n) { PlayerSpec("P$it", colorIndex = it) }

internal fun config(
    n: Int = 2,
    topology: BoardTopology = BoardTopology.CLASSIC_4,
    rules: RuleSet = RuleSet.classic(),
) = MatchConfig.create(topology, rules, players(n))

/** Builds a state with every token in base except the given (tokenId to progress) overrides. */
internal fun stateOf(cfg: MatchConfig, vararg placed: Pair<Int, Int>, current: Int = 0, sixes: Int = 0): GameState {
    val tokens = IntArray(cfg.tokenCount) { BASE }
    placed.forEach { (id, p) -> tokens[id] = p }
    return GameState(cfg, tokens, current, Phase.AWAITING_ROLL, consecutiveSixes = sixes)
}

/** Progress a token of [seat] needs to stand on absolute [cell]. */
internal fun progressFor(topology: BoardTopology, seat: Int, cell: Int) =
    ((cell - topology.startCell(seat)) % topology.trackLength + topology.trackLength) % topology.trackLength

internal fun GameState.rollAndMove(dice: Int, tokenId: Int, events: MutableList<GameEvent>? = null): GameState {
    val rolled = LudoEngine.roll(this, dice, events)
    return LudoEngine.move(rolled, tokenId, events)
}

class TopologyTest {
    @Test fun classicGeometry() {
        val t = BoardTopology.CLASSIC_4
        assertEquals(52, t.trackLength)
        assertEquals(8, t.safeCells.size)
        assertEquals(56, t.homeProgress)
        assertEquals(51, t.firstStretchProgress)
        assertEquals(5, (t.firstStretchProgress until t.homeProgress).count())
        assertEquals(listOf(0, 8, 13, 21, 26, 34, 39, 47), t.safeCells)
        assertEquals((39 + 20) % 52, t.absoluteCell(3, 20))
        assertEquals(-1, t.absoluteCell(0, 51))
        assertEquals(-1, t.absoluteCell(0, BASE))
    }

    @Test fun hexGeometry() {
        val t = BoardTopology.HEX_6
        assertEquals(78, t.trackLength)
        assertEquals(12, t.safeCells.size)
        assertEquals(82, t.homeProgress)
        assertContentEquals(intArrayOf(0, 3), t.seatsFor(2))
        assertContentEquals(intArrayOf(0, 1, 2, 3, 4, 5), t.seatsFor(6))
    }

    @Test fun lastTrackCellIsJustBeforeOwnStart() {
        for (t in BoardTopology.entries) for (seat in 0 until t.seats) {
            val last = t.absoluteCell(seat, t.lastTrackProgress)
            assertEquals((t.startCell(seat) - 2 + t.trackLength) % t.trackLength, last)
        }
    }

    @Test fun classicRejectsSixPlayers() {
        assertFailsWith<IllegalArgumentException> { config(6, BoardTopology.CLASSIC_4) }
    }
}

class SetupTest {
    @Test fun classicStartsInBase() {
        val s = LudoEngine.newGame(config(4))
        assertTrue(s.tokens.all { it == BASE })
        assertEquals(Phase.AWAITING_ROLL, s.phase)
    }

    @Test fun quickModeStartsOneTokenOnBoard() {
        val s = LudoEngine.newGame(config(3, rules = RuleSet.quick()))
        for (p in 0 until 3) {
            assertEquals(0, s.tokens[p * 4])
            assertEquals(listOf(BASE, BASE, BASE), (1..3).map { s.tokens[p * 4 + it] })
        }
    }

    @Test fun teamUpTeamsAreOppositeCorners() {
        val c4 = config(4, rules = RuleSet.teamUp())
        assertContentEquals(intArrayOf(0, 1, 0, 1), c4.teams)
        val c6 = config(6, BoardTopology.HEX_6, RuleSet.teamUp())
        assertContentEquals(intArrayOf(0, 1, 0, 1, 0, 1), c6.teams)
        val hex4 = config(4, BoardTopology.HEX_6, RuleSet.teamUp())
        assertContentEquals(intArrayOf(0, 1, 3, 4), hex4.seats)
        assertContentEquals(intArrayOf(0, 1, 0, 1), hex4.teams)
    }
}

class BasicTurnTest {
    @Test fun nonSixWithAllInBasePasses() {
        val ev = mutableListOf<GameEvent>()
        val s = LudoEngine.roll(LudoEngine.newGame(config(2)), 4, ev)
        assertEquals(1, s.current)
        assertTrue(ev.any { it is GameEvent.NoLegalMoves })
        assertTrue(ev.any { it is GameEvent.TurnPassed })
    }

    @Test fun sixReleasesAndGrantsBonus() {
        val ev = mutableListOf<GameEvent>()
        val s = LudoEngine.newGame(config(2)).rollAndMove(6, 0, ev)
        assertEquals(0, s.tokens[0])
        assertEquals(0, s.current)
        assertEquals(1, s.consecutiveSixes)
        assertTrue(ev.contains(GameEvent.BonusTurn(0, BonusReason.SIX)))
        assertTrue(ev.any { it is GameEvent.TokenReleased })
    }

    @Test fun rushReleasesOnOne() {
        val s = LudoEngine.roll(LudoEngine.newGame(config(2, rules = RuleSet.rush())), 1)
        assertEquals(Phase.AWAITING_MOVE, s.phase)
        assertContentEquals(intArrayOf(0), LudoEngine.legalMoves(s))
        val classic = LudoEngine.roll(LudoEngine.newGame(config(2)), 1)
        assertEquals(1, classic.current)
    }

    @Test fun stackedTwinsCollapseToOneMove() {
        val cfg = config(2)
        val s = LudoEngine.roll(stateOf(cfg, 0 to 10, 1 to 10, 2 to 20), 3)
        assertContentEquals(intArrayOf(0, 2), LudoEngine.legalMoves(s))
        assertTrue(LudoEngine.isLegal(s, 1))
    }

    @Test fun exactRollRequiredForHome() {
        val cfg = config(2)
        val s = LudoEngine.roll(stateOf(cfg, 0 to 53), 4)
        assertEquals(1, s.current, "overshoot is illegal so the turn passes")
        val ok = LudoEngine.roll(stateOf(cfg, 0 to 53), 3)
        assertContentEquals(intArrayOf(0), LudoEngine.legalMoves(ok))
    }

    @Test fun homeGrantsBonus() {
        val cfg = config(2)
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(cfg, 0 to 53, 1 to 5).rollAndMove(3, 0, ev)
        assertEquals(56, s.tokens[0])
        assertEquals(0, s.current)
        assertTrue(ev.contains(GameEvent.BonusTurn(0, BonusReason.HOME)))
    }

    @Test fun enteringStretchEmitsEvent() {
        val ev = mutableListOf<GameEvent>()
        stateOf(config(2), 0 to 49).rollAndMove(4, 0, ev)
        assertTrue(ev.contains(GameEvent.TokenEnteredHomeStretch(0)))
    }

    @Test fun tripleSixForfeits() {
        val cfg = config(2)
        var s = stateOf(cfg, 0 to 0)
        s = s.rollAndMove(6, 0)
        s = s.rollAndMove(6, 0)
        assertEquals(2, s.consecutiveSixes)
        val ev = mutableListOf<GameEvent>()
        s = LudoEngine.roll(s, 6, ev)
        assertEquals(1, s.current)
        assertEquals(12, s.tokens[0], "third six never moves")
        assertTrue(ev.any { it is GameEvent.TripleSixForfeit })
        assertEquals(0, s.consecutiveSixes)
    }

    @Test fun illegalMoveRejected() {
        val s = LudoEngine.roll(stateOf(config(2), 0 to 10), 3)
        assertFailsWith<IllegalArgumentException> { LudoEngine.move(s, 1) }
        assertFailsWith<IllegalArgumentException> { LudoEngine.move(s, 4) }
    }
}

class CaptureAndFortressTest {
    private val cfg = config(2) // seats 0 and 2
    private val topo = BoardTopology.CLASSIC_4
    private fun p1At(cell: Int) = progressFor(topo, 2, cell)

    @Test fun singletonOnPlainTileIsCaptured() {
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(cfg, 0 to 10, 4 to p1At(10) - 3, current = 1).rollAndMove(3, 4, ev)
        assertEquals(BASE, s.tokens[0])
        assertEquals(1, s.current, "capture grants a bonus turn")
        assertTrue(ev.contains(GameEvent.BonusTurn(1, BonusReason.CAPTURE)))
        assertTrue(ev.any { it is GameEvent.Captured && it.victimTokenId == 0 && it.victimProgress == 10 })
    }

    @Test fun singletonOnSafeStarIsProtected() {
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(cfg, 0 to 8, 4 to p1At(8) - 3, current = 1).rollAndMove(3, 4, ev)
        assertEquals(8, s.tokens[0])
        assertEquals(0, s.current)
        assertTrue(ev.none { it is GameEvent.Captured })
    }

    @Test fun startTileIsSafe() {
        // Player 1 releases onto its own start (cell 26) where player 0 stands alone.
        val s = stateOf(cfg, 0 to 26, current = 1).rollAndMove(6, 4)
        assertEquals(26, s.tokens[0])
    }

    @Test fun fortressOnPlainTileIsImmune() {
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(cfg, 0 to 10, 1 to 10, 4 to p1At(10) - 3, current = 1).rollAndMove(3, 4, ev)
        assertEquals(10, s.tokens[0])
        assertEquals(10, s.tokens[1])
        assertEquals(p1At(10), s.tokens[4], "attacker coexists on the fortress tile")
        assertEquals(0, s.current, "no capture, no bonus")
        assertTrue(ev.contains(GameEvent.FortressStandoff(4, 10, 0)))
        assertEquals(3, s.tokensAt(10).size)
    }

    @Test fun threeStackFortressIsImmune() {
        val s = stateOf(cfg, 0 to 10, 1 to 10, 2 to 10, 4 to p1At(10) - 5, current = 1).rollAndMove(5, 4)
        assertEquals(listOf(10, 10, 10), (0..2).map { s.tokens[it] })
    }

    @Test fun movingOntoFriendFormsFortress() {
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(cfg, 0 to 10, 1 to 7).rollAndMove(3, 1, ev)
        assertTrue(ev.contains(GameEvent.FortressFormed(10, 0, 2)))
        assertTrue(s.isFortress(10, 0))
    }

    @Test fun breakingFortressExposesSingleton() {
        val ev = mutableListOf<GameEvent>()
        var s = stateOf(cfg, 0 to 10, 1 to 10, 4 to p1At(10) - 2)
        s = s.rollAndMove(4, 1, ev)
        assertTrue(ev.contains(GameEvent.FortressBroken(10, 0)))
        assertFalse(s.isFortress(10, 0))
        assertEquals(1, s.current)
        s = s.rollAndMove(2, 4)
        assertEquals(BASE, s.tokens[0], "remaining singleton is now capturable")
    }

    @Test fun brokenFortressOnSafeTileStaysProtected() {
        var s = stateOf(cfg, 0 to 8, 1 to 8, 4 to p1At(8) - 2)
        s = s.rollAndMove(4, 1)
        s = s.rollAndMove(2, 4)
        assertEquals(8, s.tokens[0])
    }

    @Test fun standoffThenBreakAllowsCaptureOfLeftover() {
        // P1 coexists with P0's fortress; P0 leaves one token behind; P1 second token captures it.
        var s = stateOf(cfg, 0 to 10, 1 to 10, 4 to p1At(10) - 3, 5 to p1At(10) - 1, current = 1)
        s = s.rollAndMove(3, 4)
        assertEquals(0, s.current)
        s = s.rollAndMove(2, 0)
        s = s.rollAndMove(1, 5)
        assertEquals(BASE, s.tokens[1])
        assertEquals(p1At(10), s.tokens[4], "P1's own coexisting token is untouched")
    }

    @Test fun fortressDisabledCapturesWholeStack() {
        val off = config(2, rules = RuleSet(fortressEnabled = false))
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(off, 0 to 10, 1 to 10, 4 to p1At(10) - 3, current = 1).rollAndMove(3, 4, ev)
        assertEquals(BASE, s.tokens[0])
        assertEquals(BASE, s.tokens[1])
        assertTrue(ev.none { it is GameEvent.FortressFormed || it is GameEvent.FortressStandoff })
    }

    @Test fun homeStretchIsImmune() {
        // Tokens in different players' stretches can never share a cell.
        val s = stateOf(cfg, 0 to 52, 4 to 52)
        assertEquals(-1, s.cellOf(0))
        assertEquals(-1, s.cellOf(4))
    }

    @Test fun captureInMultiplayerOnlyHitsSingletonTeams() {
        val c3 = config(3) // seats 0,1,2
        val p1 = { cell: Int -> progressFor(topo, 1, cell) }
        val p2 = { cell: Int -> progressFor(topo, 2, cell) }
        // Cell 30: P0 fortress (2) + P1 singleton; P2 lands there.
        val ev = mutableListOf<GameEvent>()
        val s = stateOf(c3, 0 to 30, 1 to 30, 4 to p1(30), 8 to p2(30) - 4, current = 2).rollAndMove(4, 8, ev)
        assertEquals(30, s.tokens[0]); assertEquals(30, s.tokens[1])
        assertEquals(BASE, s.tokens[4])
        assertTrue(ev.contains(GameEvent.FortressStandoff(8, 30, 0)))
        assertEquals(2, s.current)
    }
}

class TeamUpTest {
    private val cfg = config(4, rules = RuleSet.teamUp()) // P0(seat0)&P2(seat2) vs P1&P3
    private val topo = BoardTopology.CLASSIC_4

    @Test fun teammatesNeverCaptureAndFormFortress() {
        val ev = mutableListOf<GameEvent>()
        val p2At = progressFor(topo, 2, 30)
        val s = stateOf(cfg, 0 to 30, 8 to p2At - 2, current = 2).rollAndMove(2, 8, ev)
        assertEquals(30, s.tokens[0])
        assertTrue(ev.contains(GameEvent.FortressFormed(30, 0, 2)))
        assertTrue(s.isFortress(30, 0))
        assertEquals(3, s.current, "no bonus for stacking")
        // Enemy P3 lands on the team fortress: no capture.
        val p3 = progressFor(topo, 3, 30)
        val after = s.copy(tokens = s.tokens.copyOf().also { it[12] = p3 - 1 }).rollAndMove(1, 12)
        assertEquals(30, after.cellOf(0)); assertEquals(30, after.cellOf(8))
    }

    @Test fun finishedPlayerControlsTeammate() {
        val tokens = IntArray(16) { BASE }
        for (t in 0..3) tokens[t] = 56
        tokens[8] = 20
        val s = GameState(cfg, tokens, 0, Phase.AWAITING_ROLL, finishOrder = intArrayOf(0))
        assertContentEquals(intArrayOf(2), s.controllablePlayers())
        val rolled = LudoEngine.roll(s, 3)
        assertContentEquals(intArrayOf(8), LudoEngine.legalMoves(rolled))
        val moved = LudoEngine.move(rolled, 8)
        assertEquals(23, moved.tokens[8])
        assertEquals(1, moved.current)
    }

    @Test fun teamWinsWhenAllMembersFinish() {
        val tokens = IntArray(16) { BASE }
        for (t in 0..3) tokens[t] = 56
        for (t in 8..10) tokens[t] = 56
        tokens[11] = 55
        val s = GameState(cfg, tokens, 0, Phase.AWAITING_ROLL, finishOrder = intArrayOf(0))
        val ev = mutableListOf<GameEvent>()
        val end = s.rollAndMove(1, 11, ev)
        assertEquals(Phase.GAME_OVER, end.phase)
        assertEquals(0, end.winnerTeam)
        assertTrue(ev.any { it is GameEvent.GameOver })
    }

    @Test fun teamUpNeedsFourOrSix() {
        assertFailsWith<IllegalArgumentException> { config(3, rules = RuleSet.teamUp()) }
    }
}

class WinConditionTest {
    @Test fun quickModeEndsOnFirstTokenHome() {
        val cfg = config(2, rules = RuleSet.quick(tokensToWin = 1))
        val s = stateOf(cfg, 0 to 54).rollAndMove(2, 0)
        assertEquals(Phase.GAME_OVER, s.phase)
        assertEquals(0, s.winnerTeam)
        assertContentEquals(intArrayOf(0, 1), s.finishOrder)
    }

    @Test fun quickModeTwoTokens() {
        val cfg = config(2, rules = RuleSet.quick(tokensToWin = 2))
        val s = stateOf(cfg, 0 to 54, 1 to 30).rollAndMove(2, 0)
        assertEquals(Phase.AWAITING_ROLL, s.phase)
        assertEquals(0, s.current)
    }

    @Test fun playForAllPlacementsSkipsFinishedPlayers() {
        val cfg = config(3, rules = RuleSet(playForAllPlacements = true))
        val tokens = IntArray(12) { BASE }
        for (t in 0..2) tokens[t] = 56
        tokens[3] = 55
        tokens[4] = 10
        tokens[8] = 10
        val s = GameState(cfg, tokens, 0, Phase.AWAITING_ROLL).rollAndMove(1, 3)
        assertContentEquals(intArrayOf(0), s.finishOrder)
        assertEquals(Phase.AWAITING_ROLL, s.phase)
        assertEquals(1, s.current, "finished player loses its home bonus and is skipped")
        assertEquals(2, LudoEngine.nextPlayer(s, 1))
        assertEquals(1, LudoEngine.nextPlayer(s, 2))
    }
}

class CodecTest {
    @Test fun roundTripsState() {
        val s = stateOf(config(4, rules = RuleSet.rush()), 0 to 10, 5 to 20, current = 2, sixes = 1)
        assertEquals(s, StateCodec.decodeGame(StateCodec.encode(s)))
    }

    @Test fun matchLogReplaysExactly() {
        val cfg = config(4)
        val dice = SeededDice(7)
        var s = LudoEngine.newGame(cfg)
        var log = MatchLog(cfg)
        repeat(300) {
            if (s.phase == Phase.GAME_OVER) return@repeat
            val roll = GameAction.Roll(dice.roll())
            s = LudoEngine.reduce(s, roll); log = log.append(roll)
            if (s.phase == Phase.AWAITING_MOVE) {
                val mv = GameAction.MoveToken(LudoEngine.legalMoves(s).last())
                s = LudoEngine.reduce(s, mv); log = log.append(mv)
            }
        }
        val decoded = StateCodec.decodeLog(StateCodec.encode(log))
        assertEquals(s, decoded.replay())
    }
}

class StackedSixesTest {
    @Test fun everyStackedSixAddsSixTiles() {
        val cfg = config(2)
        var s = stateOf(cfg, 0 to 0)
        val events = ArrayList<GameEvent>()
        s = s.rollAndMove(6, 0, events)
        assertEquals(6, s.tokens[0]); assertEquals(0, s.current, "a six earns another roll")
        s = s.rollAndMove(6, 0, events)
        assertEquals(12, s.tokens[0]); assertEquals(2, s.consecutiveSixes)
        s = s.rollAndMove(4, 0, events)
        assertEquals(16, s.tokens[0]); assertEquals(1, s.current)
        val moved = events.filterIsInstance<GameEvent.TokenMoved>()
        assertEquals(listOf(0 to 6, 6 to 12, 12 to 16), moved.map { it.fromProgress to it.toProgress },
            "each move starts where the previous one ended")
    }

    @Test fun stackedSixesFromBaseReleaseThenAdvance() {
        val cfg = config(2)
        var s = stateOf(cfg)
        s = s.rollAndMove(6, 0)          // release onto the start square
        assertEquals(0, s.tokens[0])
        s = s.rollAndMove(6, 0)          // 0 -> 6
        assertEquals(6, s.tokens[0])
        s = s.rollAndMove(5, 0)          // 6 -> 11, turn passes
        assertEquals(11, s.tokens[0]); assertEquals(1, s.current)
    }

    @Test fun stackedSixesNearHomeNeverOverflow() {
        val cfg = config(2)
        val home = cfg.topology.homeProgress
        var s = stateOf(cfg, 0 to home - 13, 1 to 5)
        s = s.rollAndMove(6, 0)
        s = s.rollAndMove(6, 0)
        assertEquals(home - 1, s.tokens[0])
        val rolled = LudoEngine.roll(s.copy(consecutiveSixes = 0), 6)
        assertTrue(0 !in LudoEngine.legalMoves(rolled).toList(), "a six past Home is not legal")
        assertEquals(home - 1, rolled.tokens[0], "the pawn stays put; it never wraps or resets")
    }
}

