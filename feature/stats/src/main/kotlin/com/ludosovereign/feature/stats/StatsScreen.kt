package com.ludosovereign.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ludosovereign.data.MatchHistoryEntity
import com.ludosovereign.data.ProfileEntity
import com.ludosovereign.data.ProfileStatsEntity
import com.ludosovereign.engine.GameEvent
import com.ludosovereign.engine.MatchLog
import com.ludosovereign.feature.board.BoardCanvas
import com.ludosovereign.session.Progression
import java.text.DateFormat
import java.util.Date

data class Achievement(val title: String, val description: String, val unlocked: Boolean)

/** Offline achievements derived purely from local profile stats. */
fun achievements(s: ProfileStatsEntity?, xp: Int): List<Achievement> {
    val st = s ?: ProfileStatsEntity(0)
    return listOf(
        Achievement("First Blood", "Cut an opponent's token", st.captures >= 1),
        Achievement("Executioner", "Make 100 cuts", st.captures >= 100),
        Achievement("Architect", "Build 10 Fortresses", st.fortressesBuilt >= 10),
        Achievement("Citadel", "Build 100 Fortresses", st.fortressesBuilt >= 100),
        Achievement("First Crown", "Win a match", st.wins >= 1),
        Achievement("Veteran", "Play 50 matches", st.games >= 50),
        Achievement("Untouchable", "Win without losing a single token", st.flawlessWins >= 1),
        Achievement("Giant Slayer", "Beat a lineup that includes the Unbeatable engine", st.unbeatableWins >= 1),
        Achievement("Snake Charmer", "Win at Snakes & Ladders", st.snlWins >= 1),
        Achievement("Sovereign", "Reach the Sovereign rank", Progression.rankFor(xp) == Progression.Rank.SOVEREIGN),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    profiles: List<ProfileEntity>,
    activeProfileId: Long,
    stats: ProfileStatsEntity?,
    history: List<MatchHistoryEntity>,
    onSelectProfile: (Long) -> Unit,
    onCreateProfile: (String) -> Unit,
    onOpenReplay: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var newProfile by remember { mutableStateOf<String?>(null) }
    val active = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.stats_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { IconButton(onClick = { newProfile = "" }) { Icon(Icons.Filled.Add, stringResource(R.string.new_profile)) } },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { p -> FilterChip(selected = p.id == active?.id, onClick = { onSelectProfile(p.id) }, label = { Text(p.name) }) }
                }
            }
            if (active != null) item {
                val rank = Progression.rankFor(active.xp)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(rank.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${active.xp} XP", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { Progression.progressToNext(active.xp) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            item {
                val s = stats ?: ProfileStatsEntity(0)
                val winPct = if (s.games > 0) 100 * s.wins / s.games else 0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatLine(stringResource(R.string.games), "${s.games}")
                        StatLine(stringResource(R.string.wins), "${s.wins} ($winPct%)")
                        StatLine(stringResource(R.string.losses), "${s.games - s.wins}")
                        StatLine(stringResource(R.string.captures), "${s.captures}")
                        StatLine(stringResource(R.string.times_captured), "${s.timesCaptured}")
                        StatLine(stringResource(R.string.fortresses), "${s.fortressesBuilt}")
                        StatLine(stringResource(R.string.tokens_home), "${s.tokensHome}")
                        StatLine(stringResource(R.string.snl_record), "${s.snlWins}/${s.snlGames}")
                    }
                }
            }
            item { Text(stringResource(R.string.achievements), style = MaterialTheme.typography.titleMedium) }
            items(achievements(stats, active?.xp ?: 0)) { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (a.unlocked) Icons.Filled.Check else Icons.Filled.Lock, null,
                        tint = if (a.unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(a.title, fontWeight = FontWeight.SemiBold)
                        Text(a.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium) }
            items(history, key = { it.id }) { h ->
                Card(onClick = { if (h.kind == "LUDO") onOpenReplay(h.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${h.variant.lowercase().replace('_', ' ')} · ${h.board.lowercase().replace('_', ' ')}", fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.won_by, h.winnerName), style = MaterialTheme.typography.bodySmall)
                        Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(h.endedAt)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    newProfile?.let { name ->
        AlertDialog(
            onDismissRequest = { newProfile = null },
            title = { Text(stringResource(R.string.new_profile)) },
            text = { OutlinedTextField(name, { newProfile = it.take(20) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreateProfile(name); newProfile = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Step through a finished match's move log on a read-only board. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(log: MatchLog, onBack: () -> Unit) {
    var step by remember { mutableFloatStateOf(log.actions.size.toFloat()) }
    val events = remember(step.toInt()) { mutableListOf<GameEvent>() }
    val state = remember(step.toInt()) { log.replay(step.toInt()) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.replay)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            BoardCanvas(state, events, step.toLong(), awaitingHuman = false, animationSpeed = 2f, onTokenTap = {}, onStep = {},
                modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.replay_step, step.toInt(), log.actions.size))
            Slider(value = step, onValueChange = { step = it }, valueRange = 0f..log.actions.size.toFloat().coerceAtLeast(1f),
                steps = 0)
        }
    }
}
