package com.ludosovereign.sound

import com.ludosovereign.audio.SoundEvent
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.snl.SnlEvent

/** Single entry point the screens call with each event batch: picks sounds + haptics. */
class GameFeedback(private val sound: SoundEngine, private val haptics: HapticController) {

    fun play(event: SoundEvent) {
        sound.play(event)
        haptics.perform(event)
    }

    /**
     * [state] is the position the events led to (seats never change mid-match); it lets a pawn that
     * lands on a star or coloured start square play the same safety chime as entering the home lane.
     */
    fun onLudoEvents(events: List<GameEvent>, humanWon: Boolean, state: GameState? = null) {
        ludoCues(events, humanWon, state).forEach(::play)
    }

    fun onSnlEvents(events: List<SnlEvent>) {
        for (e in events) when (e) {
            is SnlEvent.Rolled -> play(SoundEvent.DICE_LAND)
            is SnlEvent.LadderClimb -> play(SoundEvent.LADDER)
            is SnlEvent.SnakeSlide -> play(SoundEvent.SNAKE)
            is SnlEvent.TripleSixForfeit -> play(SoundEvent.TRIPLE_SIX)
            is SnlEvent.GameOver -> play(SoundEvent.WIN)
            else -> Unit
        }
    }

    fun step() = sound.play(SoundEvent.STEP).also { haptics.perform(SoundEvent.STEP) }
    fun diceTumble() = sound.play(SoundEvent.DICE_ROLL)
    fun tap() = play(SoundEvent.UI_TAP)

    /** Haptic-only touch acknowledgement for dice and pawn taps; the sound mix stays unchanged. */
    fun touch() = haptics.perform(SoundEvent.UI_TAP)

    companion object {
        /**
         * The cues one Ludo event batch plays, in order. Pure, so the mapping (moves, captures, safe
         * squares, home) is unit-tested once and holds for every sound pack: each pack renders its
         * own sound for the same [SoundEvent].
         */
        fun ludoCues(events: List<GameEvent>, humanWon: Boolean, state: GameState? = null): List<SoundEvent> {
            val cues = LinkedHashSet<SoundEvent>()
            for (e in events) when (e) {
                is GameEvent.DiceRolled -> { cues += SoundEvent.DICE_LAND; if (e.value == 6) cues += SoundEvent.SIX }
                is GameEvent.TripleSixForfeit -> cues += SoundEvent.TRIPLE_SIX
                is GameEvent.TokenReleased -> cues += SoundEvent.RELEASE
                is GameEvent.TokenEnteredHomeStretch -> cues += SoundEvent.HOME_STRETCH
                is GameEvent.TokenMoved -> if (state != null && landsSafe(state, e)) cues += SoundEvent.SAFE_SPOT
                is GameEvent.TokenReachedHome -> cues += SoundEvent.HOME
                is GameEvent.Captured -> cues += SoundEvent.CAPTURE
                is GameEvent.FortressFormed -> cues += SoundEvent.FORTRESS_LOCK
                is GameEvent.FortressStandoff -> cues += SoundEvent.FORTRESS_STANDOFF
                is GameEvent.TurnPassed -> cues += SoundEvent.TURN
                is GameEvent.GameOver -> cues += if (humanWon) SoundEvent.WIN else SoundEvent.LOSE
                else -> Unit
            }
            // Hop sounds are played per tile by the board animator.
            if (cues.size > 1) cues.remove(SoundEvent.STEP)
            if (SoundEvent.CAPTURE in cues || SoundEvent.FORTRESS_LOCK in cues) cues.remove(SoundEvent.TURN)
            // A capture or home arrival outranks the safe-square chime on the same landing.
            if (SoundEvent.CAPTURE in cues || SoundEvent.HOME in cues) cues.remove(SoundEvent.SAFE_SPOT)
            return cues.toList()
        }

        private fun landsSafe(s: GameState, e: GameEvent.TokenMoved): Boolean {
            val cell = s.topology.absoluteCell(s.seatOf(s.ownerOf(e.tokenId)), e.toProgress)
            return cell >= 0 && s.topology.isSafeCell(cell)
        }
    }
}
