package com.ludosovereign.feature.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.CtaButton
import com.ludosovereign.designsystem.PaletteCard
import com.ludosovereign.designsystem.backdropTopBarColors
import com.ludosovereign.designsystem.LabeledSwitch
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.RobotIcon
import com.ludosovereign.designsystem.darken
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
 * Compact match setup. Game, board/mode and player count sit in one block at the top, then one
 * row per seat with a colour dot (tap to swap), an editable name and a one-tap Human/Bot toggle
 * (bots get an inline difficulty pill). House rules and theme fold away, and Play is pinned to
 * the bottom so it is always reachable without scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var board by remember { mutableStateOf(BoardTopology.CLASSIC_4) }
    var variant by remember { mutableStateOf(Variant.CLASSIC) }
    var quickTarget by remember { mutableStateOf(1) }
    var rushTimer by remember { mutableStateOf(true) }
    var count by remember { mutableStateOf(4) }
    var fortress by remember { mutableStateOf(true) }
    var tripleSix by remember { mutableStateOf(true) }
    // 3P/4P games keep going after the first finisher until only one player is left.
    var allPlacements by remember { mutableStateOf(true) }
    var undo by remember { mutableStateOf(undoDefault) }
    var snlLayout by remember { mutableStateOf(SnlLayouts.CLASSIC_100) }
    var snlExact by remember { mutableStateOf(true) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
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
    val active = rows.take(count)
    val allHuman = active.all { it.kind == PlayerKind.HUMAN }

    fun toggleKind(i: Int) {
        val r = rows[i]
        val wasDefault = r.name == "$botName ${i + 1}" || r.name == "$playerName ${i + 1}"
        r.kind = if (r.kind == PlayerKind.BOT) PlayerKind.HUMAN else PlayerKind.BOT
        if (wasDefault) r.name = if (r.kind == PlayerKind.BOT) "$botName ${i + 1}" else "$playerName ${i + 1}"
    }

    /** Next colour; if another active seat already has it, the two swap so colours never collide. */
    fun cycleColor(i: Int) {
        val r = rows[i]
        val next = (r.color + 1) % 6
        rows.take(count).withIndex().firstOrNull { it.index != i && it.value.color == next }?.value?.color = r.color
        r.color = next
    }

    fun start() {
        val specs = active.mapIndexed { i, r ->
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
            onStart(SetupResult.Ludo(MatchConfig.create(board, rules, specs), undo && allHuman, variant == Variant.RUSH))
        }
    }

    val ui = LocalBoardTheme.current.ui
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = ui.onBackdrop,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title), fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = backdropTopBarColors(),
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, ui.backdropBottom.copy(alpha = 0.95f), ui.backdropBottom)))
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            ) {
                if (type == GameType.LUDO && !teamValid) {
                    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = ui.cta, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.team_up_invalid), color = ui.onBackdrop, style = MaterialTheme.typography.bodySmall)
                    }
                }
                CtaButton(
                    stringResource(R.string.play), ::start, Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow, enabled = type == GameType.SNAKES || teamValid, height = 56.dp,
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // --- Top compact controls -------------------------------------------------------------
            PaletteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        GameType.entries.forEachIndexed { i, g ->
                            SegmentedButton(
                                selected = type == g, onClick = { type = g },
                                shape = SegmentedButtonDefaults.itemShape(i, GameType.entries.size),
                            ) { Text(if (g == GameType.LUDO) "Ludo" else stringResource(R.string.snakes_ladders), fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    if (type == GameType.LUDO) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BoardTopology.entries.forEach { b ->
                                Pill(if (b == BoardTopology.CLASSIC_4) "Classic 4" else "Hex 6", board == b) { board = b }
                            }
                            Spacer(Modifier.width(6.dp))
                            Variant.entries.forEach { v -> Pill(v.label(), variant == v) { variant = v } }
                        }
                        when (variant) {
                            Variant.QUICK -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 2).forEach { n -> Pill(stringResource(R.string.tokens_to_win, n), quickTarget == n) { quickTarget = n } }
                            }
                            Variant.RUSH -> LabeledSwitch(stringResource(R.string.turn_timer), rushTimer, { rushTimer = it })
                            Variant.TEAM_UP -> Text(stringResource(R.string.team_up_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Unit
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SnlLayouts.ALL.forEach { l -> Pill(l.displayName, snlLayout == l) { snlLayout = l } }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (2..maxPlayers).forEach { n -> Pill("${n}P", count == n) { count = n } }
                    }
                }
            }

            // --- Inline roster ------------------------------------------------------------------
            PaletteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    for (i in 0 until count) {
                        RosterRow(rows[i], i, ludo = type == GameType.LUDO, onToggle = { toggleKind(i) }, onColor = { cycleColor(i) })
                        if (i < count - 1) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }

            // --- House rules & theme (folded) ----------------------------------------------------
            PaletteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { rulesOpen = !rulesOpen }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.rules_and_theme), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(if (rulesOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = ui.primary)
                }
                AnimatedVisibility(rulesOpen) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (type == GameType.LUDO) {
                            LabeledSwitch(stringResource(R.string.rule_fortress), fortress, { fortress = it }, stringResource(R.string.rule_fortress_desc))
                            LabeledSwitch(stringResource(R.string.rule_triple_six), tripleSix, { tripleSix = it })
                            LabeledSwitch(stringResource(R.string.rule_all_places), allPlacements, { allPlacements = it })
                            LabeledSwitch(stringResource(R.string.rule_undo), undo, { undo = it }, stringResource(R.string.rule_undo_desc), enabled = allHuman)
                        } else {
                            LabeledSwitch(stringResource(R.string.exact_finish), snlExact, { snlExact = it })
                        }
                        Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BoardThemes.ALL.forEach { t -> Pill(t.name, themeId == t.id) { onThemeChange(t.id) } }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TierChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(34.dp)
            .background(if (selected) cs.primary else cs.surfaceVariant.copy(alpha = 0.55f), shape)
            .border(1.dp, if (selected) cs.primary else cs.outline.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = "$text${if (selected) ", selected" else ""}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 0.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) cs.onPrimary else cs.onSurface,
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun Variant.label(): String = when (this) {
    Variant.CLASSIC -> stringResource(R.string.variant_classic)
    Variant.QUICK -> stringResource(R.string.variant_quick)
    Variant.RUSH -> stringResource(R.string.variant_rush)
    Variant.TEAM_UP -> stringResource(R.string.variant_team)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterRow(row: PlayerRow, index: Int, ludo: Boolean, onToggle: () -> Unit, onColor: () -> Unit) {
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    val color = theme.tokenColor(row.color, cb)
    val bot = row.kind == PlayerKind.BOT
    val label = stringResource(R.string.player_n, index + 1)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).background(color, CircleShape).border(2.dp, color.darken(0.35f), CircleShape)
                    .clickable(onClick = onColor)
                    .semantics { contentDescription = "$label colour" },
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = row.name, onValueChange = { row.name = it.take(16) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label name" },
                decorationBox = { inner ->
                    Column {
                        inner()
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                border = BorderStroke(1.5.dp, if (bot) MaterialTheme.colorScheme.primary else color),
                modifier = Modifier.height(38.dp).semantics { contentDescription = "$label ${if (bot) "Bot" else "Human"}" },
            ) {
                Icon(if (bot) RobotIcon else Icons.Filled.Person, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (bot) stringResource(R.string.bot_name) else stringResource(R.string.human))
            }
        }
        if (bot && ludo) {
            // All three tiers on one row, equal widths, single-line labels: no wrapping on any phone.
            Row(
                Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BotTier.entries.forEach { t ->
                    TierChip(
                        text = when (t) { BotTier.CASUAL -> "Casual"; BotTier.GRANDMASTER -> "Grandmaster"; BotTier.UNBEATABLE -> "★ Unbeatable" },
                        selected = row.tier == t, onClick = { row.tier = t }, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
