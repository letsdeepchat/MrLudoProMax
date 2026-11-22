package com.ludosovereign.engine.snl

import com.ludosovereign.engine.PlayerSpec
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** A Snakes & Ladders board. Cells are numbered 1..[size]; 0 means "not yet on the board". */
@Serializable
data class SnlLayout(
    val id: String,
    val displayName: String,
    val size: Int,
    val ladders: Map<Int, Int>,
    val snakes: Map<Int, Int>,
) {
    val side: Int get() = sqrt(size.toDouble()).toInt()

    init {
        require(side * side == size) { "$id: size must be a square" }
        ladders.forEach { (b, t) -> require(t > b && b in 2 until size && t in 2..size) { "$id ladder $b->$t" } }
        snakes.forEach { (h, t) -> require(h > t && h in 2 until size && t >= 1) { "$id snake $h->$t" } }
        val starts = ladders.keys + snakes.keys
        require(starts.size == ladders.size + snakes.size) { "$id: a cell has both a snake and a ladder" }
        val ends = ladders.values + snakes.values
        require(ends.none { it in starts }) { "$id: chained jump" }
    }

    /** Where a token resting on [cell] ends up after any snake or ladder. */
    fun resolve(cell: Int): Int = ladders[cell] ?: snakes[cell] ?: cell

    /** Row (0 = bottom) and column (0 = left) of [cell] in boustrophedon order. */
    fun gridOf(cell: Int): Pair<Int, Int> {
        val i = cell - 1
        val row = i / side
        val col = if (row % 2 == 0) i % side else side - 1 - i % side
        return row to col
    }
}

object SnlLayouts {
    val CLASSIC_100 = SnlLayout(
        "classic_100", "Classic 100", 100,
        ladders = mapOf(4 to 25, 9 to 31, 21 to 42, 28 to 84, 51 to 67, 72 to 91, 80 to 99),
        snakes = mapOf(17 to 7, 54 to 34, 62 to 19, 64 to 60, 87 to 24, 93 to 73, 95 to 75, 98 to 79),
    )
    val ROYAL_PALACE = SnlLayout(
        "royal_palace", "Royal Palace", 100,
        ladders = mapOf(2 to 38, 15 to 26, 40 to 59, 47 to 76, 69 to 88, 78 to 97),
        snakes = mapOf(32 to 10, 49 to 11, 56 to 53, 83 to 61, 89 to 68, 92 to 51, 99 to 41),
    )
    val JUNGLE_RAPIDS = SnlLayout(
        "jungle_rapids", "Jungle Rapids", 100,
        ladders = mapOf(6 to 27, 19 to 58, 33 to 52, 45 to 65, 74 to 93),
        snakes = mapOf(25 to 5, 37 to 3, 48 to 30, 57 to 39, 70 to 50, 79 to 42, 86 to 67, 94 to 71, 97 to 82),
    )
    val DESERT_DUNES = SnlLayout(
        "desert_dunes", "Desert Dunes", 100,
        ladders = mapOf(5 to 16, 12 to 46, 24 to 63, 41 to 79, 59 to 81, 75 to 96),
        snakes = mapOf(29 to 9, 38 to 20, 53 to 33, 67 to 44, 85 to 57, 91 to 69, 98 to 62),
    )
    val MOUNTAIN_CLIMB = SnlLayout(
        "mountain_climb", "Mountain Climb", 100,
        ladders = mapOf(3 to 20, 11 to 35, 22 to 43, 31 to 49, 44 to 66, 58 to 77, 68 to 89, 82 to 95),
        snakes = mapOf(27 to 14, 52 to 29, 63 to 45, 73 to 53, 87 to 57, 96 to 76),
    )
    val MYSTIC_CAVES = SnlLayout(
        "mystic_caves", "Mystic Caves", 100,
        ladders = mapOf(7 to 29, 13 to 54, 34 to 48, 50 to 70, 61 to 83, 76 to 94),
        snakes = mapOf(23 to 2, 43 to 18, 59 to 37, 66 to 46, 81 to 62, 88 to 71, 93 to 65, 99 to 56),
    )
    val SPEED_64 = SnlLayout(
        "speed_64", "Speed 64", 64,
        ladders = mapOf(3 to 19, 10 to 28, 22 to 41, 35 to 54, 44 to 60),
        snakes = mapOf(26 to 8, 39 to 21, 48 to 30, 57 to 37, 62 to 45),
    )

    val ALL: List<SnlLayout> =
        listOf(CLASSIC_100, ROYAL_PALACE, JUNGLE_RAPIDS, DESERT_DUNES, MOUNTAIN_CLIMB, MYSTIC_CAVES, SPEED_64)

    fun byId(id: String): SnlLayout = ALL.first { it.id == id }
}

@Serializable
data class SnlRules(
    /** Overshooting the last cell wastes the roll (token stays) when true; when false it bounces back. */
    val exactFinish: Boolean = true,
    val bonusOnSix: Boolean = true,
    val tripleSixForfeit: Boolean = true,
    val playForAllPlacements: Boolean = false,
)

