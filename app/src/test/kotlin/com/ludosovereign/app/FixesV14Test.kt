package com.ludosovereign.app

import com.ludosovereign.audio.ProceduralSynth
import com.ludosovereign.audio.SoundEvent
import com.ludosovereign.audio.SoundPackRegistry
import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerSpec
import com.ludosovereign.engine.RuleSet
import com.ludosovereign.engine.geometry.BoardGeometry
import com.ludosovereign.feature.board.BoardView
import com.ludosovereign.feature.board.homeArrivals
import com.ludosovereign.feature.board.planMove
import com.ludosovereign.feature.board.tickScale
import com.ludosovereign.sound.GameFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** 1.4.0 board fixes (stacked-six hops, finish ticks) and the sound effect profiles. */
class FixesV14Test {
    private val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), listOf(PlayerSpec("A"), PlayerSpec("B")))

    @Test fun stackedSixHopsStartWhereThePreviousSixEnded() {
        val view = BoardView(BoardGeometry(BoardTopology.CLASSIC_4))
        var s = GameState(cfg, IntArray(8) { BASE }.also { it[0] = 0 }, 0, Phase.AWAITING_ROLL)
        var pawn = view.tokenPoint(0, 0, 0)
        for ((roll, expected) in listOf(6 to 6, 6 to 12, 5 to 17)) {
            val events = ArrayList<GameEvent>()
            s = LudoEngine.move(LudoEngine.roll(s, roll, events), 0, events)
            // The board's snapshot may be stale (an earlier animation was cut short): the plan ignores it.
            val plan = planMove(events)!!
            assertEquals(expected - roll, plan.from)
            assertEquals(expected, plan.to)
            val path = view.hopPath(0, plan.from, plan.to, 0)
            assertEquals("a roll of $roll is $roll hops", roll, path.size)
            val first = path.first()
            assertTrue("hop ${plan.from}→${plan.to} starts next to where the pawn stands",
                hypot(first.x - pawn.x, first.y - pawn.y) in 0.5f..1.8f)
            pawn = path.last()
            assertEquals(view.tokenPoint(0, expected, 0), pawn)
        }
    }

    @Test fun releaseIsPlannedFromTheYard() {
        val events = listOf(GameEvent.DiceRolled(0, 6), GameEvent.TokenReleased(2, 0))
        val plan = planMove(events)!!
        assertEquals(2, plan.tokenId); assertEquals(BASE, plan.from); assertEquals(0, plan.to)
        assertNull(planMove(listOf(GameEvent.DiceRolled(0, 3), GameEvent.NoLegalMoves(0, 3))))
    }

    @Test fun finishTicksShowForEveryHomePawnOnceTheLastOneLands() {
        val h = BoardTopology.CLASSIC_4.homeProgress
        val before = GameState(cfg, IntArray(8) { BASE }.also { it[0] = h; it[1] = h; it[2] = h; it[3] = h - 3 }, 0, Phase.AWAITING_ROLL)
        val events = ArrayList<GameEvent>()
        val after = LudoEngine.move(LudoEngine.roll(before, 3, events), 3, events)
        assertEquals(setOf(3), homeArrivals(events))
        val home = (0 until 4).map { after.tokens[it] == h }
        assertTrue(home.all { it })
        // Before the landing animation starts, only the arriving pawn's ✓ waits.
        assertEquals(listOf(1f, 1f, 1f, 0f), (0 until 4).map { tickScale(home[it], null, it in homeArrivals(events)) })
        // Mid-pop and after: the arriving ✓ animates in, then every ✓ is full.
        assertEquals(0.5f, tickScale(true, 0.5f, false))
        assertEquals(listOf(1f, 1f, 1f, 1f), (0 until 4).map { tickScale(home[it], null, false) })
        // A pawn not Home never shows a ✓, even if a stale pop value lingers.
        assertEquals(0f, tickScale(false, 0.7f, false))
    }

    @Test fun fiveDistinctSoundEffectProfiles() {
        val profiles = SoundPackRegistry.PROFILES
        assertEquals(5, profiles.size)
        assertEquals(listOf("Casual Chimes (King-style)", "Traditional", "Modern Casual", "Retro Arcade", "Minimalist"),
            profiles.map { it.displayName })
        assertEquals(profiles.size, profiles.map { it.style }.toSet().size)
        val synth = ProceduralSynth()
        // Moves, captures, safe squares and home finishes each sound different in every profile.
        for (cue in listOf(SoundEvent.STEP, SoundEvent.CAPTURE, SoundEvent.SAFE_SPOT, SoundEvent.HOME, SoundEvent.DICE_ROLL)) {
            val renders = profiles.map { synth.render(it, cue) }
            for (i in renders.indices) for (j in i + 1 until renders.size) {
                assertFalse("${profiles[i].id} and ${profiles[j].id} share the same $cue", renders[i].contentEquals(renders[j]))
            }
        }
        // Minimalist really is quieter.
        fun peak(id: String) = synth.render(SoundPackRegistry.byId(id), SoundEvent.CAPTURE).maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak("minimal") < peak("chimes") * 0.6)
    }

    @Test fun cuesMapMovesCapturesSafeSquaresAndHome() {
        // Pawn 0 lands on its own start square's neighbour star? Use a known safe cell: seat 0's start (cell 0) + 8.
        val topo = BoardTopology.CLASSIC_4
        val star = topo.safeCells.first { it != topo.startCell(0) && it < 20 }
        val s = GameState(cfg, IntArray(8) { BASE }.also { it[0] = star }, 1, Phase.AWAITING_ROLL)
        assertEquals(listOf(SoundEvent.SAFE_SPOT),
            GameFeedback.ludoCues(listOf(GameEvent.TokenMoved(0, star - 3, star)), humanWon = false, state = s))
        assertEquals(listOf(SoundEvent.CAPTURE),
            GameFeedback.ludoCues(listOf(GameEvent.TokenMoved(0, 5, 9), GameEvent.Captured(4, 0, 9, 30), GameEvent.TurnPassed(0, 1)), false, s))
        assertEquals(listOf(SoundEvent.HOME),
            GameFeedback.ludoCues(listOf(GameEvent.TokenMoved(0, 54, 56), GameEvent.TokenReachedHome(0)), false, s))
        assertEquals(listOf(SoundEvent.RELEASE), GameFeedback.ludoCues(listOf(GameEvent.TokenReleased(0, 0)), false, s))
        // Every profile renders every cue the mapping can produce.
        val synth = ProceduralSynth()
        for (p in SoundPackRegistry.ALL) for (e in SoundEvent.entries) assertTrue("${p.id}/$e", synth.render(p, e).isNotEmpty())
    }
}
