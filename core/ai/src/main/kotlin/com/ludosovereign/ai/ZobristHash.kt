package com.ludosovereign.ai

import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.Phase
import kotlin.random.Random

/**
 * Zobrist hashing for Ludo positions.
 *
 * Tokens of the same player are interchangeable, so token keys are indexed by (player, progress)
 * rather than token id and combined with *addition* instead of XOR: addition is order-independent
 * like XOR, but two tokens on the same square don't cancel out (k + k ≠ 0), so stacks/Fortresses
 * hash correctly and permuted-but-equivalent positions share one transposition-table entry.
 */
class ZobristHash(maxPlayers: Int = 6, maxProgress: Int = 82, seed: Long = 0x5EED_1DD0L) {
    private val rng = Random(seed)
    private val tokenKeys = Array(maxPlayers) { LongArray(maxProgress + 2) { rng.nextLong() } }
    private val currentKeys = LongArray(maxPlayers) { rng.nextLong() }
    private val finishedKeys = LongArray(maxPlayers) { rng.nextLong() }
    private val sixesKeys = LongArray(3) { rng.nextLong() }
    private val diceKeys = LongArray(7) { rng.nextLong() }
    private val phaseKeys = LongArray(Phase.entries.size) { rng.nextLong() }

    fun hash(s: GameState): Long {
        var h = 0L
        val tpp = s.tokensPerPlayer
        for (id in s.tokens.indices) h += tokenKeys[id / tpp][s.tokens[id] + 1]
        h = h xor currentKeys[s.current]
        for (p in s.finishOrder) h = h xor finishedKeys[p]
        h = h xor sixesKeys[s.consecutiveSixes.coerceAtMost(2)]
        h = h xor diceKeys[s.dice]
        h = h xor phaseKeys[s.phase.ordinal]
        return h
    }
}

/**
 * Fixed-size, always-replace-if-deeper transposition table backed by parallel primitive arrays
 * (no per-entry allocation, so it can live for the whole match without GC churn).
 */
class TranspositionTable(bits: Int = 17) {
    private val size = 1 shl bits
    private val mask = size - 1
    private val keys = LongArray(size)
    private val values = IntArray(size)
    private val depths = ByteArray(size)
    private val flags = ByteArray(size)
    private val moves = ByteArray(size)

    var hits = 0L; private set
    var probes = 0L; private set

    fun clear() {
        keys.fill(0); depths.fill(0); flags.fill(0); moves.fill(-1)
        hits = 0; probes = 0
    }

    /** Returns the stored entry index or -1. */
    fun probe(key: Long, depth: Int): Int {
        probes++
        val i = (key xor (key ushr 32)).toInt() and mask
        if (keys[i] == key && flags[i] != EMPTY && depths[i] >= depth) { hits++; return i }
        return -1
    }

    fun bestMove(key: Long): Int {
        val i = (key xor (key ushr 32)).toInt() and mask
        return if (keys[i] == key && flags[i] != EMPTY) moves[i].toInt() else -1
    }

    fun value(i: Int) = values[i]
    fun flag(i: Int) = flags[i]

    fun store(key: Long, depth: Int, value: Int, flag: Byte, move: Int = -1) {
        val i = (key xor (key ushr 32)).toInt() and mask
        if (keys[i] == key && depths[i] > depth) return
        keys[i] = key; values[i] = value; depths[i] = depth.toByte(); flags[i] = flag; moves[i] = move.toByte()
    }

    companion object {
        const val EMPTY: Byte = 0
        const val EXACT: Byte = 1
        const val LOWER: Byte = 2
        const val UPPER: Byte = 3
    }
}
