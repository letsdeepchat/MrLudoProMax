package com.ludosovereign.engine

import kotlinx.serialization.Serializable

/**
 * Geometry of a Ludo board, expressed purely in track indices (no rendering concerns).
 *
 * Every seat owns one 13-cell arm of the shared main track. A token's position is stored as
 * *progress relative to its own start cell*:
 *
 * | progress                         | meaning                                   |
 * |----------------------------------|-------------------------------------------|
 * | [BASE] (-1)                      | waiting in the yard                       |
 * | 0 .. [lastTrackProgress]         | on the shared main track                  |
 * | [firstStretchProgress] .. +4     | private home stretch (5 cells, immune)    |
 * | [homeProgress]                   | finished                                  |
 *
 * Classic: 52 track cells, a token walks 51 of them, 5 stretch cells, then Home (56 steps total).
 * Hex:     78 track cells, a token walks 77 of them, 5 stretch cells, then Home (82 steps total).
 */
@Serializable
enum class BoardTopology(val seats: Int) {
    CLASSIC_4(4),
    HEX_6(6);

    val armLength: Int get() = ARM_LENGTH
    val trackLength: Int get() = seats * ARM_LENGTH
    val lastTrackProgress: Int get() = trackLength - 2
    val firstStretchProgress: Int get() = trackLength - 1
    val homeStretchLength: Int get() = HOME_STRETCH
    val homeProgress: Int get() = lastTrackProgress + HOME_STRETCH + 1

    fun startCell(seat: Int): Int = seat * ARM_LENGTH

    /** The star safe tile on each arm, 8 cells after that arm's start tile. */
    fun starCell(seat: Int): Int = seat * ARM_LENGTH + STAR_OFFSET

    /** Absolute main-track cell for a token of [seat] at [progress], or -1 when not on the main track. */
    fun absoluteCell(seat: Int, progress: Int): Int =
        if (progress in 0..lastTrackProgress) (startCell(seat) + progress) % trackLength else -1

    fun isOnTrack(progress: Int): Boolean = progress in 0..lastTrackProgress
    fun isInHomeStretch(progress: Int): Boolean = progress in firstStretchProgress until homeProgress
    fun isHome(progress: Int): Boolean = progress == homeProgress

    private val safeMask: BooleanArray by lazy {
        BooleanArray(trackLength).also { arr ->
            for (s in 0 until seats) {
                arr[startCell(s)] = true
                arr[starCell(s)] = true
            }
        }
    }

    fun isSafeCell(cell: Int): Boolean = cell >= 0 && safeMask[cell]

    val safeCells: List<Int> get() = (0 until trackLength).filter { safeMask[it] }

    /**
     * Seats used for a given player count, spread as evenly as possible around the board
     * (2 players always sit opposite each other).
     */
    fun seatsFor(playerCount: Int): IntArray {
        require(playerCount in 2..seats) { "$this supports 2..$seats players, got $playerCount" }
        return when (this) {
            CLASSIC_4 -> when (playerCount) {
                2 -> intArrayOf(0, 2)
                3 -> intArrayOf(0, 1, 2)
                else -> intArrayOf(0, 1, 2, 3)
            }
            HEX_6 -> when (playerCount) {
                2 -> intArrayOf(0, 3)
                3 -> intArrayOf(0, 2, 4)
                4 -> intArrayOf(0, 1, 3, 4)
                5 -> intArrayOf(0, 1, 2, 3, 4)
                else -> intArrayOf(0, 1, 2, 3, 4, 5)
            }
        }
    }

    companion object {
        const val BASE = -1
        const val ARM_LENGTH = 13
        const val HOME_STRETCH = 5
        const val STAR_OFFSET = 8
    }
}
