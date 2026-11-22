package com.ludosovereign.engine

import java.security.SecureRandom
import kotlin.random.Random

/** Source of dice values for fair play. The Unbeatable bot bypasses this with its own oracle. */
fun interface DiceSource {
    fun roll(): Int
}

/** Cryptographically uniform d6 (rejection-free: SecureRandom.nextInt(bound) is unbiased). */
class FairDice(private val rng: SecureRandom = SecureRandom()) : DiceSource {
    override fun roll(): Int = rng.nextInt(6) + 1
}

/** Reproducible dice for tests, simulations and replays. */
class SeededDice(seed: Long) : DiceSource {
    private val rng = Random(seed)
    override fun roll(): Int = rng.nextInt(1, 7)
}

/** Scripted dice for unit tests. */
class ScriptedDice(vararg values: Int) : DiceSource {
    private val queue = ArrayDeque(values.toList())
    override fun roll(): Int = queue.removeFirst()
}
