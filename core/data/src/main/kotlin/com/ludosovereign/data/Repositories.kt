package com.ludosovereign.data

import androidx.room.withTransaction
import com.ludosovereign.engine.StateCodec
import com.ludosovereign.session.Checkpoint
import com.ludosovereign.session.GameKind
import com.ludosovereign.session.HistoryRepository
import com.ludosovereign.session.MatchResult
import com.ludosovereign.session.Progression
import com.ludosovereign.session.SaveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSaveRepository(private val db: LudoDatabase, private val clock: () -> Long = System::currentTimeMillis) : SaveRepository {
    private val dao = db.savedGames()

    override suspend fun save(slot: Int, checkpoint: Checkpoint) = dao.upsert(
        SavedGameEntity(slot, checkpoint.kind.name, checkpoint.stateJson, checkpoint.logJson, checkpoint.statsJson,
            checkpoint.summary, checkpoint.finished, clock()),
    )

    override suspend fun load(slot: Int): Checkpoint? = dao.get(slot)?.toCheckpoint()
    override suspend fun clear(slot: Int) = dao.delete(slot)

    /** Copies the live auto-save into a manual slot. */
    suspend fun copyAutoSaveTo(slot: Int) {
        val auto = dao.get(SaveRepository.AUTO_SLOT) ?: return
        dao.upsert(auto.copy(slot = slot, updatedAt = clock()))
    }

    fun observeSlots(): Flow<List<SaveSlot>> = dao.observeAll().map { list -> list.map { SaveSlot(it.slot, it.summary, it.updatedAt, it.finished, GameKind.valueOf(it.kind)) } }

    private fun SavedGameEntity.toCheckpoint() = Checkpoint(GameKind.valueOf(kind), stateJson, logJson, statsJson, summary, finished)

    companion object { const val MANUAL_SLOTS = 3 }
}

data class SaveSlot(val slot: Int, val summary: String, val updatedAt: Long, val finished: Boolean, val kind: GameKind)

/**
 * Records finished matches and folds them into each human player's offline profile: stats,
 * XP and rank. Everything happens in one transaction so a crash can't half-apply a result.
 */
class RoomHistoryRepository(private val db: LudoDatabase, private val clock: () -> Long = System::currentTimeMillis) : HistoryRepository {

    override suspend fun record(result: MatchResult) {
        db.withTransaction {
            val winnerIdx = result.finishOrder.firstOrNull() ?: -1
            val winnerName = result.players.getOrNull(winnerIdx) ?: "?"
            db.history().insert(
                MatchHistoryEntity(
                    endedAt = clock(),
                    kind = result.kind.name,
                    variant = result.variant,
                    board = result.board,
                    resultJson = StateCodec.json.encodeToString(MatchResult.serializer(), result),
                    winnerName = winnerName,
                    humanWon = result.humanFlags.getOrElse(winnerIdx) { false },
                    durationMillis = result.durationMillis,
                ),
            )
            val stats = db.stats()
            result.profileIds.forEachIndexed { player, profileId ->
                if (profileId == null) return@forEachIndexed
                val place = result.finishOrder.indexOf(player) + 1
                val won = result.isWinner(player)
                val s = stats.stats(profileId) ?: ProfileStatsEntity(profileId)
                val ps = result.stats?.players?.getOrNull(player)
                val beatUnbeatable = won && result.opposedUnbeatable
                stats.upsertStats(
                    if (result.kind == GameKind.SNAKES_AND_LADDERS) {
                        s.copy(snlGames = s.snlGames + 1, snlWins = s.snlWins + if (won) 1 else 0)
                    } else s.copy(
                        games = s.games + 1,
                        wins = s.wins + if (won) 1 else 0,
                        captures = s.captures + (ps?.captures ?: 0),
                        timesCaptured = s.timesCaptured + (ps?.timesCaptured ?: 0),
                        fortressesBuilt = s.fortressesBuilt + (ps?.fortressesBuilt ?: 0),
                        tokensHome = s.tokensHome + (ps?.tokensHome ?: 0),
                        sixes = s.sixes + (ps?.sixes ?: 0),
                        flawlessWins = s.flawlessWins + if (won && ps?.timesCaptured == 0) 1 else 0,
                        unbeatableWins = s.unbeatableWins + if (beatUnbeatable) 1 else 0,
                    ),
                )
                val xp = if (ps != null) Progression.xpFor(ps, if (won) 1 else place.coerceAtLeast(2), result.players.size)
                else if (won) 60 else 20
                stats.addXp(profileId, xp)
            }
        }
    }

    fun observeHistory(): Flow<List<MatchHistoryEntity>> = db.history().observeRecent()

    suspend fun result(id: Long): MatchResult? = db.history().get(id)?.let {
        StateCodec.json.decodeFromString(MatchResult.serializer(), it.resultJson)
    }
}

class ProfileRepository(private val db: LudoDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val dao = db.stats()

    fun observeProfiles(): Flow<List<ProfileEntity>> = dao.observeProfiles()
    fun observeStats(id: Long): Flow<ProfileStatsEntity?> = dao.observeStats(id)

    suspend fun ensureDefaultProfile(defaultName: String): ProfileEntity {
        dao.profiles().firstOrNull()?.let { return it }
        val id = dao.insertProfile(ProfileEntity(name = defaultName, colorIndex = 0, createdAt = clock()))
        return dao.profiles().first { it.id == id }
    }

    suspend fun create(name: String, colorIndex: Int): Long =
        dao.insertProfile(ProfileEntity(name = name.trim().take(20), colorIndex = colorIndex, createdAt = clock()))

    suspend fun rename(id: Long, name: String, colorIndex: Int) = dao.renameProfile(id, name.trim().take(20), colorIndex)
    suspend fun delete(id: Long) = dao.deleteProfileCascade(id)
}
