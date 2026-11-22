package com.ludosovereign.feature.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ludosovereign.ai.BotFactory
import com.ludosovereign.ai.LudoBot
import com.ludosovereign.data.RoomSaveRepository
import com.ludosovereign.engine.FairDice
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.LudoEngine
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.session.Checkpoint
import com.ludosovereign.session.HistoryRepository
import com.ludosovereign.session.LudoUiState
import com.ludosovereign.session.MatchSession
import com.ludosovereign.session.SaveRepository
import com.ludosovereign.session.SessionSettings
import com.ludosovereign.sound.GameFeedback
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the board screen should start: a fresh match or an auto-save/slot to resume. */
sealed interface LudoLaunch {
    data class New(val config: MatchConfig) : LudoLaunch
    data class Resume(val checkpoint: Checkpoint) : LudoLaunch
}

class LudoBoardViewModel(
    launch: LudoLaunch,
    settings: SessionSettings,
    private val saves: RoomSaveRepository,
    private val history: HistoryRepository,
    private val feedback: GameFeedback,
) : ViewModel() {

    private fun botsFor(state: GameState): Map<Int, LudoBot> =
        state.config.players.withIndex()
            .filter { it.value.kind == PlayerKind.BOT }
            .associate { (i, p) -> i to BotFactory.create(requireNotNull(p.botTier)) }

    private val session: MatchSession = when (launch) {
        is LudoLaunch.New -> {
            val g = LudoEngine.newGame(launch.config)
            MatchSession(g, bots = botsFor(g), dice = FairDice(), settings = settings,
                onCheckpoint = ::autoSave, onFinished = { history.record(it) })
        }
        is LudoLaunch.Resume -> MatchSession.restore(launch.checkpoint, ::botsFor, FairDice(), settings,
            onCheckpoint = ::autoSave, onFinished = { history.record(it) })
    }

    val ui: StateFlow<LudoUiState> = session.ui

    init {
        session.start(viewModelScope)
        viewModelScope.launch {
            var lastSeq = -1L
            var lastRolling = -1
            session.ui.collect { s ->
                if (s.rollingPlayer >= 0 && s.rollingPlayer != lastRolling) feedback.diceTumble()
                lastRolling = s.rollingPlayer
                if (s.eventSeq != lastSeq) {
                    lastSeq = s.eventSeq
                    val winnerTeam = s.lastEvents.filterIsInstance<GameEvent.GameOver>().firstOrNull()?.winnerTeam
                    val humanWon = winnerTeam != null && s.game.config.players.indices.any {
                        s.game.config.players[it].kind == PlayerKind.HUMAN && s.game.config.teams[it] == winnerTeam
                    }
                    feedback.onLudoEvents(s.lastEvents, humanWon)
                }
            }
        }
    }

    private suspend fun autoSave(c: Checkpoint) {
        if (c.finished) saves.clear(SaveRepository.AUTO_SLOT) else saves.save(SaveRepository.AUTO_SLOT, c)
    }

    fun roll() = session.roll()
    fun tapToken(tokenId: Int) = session.move(tokenId)
    fun undo() = session.undo()
    fun saveToSlot(slot: Int) = viewModelScope.launch { saves.copyAutoSaveTo(slot) }
    fun step() = feedback.step()

    override fun onCleared() { session.stop() }

    class Factory(
        private val launch: LudoLaunch,
        private val settings: SessionSettings,
        private val saves: RoomSaveRepository,
        private val history: HistoryRepository,
        private val feedback: GameFeedback,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LudoBoardViewModel(launch, settings, saves, history, feedback) as T
    }
}