@Serializable
class SnlState(
    val layoutId: String,
    val players: List<PlayerSpec>,
    val rules: SnlRules,
    val positions: IntArray,
    val current: Int = 0,
    val consecutiveSixes: Int = 0,
    val finishOrder: IntArray = IntArray(0),
    val gameOver: Boolean = false,
    val rollCount: Int = 0,
) {
    val layout: SnlLayout get() = SnlLayouts.byId(layoutId)

    fun copy(
        positions: IntArray = this.positions,
        current: Int = this.current,
        consecutiveSixes: Int = this.consecutiveSixes,
        finishOrder: IntArray = this.finishOrder,
        gameOver: Boolean = this.gameOver,
        rollCount: Int = this.rollCount,
    ) = SnlState(layoutId, players, rules, positions, current, consecutiveSixes, finishOrder, gameOver, rollCount)

    override fun equals(other: Any?): Boolean = other is SnlState && layoutId == other.layoutId &&
        players == other.players && rules == other.rules && positions.contentEquals(other.positions) &&
        current == other.current && consecutiveSixes == other.consecutiveSixes &&
        finishOrder.contentEquals(other.finishOrder) && gameOver == other.gameOver && rollCount == other.rollCount

    override fun hashCode(): Int = positions.contentHashCode() * 31 + current
}

@Serializable
sealed interface SnlEvent {
    @Serializable data class Rolled(val player: Int, val value: Int) : SnlEvent
    @Serializable data class Moved(val player: Int, val from: Int, val to: Int) : SnlEvent
    @Serializable data class Overshoot(val player: Int, val value: Int) : SnlEvent
    @Serializable data class LadderClimb(val player: Int, val from: Int, val to: Int) : SnlEvent
    @Serializable data class SnakeSlide(val player: Int, val from: Int, val to: Int) : SnlEvent
    @Serializable data class TripleSixForfeit(val player: Int) : SnlEvent
    @Serializable data class BonusTurn(val player: Int) : SnlEvent
    @Serializable data class PlayerFinished(val player: Int, val place: Int) : SnlEvent
    @Serializable data class GameOver(val finishOrder: List<Int>) : SnlEvent
}

/** Pure reducer for Snakes & Ladders (2–6 players). */
object SnakesAndLaddersEngine {

    fun newGame(layout: SnlLayout, players: List<PlayerSpec>, rules: SnlRules = SnlRules()): SnlState {
        require(players.size in 2..6)
        return SnlState(layout.id, players, rules, IntArray(players.size))
    }

    fun roll(state: SnlState, value: Int, events: MutableList<SnlEvent>? = null): SnlState {
        require(!state.gameOver) { "game over" }
        require(value in 1..6)
        val p = state.current
        val layout = state.layout
        events?.add(SnlEvent.Rolled(p, value))
        val rolled = state.rollCount + 1

        if (value == 6 && state.rules.tripleSixForfeit && state.consecutiveSixes >= 2) {
            events?.add(SnlEvent.TripleSixForfeit(p))
            return state.copy(current = next(state, p), consecutiveSixes = 0, rollCount = rolled)
        }

        val positions = state.positions.copyOf()
        val from = positions[p]
        var to = from + value
        if (to > layout.size) {
            if (state.rules.exactFinish) {
                events?.add(SnlEvent.Overshoot(p, value))
                to = from
            } else {
                to = layout.size - (to - layout.size)
            }
        }
        if (to != from) events?.add(SnlEvent.Moved(p, from, to))
        val resolved = layout.resolve(to)
        if (resolved > to) events?.add(SnlEvent.LadderClimb(p, to, resolved))
        if (resolved < to) events?.add(SnlEvent.SnakeSlide(p, to, resolved))
        positions[p] = resolved

        var finishOrder = state.finishOrder
        if (resolved == layout.size) {
            finishOrder = finishOrder + p
            events?.add(SnlEvent.PlayerFinished(p, finishOrder.size))
            val remaining = state.players.size - finishOrder.size
            if (!state.rules.playForAllPlacements || remaining <= 1) {
                val rest = state.players.indices.filter { it !in finishOrder }.sortedByDescending { positions[it] }
                val order = finishOrder + rest.toIntArray()
                events?.add(SnlEvent.GameOver(order.toList()))
                return state.copy(positions = positions, finishOrder = order, gameOver = true, rollCount = rolled)
            }
            val s = state.copy(positions = positions, finishOrder = finishOrder, rollCount = rolled)
            return s.copy(current = next(s, p), consecutiveSixes = 0)
        }

        if (value == 6 && state.rules.bonusOnSix) {
            events?.add(SnlEvent.BonusTurn(p))
            return state.copy(positions = positions, consecutiveSixes = state.consecutiveSixes + 1, rollCount = rolled)
        }
        val s = state.copy(positions = positions, finishOrder = finishOrder, rollCount = rolled)
        return s.copy(current = next(s, p), consecutiveSixes = 0)
    }

    private fun next(state: SnlState, from: Int): Int {
        val n = state.players.size
        var p = from
        repeat(n) {
            p = (p + 1) % n
            if (!state.finishOrder.contains(p)) return p
        }
        return from
    }
}
