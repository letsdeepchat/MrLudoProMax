package com.ludosovereign.session

import com.ludosovereign.engine.MatchLog
import kotlinx.serialization.Serializable

@Serializable
enum class GameKind { LUDO, SNAKES_AND_LADDERS }

/**
 * What the session hands to persistence after every applied action. The Android data module stores
 * it in Room (slot 0 = auto-resume, 1..N = manual slots).
 */
data class Checkpoint(
    val kind: GameKind,
    val stateJson: String,
    val logJson: String?,
    val statsJson: String?,
    val summary: String,
    val finished: Boolean,
)

@Serializable
data class MatchResult(
    val kind: GameKind,
    val variant: String,
    val board: String,
    val players: List<String>,
    val humanFlags: List<Boolean>,
    val profileIds: List<Long?>,
    val finishOrder: List<Int>,
    val winnerTeam: Int,
    val stats: MatchStats?,
    val log: MatchLog?,
    val durationMillis: Long,
)

/** Implemented by `:core:data` (Room). In-memory fakes are used in tests. */
interface SaveRepository {
    suspend fun save(slot: Int, checkpoint: Checkpoint)
    suspend fun load(slot: Int): Checkpoint?
    suspend fun clear(slot: Int)

    companion object { const val AUTO_SLOT = 0 }
}

interface HistoryRepository {
    suspend fun record(result: MatchResult)
}
