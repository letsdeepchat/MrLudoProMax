package com.ludosovereign.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Slot 0 is the auto-resume slot written after every action; 1..3 are manual save slots. */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val slot: Int,
    val kind: String,
    val stateJson: String,
    val logJson: String?,
    val statsJson: String?,
    val summary: String,
    val finished: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "match_history", indices = [Index("endedAt")])
data class MatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endedAt: Long,
    val kind: String,
    val variant: String,
    val board: String,
    /** JSON-encoded [com.ludosovereign.session.MatchResult]. */
    val resultJson: String,
    val winnerName: String,
    val humanWon: Boolean,
    val durationMillis: Long,
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val xp: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "profile_stats")
data class ProfileStatsEntity(
    @PrimaryKey val profileId: Long,
    val games: Int = 0,
    val wins: Int = 0,
    val captures: Int = 0,
    val timesCaptured: Int = 0,
    val fortressesBuilt: Int = 0,
    val tokensHome: Int = 0,
    val sixes: Int = 0,
    val flawlessWins: Int = 0,
    val unbeatableWins: Int = 0,
    val snlGames: Int = 0,
    val snlWins: Int = 0,
)

@Dao
interface SavedGameDao {
    @Upsert suspend fun upsert(game: SavedGameEntity)
    @Query("SELECT * FROM saved_games WHERE slot = :slot") suspend fun get(slot: Int): SavedGameEntity?
    @Query("SELECT * FROM saved_games ORDER BY slot") fun observeAll(): Flow<List<SavedGameEntity>>
    @Query("DELETE FROM saved_games WHERE slot = :slot") suspend fun delete(slot: Int)
}

@Dao
interface MatchHistoryDao {
    @Insert suspend fun insert(match: MatchHistoryEntity): Long
    @Query("SELECT * FROM match_history ORDER BY endedAt DESC LIMIT :limit") fun observeRecent(limit: Int = 200): Flow<List<MatchHistoryEntity>>
    @Query("SELECT * FROM match_history WHERE id = :id") suspend fun get(id: Long): MatchHistoryEntity?
    @Query("DELETE FROM match_history") suspend fun clear()
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM profiles ORDER BY id") fun observeProfiles(): Flow<List<ProfileEntity>>
    @Query("SELECT * FROM profiles ORDER BY id") suspend fun profiles(): List<ProfileEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertProfile(p: ProfileEntity): Long
    @Query("UPDATE profiles SET name = :name, colorIndex = :color WHERE id = :id") suspend fun renameProfile(id: Long, name: String, color: Int)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun deleteProfile(id: Long)
    @Query("UPDATE profiles SET xp = xp + :delta WHERE id = :id") suspend fun addXp(id: Long, delta: Int)

    @Query("SELECT * FROM profile_stats WHERE profileId = :id") suspend fun stats(id: Long): ProfileStatsEntity?
    @Query("SELECT * FROM profile_stats WHERE profileId = :id") fun observeStats(id: Long): Flow<ProfileStatsEntity?>
    @Upsert suspend fun upsertStats(s: ProfileStatsEntity)
    @Query("DELETE FROM profile_stats WHERE profileId = :id") suspend fun deleteStats(id: Long)

    @Transaction
    suspend fun deleteProfileCascade(id: Long) { deleteStats(id); deleteProfile(id) }
}

@Database(
    entities = [SavedGameEntity::class, MatchHistoryEntity::class, ProfileEntity::class, ProfileStatsEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LudoDatabase : RoomDatabase() {
    abstract fun savedGames(): SavedGameDao
    abstract fun history(): MatchHistoryDao
    abstract fun stats(): StatsDao

    companion object {
        fun create(context: Context): LudoDatabase =
            Room.databaseBuilder(context.applicationContext, LudoDatabase::class.java, "ludo_sovereign.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
