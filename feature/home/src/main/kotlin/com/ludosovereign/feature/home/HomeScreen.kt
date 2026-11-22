package com.ludosovereign.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ludosovereign.data.SaveSlot
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.TokenGlyph
import com.ludosovereign.designsystem.drawToken
import com.ludosovereign.designsystem.tokenColor
import java.text.DateFormat
import java.util.Date

/** Clutter-free dashboard: Quick Resume first, then the two games, then everything else. */
@Composable
fun HomeScreen(
    slots: List<SaveSlot>,
    profileName: String,
    rankName: String,
    onResume: (Int) -> Unit,
    onDeleteSlot: (Int) -> Unit,
    onPlayLudo: () -> Unit,
    onPlaySnakes: () -> Unit,
    onStats: () -> Unit,
    onAudio: () -> Unit,
    onSettings: () -> Unit,
) {
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    val auto = slots.firstOrNull { it.slot == 0 && !it.finished }
    Scaffold(containerColor = theme.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(44.dp)) {
                    val r = size.minDimension * 0.22f
                    listOf(Offset(r, r), Offset(size.width - r, r), Offset(r, size.height - r), Offset(size.width - r, size.height - r))
                        .forEachIndexed { i, o -> drawToken(o, r, theme.tokenColor(i, cb), TokenGlyph.entries[i]) }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("$profileName · $rankName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
            }

            if (auto != null) {
                ElevatedCard(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.quick_resume), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(auto.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = { onResume(0) }) {
                            Icon(Icons.Filled.PlayArrow, null); Text(stringResource(R.string.resume))
                        }
                    }
                }
            }

            BigAction(stringResource(R.string.play_ludo), stringResource(R.string.play_ludo_sub), onPlayLudo)
            BigAction(stringResource(R.string.play_snakes), stringResource(R.string.play_snakes_sub), onPlaySnakes)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onStats, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.stats)) }
                FilledTonalButton(onClick = onAudio, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.audio_studio)) }
            }

            val manual = slots.filter { it.slot > 0 }
            if (manual.isNotEmpty()) {
                Text(stringResource(R.string.saved_games), style = MaterialTheme.typography.titleMedium)
                manual.forEach { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.slot_n, s.slot), fontWeight = FontWeight.SemiBold)
                                Text(s.summary, style = MaterialTheme.typography.bodySmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onDeleteSlot(s.slot) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                            IconButton(onClick = { onResume(s.slot) }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.resume)) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.offline_promise), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BigAction(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(
    themeId: String,
    colorblind: Boolean,
    autoMove: Boolean,
    animationSpeed: Float,
    language: String,
    onTheme: (String) -> Unit,
    onColorblind: (Boolean) -> Unit,
    onAutoMove: (Boolean) -> Unit,
    onAnimationSpeed: (Float) -> Unit,
    onLanguage: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = onBack) { Text(stringResource(R.string.done)) }
            }
            com.ludosovereign.designsystem.SectionCard(stringResource(R.string.theme)) {
                com.ludosovereign.designsystem.ChoiceRow(BoardThemes.ALL.map { it.id }, themeId, { BoardThemes.byId(it).name }, onTheme)
                com.ludosovereign.designsystem.LabeledSwitch(stringResource(R.string.colorblind), colorblind, onColorblind,
                    stringResource(R.string.colorblind_desc))
            }
            com.ludosovereign.designsystem.SectionCard(stringResource(R.string.gameplay)) {
                com.ludosovereign.designsystem.LabeledSwitch(stringResource(R.string.auto_move), autoMove, onAutoMove, stringResource(R.string.auto_move_desc))
                com.ludosovereign.designsystem.LabeledSlider(stringResource(R.string.animation_speed), (animationSpeed - 0.5f) / 1.5f,
                    { onAnimationSpeed(0.5f + it * 1.5f) }, "×%.1f".format(animationSpeed))
            }
            com.ludosovereign.designsystem.SectionCard(stringResource(R.string.language)) {
                com.ludosovereign.designsystem.ChoiceRow(LANGUAGES.map { it.first }, language,
                    { tag -> LANGUAGES.first { it.first == tag }.second }, onLanguage)
            }
        }
    }
}

/**
 * Language tags with their names written in that language ("" = system default). Adding a language
 * is a drop-in: add `values-<tag>/strings.xml` to each module and list the tag here.
 */
val LANGUAGES = listOf(
    "" to "System", "en" to "English", "hi" to "हिन्दी", "es" to "Español", "fr" to "Français", "ar" to "العربية",
)
