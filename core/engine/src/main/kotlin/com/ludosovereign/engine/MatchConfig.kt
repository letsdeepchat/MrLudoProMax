package com.ludosovereign.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Variant { CLASSIC, QUICK, RUSH, TEAM_UP }

@Serializable
enum class PlayerKind { HUMAN, BOT }

/** Bot strength. [UNBEATABLE] chooses its own dice and is therefore *not* a fair-dice opponent. */
@Serializable
enum class BotTier(val fairDice: Boolean) {
    CASUAL(true),
    GRANDMASTER(true),
    UNBEATABLE(false),
}

/**
 * All rule knobs the engine honours. Presets for each [Variant] live in the companion; every field
 * can still be overridden from the setup screen ("Rule Toggles").
 */
@Serializable
data class RuleSet(
    val variant: Variant = Variant.CLASSIC,
    val tokensPerPlayer: Int = 4,
    /** Number of a player's tokens that must reach Home to finish. */
    val tokensToWin: Int = 4,
    /** Tokens placed on the start tile at match start (Quick mode = 1). */
    val tokensStartingOnBoard: Int = 0,
    /** Bit i set => rolling i releases a token from base. Classic = 6 only, Rush = 1 or 6. */
    val releaseMask: Int = maskOf(6),
    val bonusOnSix: Boolean = true,
    val bonusOnCapture: Boolean = true,
    val bonusOnHome: Boolean = true,
    val tripleSixForfeit: Boolean = true,
    /** Custom Rule #1. When false, landing on any enemy stack captures all of it (old-school rule). */
    val fortressEnabled: Boolean = true,
    val teamUp: Boolean = false,
    /** 0 = no timer. Enforced by the UI/session layer, not by the reducer. */
    val turnTimerSeconds: Int = 0,
    /** false: match ends at the first finisher. true: keep playing until every placement is decided. */
    val playForAllPlacements: Boolean = false,
) {
    init {
        require(tokensPerPlayer in 1..4)
        require(tokensToWin in 1..tokensPerPlayer)
        require(tokensStartingOnBoard in 0..tokensPerPlayer)
        require(releaseMask and 0b1111110 != 0) { "at least one release value required" }
    }

    fun releasesOn(dice: Int): Boolean = releaseMask and (1 shl dice) != 0

    companion object {
        fun maskOf(vararg values: Int): Int = values.fold(0) { acc, v -> acc or (1 shl v) }

        fun classic() = RuleSet()

        fun quick(tokensToWin: Int = 1) = RuleSet(
            variant = Variant.QUICK,
            tokensToWin = tokensToWin,
            tokensStartingOnBoard = 1,
        )

        fun rush(turnTimerSeconds: Int = 10) = RuleSet(
            variant = Variant.RUSH,
            releaseMask = maskOf(1, 6),
            turnTimerSeconds = turnTimerSeconds,
        )

        fun teamUp() = RuleSet(variant = Variant.TEAM_UP, teamUp = true)
    }
}

@Serializable
data class PlayerSpec(
    val name: String,
    val kind: PlayerKind = PlayerKind.HUMAN,
    val botTier: BotTier? = null,
    /** Index into the active theme's token palette. */
    val colorIndex: Int = 0,
    /** Local profile id for stats; null for guests and bots. */
    val profileId: Long? = null,
)

/**
 * Immutable description of a match. Players are listed in turn order; [seats] maps player index to
 * board seat and [teams] maps player index to team id (each player is its own team unless Team Up).
 */
@Serializable
data class MatchConfig(
    val topology: BoardTopology,
    val rules: RuleSet,
    val players: List<PlayerSpec>,
    val seats: IntArray,
    val teams: IntArray,
) {
    val playerCount: Int get() = players.size
    val tokenCount: Int get() = players.size * rules.tokensPerPlayer
    val teamCount: Int get() = teams.distinct().size

    init {
        require(players.size in 2..topology.seats) { "player count ${players.size} not valid for $topology" }
        require(seats.size == players.size && teams.size == players.size)
        require(seats.distinct().size == seats.size) { "duplicate seats" }
        require(seats.all { it in 0 until topology.seats })
        if (rules.teamUp) require(teamCount == 2) { "Team Up needs exactly two teams" }
        else require(teamCount == players.size) { "teams must be unique when Team Up is off" }
    }

    fun teamMates(player: Int): List<Int> = (0 until playerCount).filter { it != player && teams[it] == teams[player] }

    override fun equals(other: Any?): Boolean = other is MatchConfig &&
        topology == other.topology && rules == other.rules && players == other.players &&
        seats.contentEquals(other.seats) && teams.contentEquals(other.teams)

    override fun hashCode(): Int =
        ((topology.hashCode() * 31 + rules.hashCode()) * 31 + players.hashCode()) * 31 + seats.contentHashCode()

    companion object {
        /**
         * Builds a config with seats spread evenly. For Team Up, opposite corners are teammates:
         * 2v2 on the classic board (seats 0+2 vs 1+3), 3v3 on the hex board (0+2+4 vs 1+3+5),
         * and 2v2 on the hex board (0+3 vs 1+4).
         */
        fun create(topology: BoardTopology, rules: RuleSet, players: List<PlayerSpec>): MatchConfig {
            val seats = topology.seatsFor(players.size)
            val teams = if (rules.teamUp) {
                when {
                    topology == BoardTopology.CLASSIC_4 && players.size == 4 -> IntArray(4) { seats[it] % 2 }
                    topology == BoardTopology.HEX_6 && players.size == 6 -> IntArray(6) { seats[it] % 2 }
                    topology == BoardTopology.HEX_6 && players.size == 4 -> IntArray(4) { seats[it] % 3 }
                    else -> throw IllegalArgumentException("Team Up supports 2v2 (4 players) or 3v3 (6 players on hex)")
                }
            } else IntArray(players.size) { it }
            return MatchConfig(topology, rules, players, seats, teams)
        }
    }
}
