package com.ludosovereign.session

import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.MatchConfig
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
)

@Serializable
data class MatchStats(val players: List<PlayerMatchStats>) {
    companion object {
        fun empty(config: MatchConfig) = MatchStats(List(config.playerCount) { PlayerMatchStats() })
    }

    /**
     * Folds a batch of events. [before] is the state the batch was produced from; it resolves token
     * owners and which player built a Fortress.
     */
    fun fold(before: GameState, events: List<GameEvent>): MatchStats {
        val p = players.toMutableList()
        fun upd(i: Int, f: (PlayerMatchStats) -> PlayerMatchStats) { p[i] = f(p[i]) }
        var lastMover = before.current
        for (e in events) when (e) {
            is GameEvent.DiceRolled -> upd(e.player) { it.copy(rolls = it.rolls + 1, sixes = it.sixes + if (e.value == 6) 1 else 0) }
            is GameEvent.TokenMoved -> lastMover = before.ownerOf(e.tokenId)
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
        return MatchStats(p)
    }
}

/** Offline XP and rank badges. Purely local; nothing is ever uploaded. */
object Progression {
    enum class Rank(val minXp: Int) {
        PAWN(0), SQUIRE(300), KNIGHT(900), BARON(2000), DUKE(4000), MONARCH(7500), SOVEREIGN(12000);
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
