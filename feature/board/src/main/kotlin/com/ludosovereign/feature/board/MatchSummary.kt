package com.ludosovereign.feature.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludosovereign.designsystem.CtaButton
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.RobotIcon
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.GameState
import com.ludosovereign.session.MatchResult
import com.ludosovereign.session.PlayerMatchStats
import com.ludosovereign.session.Podium
import androidx.compose.material3.Icon

/**
 * Full-screen post-match summary: podium standings (Ultra / Pro / Max / Aspirant), a tactical stats
 * table comparing every player, and each player's Accuracy % against the local engine's top move.
 */
@Composable
fun MatchSummary(result: MatchResult, game: GameState, onDone: () -> Unit) {
    val theme = LocalBoardTheme.current
    val ui = theme.ui
    val cb = LocalColorblind.current
    val colors = game.config.players.map { theme.tokenColor(it.colorIndex, cb) }
    val stats = result.stats?.players ?: List(result.players.size) { PlayerMatchStats() }
    val order = result.finishOrder

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = false) {}
            .semantics { contentDescription = "Match statistics" },
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ui.card, contentColor = ui.onCard),
            border = BorderStroke(2.dp, ui.cardBorder),
            modifier = Modifier.systemBarsPadding().padding(horizontal = 12.dp, vertical = 16.dp).fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(stringResource(R.string.match_stats), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

                    // ---- Final standings ------------------------------------------------------------
                    Section("Final standings") {
                        order.forEachIndexed { i, p ->
                            val place = i + 1
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                val (bg, fg) = podiumColors(place)
                                Box(Modifier.size(34.dp).background(bg, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                    Text("$place", color = fg, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.size(12.dp).background(colors[p], CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(result.players[p], fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                                if (result.humanFlags.getOrNull(p) == false) {
                                    Icon(RobotIcon, "Bot", Modifier.size(16.dp), tint = ui.onCard.copy(alpha = 0.6f))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(Podium.title(place).uppercase(), fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = fg,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.background(bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // ---- Tactical stats table -------------------------------------------------------
                    Section("Tactical stats") {
                        val cols = order
                        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                            Spacer(Modifier.weight(1.5f))
                            cols.forEach { p ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(12.dp).background(colors[p], CircleShape))
                                    Text(result.players[p], style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        HorizontalDivider(color = ui.onCard.copy(alpha = 0.15f))
                        val rows: List<Pair<String, (PlayerMatchStats) -> String>> = listOf(
                            "Cuts made" to { s -> "${s.captures}" },
                            "Times cut" to { s -> "${s.timesCaptured}" },
                            "Missed cuts" to { s -> "${s.missedCuts}" },
                            "Safe landings" to { s -> "${s.safeLandings}" },
                            "Sixes rolled" to { s -> "${s.sixes}" },
                            "Bonus turns" to { s -> "${s.bonusTurns}" },
                            "3-six forfeits" to { s -> "${s.tripleSixForfeits}" },
                            "Fortresses" to { s -> "${s.fortressesBuilt}" },
                        )
                        rows.forEachIndexed { k, (label, value) ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (k % 2 == 0) ui.onCard.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f).padding(start = 6.dp), maxLines = 1)
                                cols.forEach { p ->
                                    Text(value(stats.getOrElse(p) { PlayerMatchStats() }), style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ---- Accuracy -------------------------------------------------------------------
                    Section("Accuracy") {
                        Text(
                            "How close each player's moves were to the engine's top move on every turn with 2+ choices.",
                            style = MaterialTheme.typography.bodySmall, color = ui.onCard.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(6.dp))
                        order.forEach { p ->
                            val st = stats.getOrElse(p) { PlayerMatchStats() }
                            AccuracyRow(result.players[p], colors[p], st.accuracyPercent, st.decisions)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    CtaButton(stringResource(R.string.done), onDone, Modifier.fillMaxWidth(), icon = Icons.Filled.Check, height = 52.dp)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold,
            color = LocalBoardTheme.current.ui.primary, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

/** Green ≥ 80 %, amber ≥ 60 %, red below. */
private fun accuracyColor(pct: Int): Color = when {
    pct >= 80 -> Color(0xFF2E9E5B)
    pct >= 60 -> Color(0xFFF2A400)
    else -> Color(0xFFE0453A)
}

@Composable
private fun AccuracyRow(name: String, color: Color, pct: Int?, decisions: Int) {
    val ui = LocalBoardTheme.current.ui
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (pct == null) "—" else "$pct%", fontWeight = FontWeight.Black,
                color = if (pct == null) ui.onCard.copy(alpha = 0.6f) else accuracyColor(pct), fontSize = 18.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).background(ui.onCard.copy(alpha = 0.12f), RoundedCornerShape(5.dp))) {
            if (pct != null) Box(
                Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight()
                    .background(accuracyColor(pct), RoundedCornerShape(5.dp)),
            )
        }
        Text(
            if (decisions == 0) "No real choices this match" else "$decisions decision${if (decisions == 1) "" else "s"} analysed",
            style = MaterialTheme.typography.labelSmall, color = ui.onCard.copy(alpha = 0.6f),
        )
    }
}
