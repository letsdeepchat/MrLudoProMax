package com.ludosovereign.engine

import kotlinx.serialization.Serializable

/**
 * Side-effect-free facts emitted by the reducer. The UI turns them into animations, sounds and
 * haptics; the session layer folds them into stats and the replayable move log.
 */
@Serializable
sealed interface GameEvent {
    @Serializable data class DiceRolled(val player: Int, val value: Int) : GameEvent
    @Serializable data class TripleSixForfeit(val player: Int) : GameEvent
    @Serializable data class NoLegalMoves(val player: Int, val dice: Int) : GameEvent
    @Serializable data class TokenReleased(val tokenId: Int, val cell: Int) : GameEvent
    @Serializable data class TokenMoved(val tokenId: Int, val fromProgress: Int, val toProgress: Int) : GameEvent
    @Serializable data class TokenEnteredHomeStretch(val tokenId: Int) : GameEvent
    @Serializable data class TokenReachedHome(val tokenId: Int) : GameEvent
    @Serializable data class Captured(val victimTokenId: Int, val byTokenId: Int, val cell: Int, val victimProgress: Int) : GameEvent
    /** Mover landed on an enemy Fortress: both stay, split-cell layout, no capture, no bonus. */
    @Serializable data class FortressStandoff(val tokenId: Int, val cell: Int, val fortressTeam: Int) : GameEvent
    @Serializable data class FortressFormed(val cell: Int, val team: Int, val size: Int) : GameEvent
    @Serializable data class FortressBroken(val cell: Int, val team: Int) : GameEvent
    @Serializable data class BonusTurn(val player: Int, val reason: BonusReason) : GameEvent
    @Serializable data class TurnPassed(val from: Int, val to: Int) : GameEvent
    @Serializable data class PlayerFinished(val player: Int, val place: Int) : GameEvent
    @Serializable data class GameOver(val winnerTeam: Int, val finishOrder: List<Int>) : GameEvent
}

@Serializable
enum class BonusReason { SIX, CAPTURE, HOME }

/** Engine input. Dice values come from a [DiceSource] (or the Unbeatable dice oracle). */
@Serializable
sealed interface GameAction {
    @Serializable data class Roll(val value: Int) : GameAction
    @Serializable data class MoveToken(val tokenId: Int) : GameAction
}
