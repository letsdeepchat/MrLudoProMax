package com.ludosovereign.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.ChoiceRow
import com.ludosovereign.designsystem.LabeledSwitch
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.SectionCard
import com.ludosovereign.designsystem.tokenColor
import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BotTier
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.PlayerSpec
import com.ludosovereign.engine.RuleSet
import com.ludosovereign.engine.Variant
import com.ludosovereign.engine.snl.SnakesAndLaddersEngine
import com.ludosovereign.engine.snl.SnlLayouts
import com.ludosovereign.engine.snl.SnlRules
import com.ludosovereign.engine.snl.SnlState

enum class GameType { LUDO, SNAKES }
enum class PlayMode { VS_COMPUTER, PASS_AND_PLAY }

sealed interface SetupResult {
    data class Ludo(val config: MatchConfig, val undo: Boolean, val fast: Boolean) : SetupResult
    data class Snakes(val state: SnlState) : SetupResult
}

private class PlayerRow(name: String, kind: PlayerKind, tier: BotTier, color: Int) {
    var name by mutableStateOf(name)
    var kind by mutableStateOf(kind)
    var tier by mutableStateOf(tier)
    var color by mutableStateOf(color)
}

/**
 * Match setup: game type, mode, board, variant, players (human/bot and AI tier per seat) and rule
 * toggles. Everything is unlocked. The Unbeatable tier carries a visible non-fair-dice warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    initialType: GameType,
    profileName: String,
    profileId: Long?,
    themeId: String,
    onThemeChange: (String) -> Unit,
    undoDefault: Boolean,
    onBack: () -> Unit,
    onStart: (SetupResult) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var mode by remember { mutableStateOf(PlayMode.VS_COMPUTER) }
    var board by remember { mutableStateOf(BoardTopology.CLASSIC_4) }
    var variant by remember { mutableStateOf(Variant.CLASSIC) }
    var quickTarget by remember { mutableStateOf(1) }
    var rushTimer by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(4) }
    var fortress by remember { mutableStateOf(true) }
    var tripleSix by remember { mutableStateOf(true) }
    var allPlacements by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf(undoDefault) }
    var snlLayout by remember { mutableStateOf(SnlLayouts.CLASSIC_100) }
    var snlExact by remember { mutableStateOf(true) }
    val botName = stringResource(R.string.bot_name)
    val playerName = stringResource(R.string.player_name)
    val rows = remember {
        mutableStateListOf<PlayerRow>().apply {
            for (i in 0 until 6) add(PlayerRow(if (i == 0) profileName else "$botName ${i + 1}",
                if (i == 0) PlayerKind.HUMAN else PlayerKind.BOT, BotTier.GRANDMASTER, i))
        }
    }

    val maxPlayers = if (type == GameType.SNAKES) 6 else board.seats
    if (count > maxPlayers) count = maxPlayers
    val teamValid = variant != Variant.TEAM_UP || count == 4 || (count == 6 && board == BoardTopology.HEX_6)

    fun applyMode(m: PlayMode) {
        mode = m
        rows.forEachIndexed { i, r ->
            if (i == 0) return@forEachIndexed
            r.kind = if (m == PlayMode.VS_COMPUTER) PlayerKind.BOT else PlayerKind.HUMAN
            r.name = if (r.kind == PlayerKind.BOT) "$botName ${i + 1}" else "$playerName ${i + 1}"
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.setup_title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(stringResource(R.string.game)) {
                ChoiceRow(GameType.entries, type, { if (it == GameType.LUDO) "Ludo" else "Snakes & Ladders" }, { type = it })
                ChoiceRow(PlayMode.entries, mode, {
                    if (it == PlayMode.VS_COMPUTER) "Vs Computer" else "Pass & Play"
                }, ::applyMode)
            }

            if (type == GameType.LUDO) {
                SectionCard(stringResource(R.string.board_and_variant)) {
                    ChoiceRow(BoardTopology.entries, board, { if (it == BoardTopology.CLASSIC_4) "Classic 4" else "Hex 6" }, { board = it })
                    ChoiceRow(Variant.entries, variant, { it.label() }, { variant = it })
                    when (variant) {
                        Variant.QUICK -> ChoiceRow(listOf(1, 2), quickTarget, { stringResource(R.string.tokens_to_win, it) }, { quickTarget = it })
                        Variant.RUSH -> LabeledSwitch(stringResource(R.string.turn_timer), rushTimer, { rushTimer = it })
                        Variant.TEAM_UP -> Text(stringResource(R.string.team_up_hint), style = MaterialTheme.typography.bodySmall)
                        else -> Unit
                    }
                }
            } else {
                SectionCard(stringResource(R.string.layout)) {
                    ChoiceRow(SnlLayouts.ALL, snlLayout, { it.displayName }, { snlLayout = it })
                    LabeledSwitch(stringResource(R.string.exact_finish), snlExact, { snlExact = it })
                }
            }

            SectionCard(stringResource(R.string.players)) {
                ChoiceRow((2..maxPlayers).toList(), count, { it.toString() }, { count = it })
                for (i in 0 until count) PlayerEditor(rows[i], i, type == GameType.LUDO)
            }

            if (type == GameType.LUDO) {
                SectionCard(stringResource(R.string.rules)) {
                    LabeledSwitch(stringResource(R.string.rule_fortress), fortress, { fortress = it }, stringResource(R.string.rule_fortress_desc))
                    LabeledSwitch(stringResource(R.string.rule_triple_six), tripleSix, { tripleSix = it })
                    LabeledSwitch(stringResource(R.string.rule_all_places), allPlacements, { allPlacements = it })
                    LabeledSwitch(stringResource(R.string.rule_undo), undo, { undo = it }, stringResource(R.string.rule_undo_desc),
                        enabled = rows.take(count).all { it.kind == PlayerKind.HUMAN })
                }
            }

            SectionCard(stringResource(R.string.theme)) {
                ChoiceRow(BoardThemes.ALL.map { it.id }, themeId, { id -> BoardThemes.byId(id).name }, onThemeChange)
            }

            if (!teamValid) Text(stringResource(R.string.team_up_invalid), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val specs = rows.take(count).mapIndexed { i, r ->
                        PlayerSpec(r.name.ifBlank { "P${i + 1}" }, r.kind, if (r.kind == PlayerKind.BOT) r.tier else null, r.color,
                            profileId = if (i == 0 && r.kind == PlayerKind.HUMAN) profileId else null)
                    }
                    if (type == GameType.SNAKES) {
                        onStart(SetupResult.Snakes(SnakesAndLaddersEngine.newGame(snlLayout, specs, SnlRules(exactFinish = snlExact))))
                    } else {
                        val base = when (variant) {
                            Variant.CLASSIC -> RuleSet.classic()
                            Variant.QUICK -> RuleSet.quick(quickTarget)
                            Variant.RUSH -> RuleSet.rush(if (rushTimer) 10 else 0)
                            Variant.TEAM_UP -> RuleSet.teamUp()
                        }
                        val rules = base.copy(fortressEnabled = fortress, tripleSixForfeit = tripleSix, playForAllPlacements = allPlacements)
                        val allHuman = specs.all { it.kind == PlayerKind.HUMAN }
                        onStart(SetupResult.Ludo(MatchConfig.create(board, rules, specs), undo && allHuman, variant == Variant.RUSH))
                    }
                },
                enabled = type == GameType.SNAKES || teamValid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.start_match), style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Variant.label(): String = when (this) {
    Variant.CLASSIC -> stringResource(R.string.variant_classic)
    Variant.QUICK -> stringResource(R.string.variant_quick)
    Variant.RUSH -> stringResource(R.string.variant_rush)
    Variant.TEAM_UP -> stringResource(R.string.variant_team)
}

@Composable
private fun PlayerEditor(row: PlayerRow, index: Int, ludo: Boolean) {
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(theme.tokenColor(row.color, cb), CircleShape))
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(row.name, { row.name = it.take(16) }, singleLine = true, modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.player_n, index + 1)) })
        }
        ChoiceRow(PlayerKind.entries, row.kind, { if (it == PlayerKind.HUMAN) "Human" else "Bot" }, { row.kind = it })
        if (row.kind == PlayerKind.BOT && ludo) {
            ChoiceRow(BotTier.entries, row.tier, {
                when (it) {
                    BotTier.CASUAL -> "Casual"
                    BotTier.GRANDMASTER -> "Grandmaster"
                    BotTier.UNBEATABLE -> "★ Unbeatable"
                }
            }, { row.tier = it })
            if (row.tier == BotTier.UNBEATABLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.unbeatable_warning), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
