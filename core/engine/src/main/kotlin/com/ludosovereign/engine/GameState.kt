package com.ludosovereign.engine

import kotlinx.serialization.Serializable

@Serializable
enum class Phase { AWAITING_ROLL, AWAITING_MOVE, GAME_OVER }

/**
 * Immutable snapshot of a Ludo match. Treat [tokens] and [finishOrder] as read-only; the engine
 * always copies before writing. Token ids are global: `player * tokensPerPlayer + localIndex`.
 */
@Serializable
class GameState(
    val config: MatchConfig,
    /** Progress per token id (see [BoardTopology] for the encoding). */
    val tokens: IntArray,
    /** Player index whose turn it is. */
    val current: Int,
    val phase: Phase,
    /** Dice value awaiting a move (1..6) in [Phase.AWAITING_MOVE], else 0. */
    val dice: Int = 0,
    val consecutiveSixes: Int = 0,
    /** Player indices in the order they finished (all their required tokens Home). */
    val finishOrder: IntArray = IntArray(0),
    /** Winning team id once [Phase.GAME_OVER], else -1. */
    val winnerTeam: Int = -1,
    /** Count of dice rolls taken so far; useful for logs and replays. */
    val rollCount: Int = 0,
) {
    val topology: BoardTopology get() = config.topology
    val rules: RuleSet get() = config.rules
    val tokensPerPlayer: Int get() = config.rules.tokensPerPlayer

    fun ownerOf(tokenId: Int): Int = tokenId / tokensPerPlayer
    fun seatOf(player: Int): Int = config.seats[player]
    fun teamOf(player: Int): Int = config.teams[player]

    fun progress(tokenId: Int): Int = tokens[tokenId]

    /** Absolute main-track cell of the token, or -1 when in base, home stretch or Home. */
    fun cellOf(tokenId: Int): Int = topology.absoluteCell(seatOf(ownerOf(tokenId)), tokens[tokenId])

    fun tokensHome(player: Int): Int {
        var n = 0
        val base = player * tokensPerPlayer
        for (t in base until base + tokensPerPlayer) if (tokens[t] == topology.homeProgress) n++
        return n
    }

    fun hasFinished(player: Int): Boolean = finishOrder.contains(player)

    /** Number of tokens belonging to [team] standing on absolute main-track [cell]. */
    fun teamCountAt(cell: Int, team: Int): Int {
        if (cell < 0) return 0
        var n = 0
        for (id in tokens.indices) {
            val owner = ownerOf(id)
            if (config.teams[owner] == team && cellOf(id) == cell) n++
        }
        return n
    }

    /** Token ids on an absolute main-track cell. */
    fun tokensAt(cell: Int): List<Int> = tokens.indices.filter { cellOf(it) == cell }

    /**
     * True when [cell] holds a Fortress for [team]: 2+ tokens of the same team on one main-track tile.
     */
    fun isFortress(cell: Int, team: Int): Boolean = rules.fortressEnabled && teamCountAt(cell, team) >= 2

    /** Players whose tokens the current roller may move (self, or teammates once self is done in Team Up). */
    fun controllablePlayers(player: Int = current): IntArray {
        if (!rules.teamUp || !hasFinished(player)) return intArrayOf(player)
        return config.teamMates(player).filter { !hasFinished(it) }.toIntArray()
    }

    fun copy(
        tokens: IntArray = this.tokens,
        current: Int = this.current,
        phase: Phase = this.phase,
        dice: Int = this.dice,
        consecutiveSixes: Int = this.consecutiveSixes,
        finishOrder: IntArray = this.finishOrder,
        winnerTeam: Int = this.winnerTeam,
        rollCount: Int = this.rollCount,
    ) = GameState(config, tokens, current, phase, dice, consecutiveSixes, finishOrder, winnerTeam, rollCount)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameState) return false
        return current == other.current && phase == other.phase && dice == other.dice &&
            consecutiveSixes == other.consecutiveSixes && winnerTeam == other.winnerTeam &&
            rollCount == other.rollCount && tokens.contentEquals(other.tokens) &&
            finishOrder.contentEquals(other.finishOrder) && config == other.config
    }

    override fun hashCode(): Int {
        var h = tokens.contentHashCode()
        h = 31 * h + current
        h = 31 * h + phase.hashCode()
        h = 31 * h + dice
        h = 31 * h + consecutiveSixes
        h = 31 * h + finishOrder.contentHashCode()
        return h
    }

    override fun toString(): String =
        "GameState(current=$current, phase=$phase, dice=$dice, sixes=$consecutiveSixes, " +
            "tokens=${tokens.contentToString()}, finished=${finishOrder.contentToString()}, winner=$winnerTeam)"
}
