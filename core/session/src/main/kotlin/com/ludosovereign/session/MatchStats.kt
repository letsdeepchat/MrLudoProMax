package com.ludosovereign.session

import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.SixScheduleState
import kotlinx.serialization.Serializable

/** Per-player counters for one match, folded from [GameEvent]s. */
@Serializable
data class PlayerMatchStats(
    val rolls: Int = 0,
    val sixes: Int = 0,
    val captures: Int = 0,
    val timesCaptured: Int = 0,
    val fortressesBuilt: Int = 0,
    val fortressStandoffs: Int = 0,
    val tokensHome: Int = 0,
    /** Landings on a safe square (star or coloured start square) by a moved token. */
    val safeLandings: Int = 0,
    val bonusTurns: Int = 0,
    val tripleSixForfeits: Int = 0,
    /** Turns with a capturing move available where another move was played. */
    val missedCuts: Int = 0,
    /** Turns with 2+ distinct legal moves (the ones accuracy is measured on). */
    val decisions: Int = 0,
    /** Sum over [decisions] of how close the chosen move was to the engine's top move (0..1 each). */
    val accuracySum: Double = 0.0,
    /** The player handed a turn to the Auto-Bot this match; it is single-use, so the toggle is now spent. */
    val autoBotUsed: Boolean = false,
) {
    /** Accuracy in percent, or null when the player never had a real choice. */
    val accuracyPercent: Int? get() = if (decisions == 0) null else Math.round(accuracySum * 100.0 / decisions).toInt()
}

@Serializable
data class MatchStats(
    val players: List<PlayerMatchStats>,
    /** Turn ledger for the guaranteed-six pacing; saved with the stats so it survives save and resume. */
    val sixSchedule: SixScheduleState = SixScheduleState(),
) {
    companion object {
        fun empty(config: MatchConfig) = MatchStats(List(config.playerCount) { PlayerMatchStats() })
    }

    /**
     * Folds a batch of events. [before] is the state the batch was produced from; it resolves token
     * owners and which player built a Fortress.
     */
    /** Records one decision turn of [player]: its accuracy (0..1) and whether a capture was passed up. */
    fun recordDecision(player: Int, accuracy: Double, missedCut: Boolean): MatchStats {
        val p = players.toMutableList()
        p[player] = p[player].let {
            it.copy(decisions = it.decisions + 1, accuracySum = it.accuracySum + accuracy.coerceIn(0.0, 1.0),
                missedCuts = it.missedCuts + if (missedCut) 1 else 0)
        }
        return copy(players = p)
    }

    fun markAutoBotUsed(player: Int): MatchStats =
        copy(players = players.mapIndexed { i, p -> if (i == player) p.copy(autoBotUsed = true) else p })

    fun fold(before: GameState, events: List<GameEvent>): MatchStats {
        val p = players.toMutableList()
        fun upd(i: Int, f: (PlayerMatchStats) -> PlayerMatchStats) { p[i] = f(p[i]) }
        var lastMover = before.current
        for (e in events) when (e) {
            is GameEvent.DiceRolled -> upd(e.player) { it.copy(rolls = it.rolls + 1, sixes = it.sixes + if (e.value == 6) 1 else 0) }
            is GameEvent.TokenMoved -> {
                lastMover = before.ownerOf(e.tokenId)
                if (before.topology.isSafeCell(before.topology.absoluteCell(before.seatOf(lastMover), e.toProgress))) {
                    upd(lastMover) { it.copy(safeLandings = it.safeLandings + 1) }
                }
            }
            is GameEvent.BonusTurn -> upd(e.player) { it.copy(bonusTurns = it.bonusTurns + 1) }
            is GameEvent.TripleSixForfeit -> upd(e.player) { it.copy(tripleSixForfeits = it.tripleSixForfeits + 1) }
            is GameEvent.TokenReleased -> lastMover = before.ownerOf(e.tokenId)
            is GameEvent.Captured -> {
                upd(before.ownerOf(e.byTokenId)) { it.copy(captures = it.captures + 1) }
                upd(before.ownerOf(e.victimTokenId)) { it.copy(timesCaptured = it.timesCaptured + 1) }
            }
            is GameEvent.FortressFormed -> upd(lastMover) { it.copy(fortressesBuilt = it.fortressesBuilt + 1) }
            is GameEvent.FortressStandoff -> upd(before.ownerOf(e.tokenId)) { it.copy(fortressStandoffs = it.fortressStandoffs + 1) }
            is GameEvent.TokenReachedHome -> upd(before.ownerOf(e.tokenId)) { it.copy(tokensHome = it.tokensHome + 1) }
            else -> Unit
        }
        return copy(players = p)
    }
}

/** Offline XP and rank badges. Purely local; nothing is ever uploaded. */
object Progression {
    enum class Rank(val minXp: Int, val title: String) {
        PAWN(0, "Pawn"), SQUIRE(300, "Squire"), KNIGHT(900, "Knight"), BARON(2000, "Baron"), DUKE(4000, "Duke"),
        MONARCH(7500, "Monarch"), ULTRA(12000, "Ultra");
    }

    fun xpFor(stats: PlayerMatchStats, place: Int, playerCount: Int): Int {
        val placement = when (place) {
            1 -> 120
            else -> (60 * (playerCount - place) / (playerCount - 1).coerceAtLeast(1))
        }
        return 20 + placement + stats.captures * 15 + stats.fortressesBuilt * 10 + stats.tokensHome * 10
    }

    fun rankFor(xp: Int): Rank = Rank.entries.last { xp >= it.minXp }

    /** 0..1 progress toward the next rank (1 at max rank). */
    fun progressToNext(xp: Int): Float {
        val r = rankFor(xp)
        val next = Rank.entries.getOrNull(r.ordinal + 1) ?: return 1f
        return (xp - r.minXp).toFloat() / (next.minXp - r.minXp)
    }
}
