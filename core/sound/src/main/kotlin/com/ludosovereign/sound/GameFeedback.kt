package com.ludosovereign.sound

import com.ludosovereign.audio.SoundEvent
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.snl.SnlEvent

/** Single entry point the screens call with each event batch: picks sounds + haptics. */
class GameFeedback(private val sound: SoundEngine, private val haptics: HapticController) {

    fun play(event: SoundEvent) {
        sound.play(event)
        haptics.perform(event)
    }

    fun onLudoEvents(events: List<GameEvent>, humanWon: Boolean) {
        val cues = LinkedHashSet<SoundEvent>()
        for (e in events) when (e) {
            is GameEvent.DiceRolled -> { cues += SoundEvent.DICE_LAND; if (e.value == 6) cues += SoundEvent.SIX }
            is GameEvent.TripleSixForfeit -> cues += SoundEvent.TRIPLE_SIX
            is GameEvent.TokenReleased -> cues += SoundEvent.RELEASE
            is GameEvent.TokenMoved -> cues += SoundEvent.STEP
            is GameEvent.TokenEnteredHomeStretch -> cues += SoundEvent.HOME_STRETCH
            is GameEvent.TokenReachedHome -> cues += SoundEvent.HOME
            is GameEvent.Captured -> cues += SoundEvent.CAPTURE
            is GameEvent.FortressFormed -> cues += SoundEvent.FORTRESS_LOCK
            is GameEvent.FortressStandoff -> cues += SoundEvent.FORTRESS_STANDOFF
            is GameEvent.TurnPassed -> cues += SoundEvent.TURN
            is GameEvent.GameOver -> cues += if (humanWon) SoundEvent.WIN else SoundEvent.LOSE
            else -> Unit
        }
        // Step sounds are played per cell by the token animator; skip the batch-level one when richer cues exist.
        if (cues.size > 1) cues.remove(SoundEvent.STEP)
        if (SoundEvent.CAPTURE in cues || SoundEvent.FORTRESS_LOCK in cues) cues.remove(SoundEvent.TURN)
        cues.forEach(::play)
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
}
