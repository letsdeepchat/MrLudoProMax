package com.ludosovereign.engine

import com.ludosovereign.engine.snl.SnlState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A replayable Ludo match: the starting config plus every action. Folding [actions] through
 * [LudoEngine.reduce] reproduces the match exactly, which is how the move-log viewer works.
 */
@Serializable
data class MatchLog(val config: MatchConfig, val actions: List<GameAction> = emptyList()) {
    fun append(action: GameAction) = copy(actions = actions + action)

    fun replay(upTo: Int = actions.size, events: MutableList<GameEvent>? = null): GameState =
        actions.take(upTo).fold(LudoEngine.newGame(config)) { s, a -> LudoEngine.reduce(s, a, events) }
}

/** JSON (de)serialisation used by the Room save slots and match history. */
object StateCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "t"
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)
    fun decodeGame(text: String): GameState = json.decodeFromString(GameState.serializer(), text)

    fun encode(state: SnlState): String = json.encodeToString(SnlState.serializer(), state)
    fun decodeSnl(text: String): SnlState = json.decodeFromString(SnlState.serializer(), text)

    fun encode(log: MatchLog): String = json.encodeToString(MatchLog.serializer(), log)
    fun decodeLog(text: String): MatchLog = json.decodeFromString(MatchLog.serializer(), text)
}
