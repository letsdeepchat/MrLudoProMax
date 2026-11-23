package com.ludosovereign.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ludosovereign.session.AnimationTimings.Companion.sliderForSpeed
import com.ludosovereign.session.AnimationTimings.Companion.speedForSlider
import com.ludosovereign.data.SaveSlot
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LocalBoardTheme
import com.ludosovereign.designsystem.LocalColorblind
import com.ludosovereign.designsystem.TokenGlyph
import com.ludosovereign.designsystem.CtaButton
import com.ludosovereign.designsystem.MusicIcon
import com.ludosovereign.designsystem.PillButton
import com.ludosovereign.designsystem.TactileSurface
import com.ludosovereign.designsystem.TrophyIcon
import com.ludosovereign.designsystem.drawPin
import com.ludosovereign.designsystem.tokenColor
import java.text.DateFormat
import java.util.Date

/**
 * Clutter-free dashboard on the theme's bright backdrop: Quick Resume first, then the two games as
 * tactile game cards (white face, bold coloured icon tile, 3-D lip that presses down), then the
 * secondary pills. Every surface takes its colours from the theme's [com.ludosovereign.designsystem.UiPalette],
 * so buttons always stand clear of the backdrop.
 */
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
    val ui = theme.ui
    val cb = LocalColorblind.current
    val auto = slots.firstOrNull { it.slot == 0 && !it.finished }
    Scaffold(containerColor = Color.Transparent, contentColor = ui.onBackdrop) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(Color.White, RoundedCornerShape(16.dp)).border(2.dp, ui.cardLip.copy(alpha = 0.5f), RoundedCornerShape(16.dp))) {
                    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                        val r = size.minDimension * 0.15f
                        val q = size.minDimension
                        listOf(Offset(q * 0.3f, q * 0.46f), Offset(q * 0.7f, q * 0.46f), Offset(q * 0.3f, q * 0.96f), Offset(q * 0.7f, q * 0.96f))
                            .forEachIndexed { i, o -> drawPin(o, r, theme.tokenColor(i, cb), glyph = if (cb) TokenGlyph.entries[i] else null) }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = ui.onBackdrop)
                    Text("$profileName · $rankName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = ui.onBackdrop.copy(alpha = 0.88f))
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.background(ui.pill, CircleShape).border(2.dp, ui.pillBorder, CircleShape),
                ) { Icon(Icons.Filled.Settings, stringResource(R.string.settings), tint = ui.onPill) }
            }

            if (auto != null) QuickResume(auto.summary) { onResume(0) }

            GameCard(stringResource(R.string.play_ludo), stringResource(R.string.play_ludo_sub), onPlayLudo) { LudoIconTile() }
            GameCard(stringResource(R.string.play_snakes), stringResource(R.string.play_snakes_sub), onPlaySnakes) { SnakesIconTile() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(stringResource(R.string.stats), onStats, Modifier.weight(1f), icon = TrophyIcon)
                PillButton(stringResource(R.string.audio_studio), onAudio, Modifier.weight(1f), icon = MusicIcon)
            }

            val manual = slots.filter { it.slot > 0 }
            if (manual.isNotEmpty()) {
                Text(stringResource(R.string.saved_games), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ui.onBackdrop)
                manual.forEach { s ->
                    TactileSurface(onClick = { onResume(s.slot) }, fill = ui.card, border = ui.cardBorder, lip = ui.cardLip,
                        corner = 18.dp, contentColor = ui.onCard, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.slot_n, s.slot), fontWeight = FontWeight.Bold)
                                Text(s.summary, style = MaterialTheme.typography.bodySmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall, color = ui.onCard.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = { onDeleteSlot(s.slot) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = ui.onCard.copy(alpha = 0.7f)) }
                            IconButton(onClick = { onResume(s.slot) }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.resume), tint = ui.primary) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.offline_promise), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                color = ui.onBackdrop.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Quick Resume: one clean card (uniform 20 dp corners, no tinted overlay) with a bright Resume button. */
@Composable
private fun QuickResume(summary: String, onResume: () -> Unit) {
    val ui = LocalBoardTheme.current.ui
    TactileSurface(onClick = null, fill = ui.card, border = ui.cardBorder, lip = ui.cardLip, corner = 20.dp,
        contentColor = ui.onCard, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(stringResource(R.string.quick_resume), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = ui.onCard.copy(alpha = 0.78f))
            }
            CtaButton(stringResource(R.string.resume), onResume, icon = Icons.Filled.PlayArrow, height = 48.dp)
        }
    }
}

/** Primary game card: white/cream face, coloured icon tile on the leading edge, chevron, 3-D lip. */
@Composable
private fun GameCard(title: String, subtitle: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val ui = LocalBoardTheme.current.ui
    TactileSurface(onClick = onClick, fill = ui.card, border = ui.cardBorder, lip = ui.cardLip, corner = 22.dp, lipHeight = 5.dp,
        contentColor = ui.onCard, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ui.onCard.copy(alpha = 0.72f))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ui.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun IconTile(top: Color, bottom: Color, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Canvas(
        Modifier.size(64.dp)
            .background(Brush.verticalGradient(listOf(top, bottom)), RoundedCornerShape(18.dp))
            .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
        onDraw = draw,
    )
}

/** Four-colour pinwheel of pawns on a royal tile. */
@Composable
private fun LudoIconTile() {
    val theme = LocalBoardTheme.current
    val cb = LocalColorblind.current
    IconTile(Color(0xFFFF6B5A), Color(0xFFD92B2B)) {
        val q = size.minDimension
        drawRoundRect(Color.White, Offset(q * 0.16f, q * 0.16f), Size(q * 0.68f, q * 0.68f), CornerRadius(q * 0.14f))
        val r = q * 0.1f
        listOf(Offset(q * 0.36f, q * 0.46f), Offset(q * 0.64f, q * 0.46f), Offset(q * 0.36f, q * 0.76f), Offset(q * 0.64f, q * 0.76f))
            .forEachIndexed { i, o -> drawPin(o, r, theme.tokenColor(i, cb)) }
    }
}

/** A ladder and a little snake on a green tile. */
@Composable
private fun SnakesIconTile() {
    IconTile(Color(0xFF5BD47A), Color(0xFF1E9A4A)) {
        val q = size.minDimension
        val rail = Color(0xFFFFE08A)
        drawLine(rail, Offset(q * 0.3f, q * 0.82f), Offset(q * 0.52f, q * 0.18f), q * 0.06f, StrokeCap.Round)
        drawLine(rail, Offset(q * 0.48f, q * 0.86f), Offset(q * 0.7f, q * 0.22f), q * 0.06f, StrokeCap.Round)
        for (k in 1..4) {
            val t = k / 5f
            val a = Offset(q * (0.3f + 0.22f * t), q * (0.82f - 0.64f * t))
            drawLine(rail, a, a + Offset(q * 0.18f, q * 0.04f), q * 0.05f, StrokeCap.Round)
        }
        val snake = Path().apply {
            moveTo(q * 0.18f, q * 0.3f)
            cubicTo(q * 0.45f, q * 0.25f, q * 0.2f, q * 0.62f, q * 0.5f, q * 0.6f)
            cubicTo(q * 0.75f, q * 0.58f, q * 0.62f, q * 0.85f, q * 0.84f, q * 0.84f)
        }
        drawPath(snake, Color(0xFF7A3FD1), style = Stroke(q * 0.09f, cap = StrokeCap.Round))
        drawCircle(Color(0xFF7A3FD1), q * 0.08f, Offset(q * 0.18f, q * 0.3f))
        drawCircle(Color.White, q * 0.025f, Offset(q * 0.16f, q * 0.28f))
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
    val ui = LocalBoardTheme.current.ui
    Scaffold(containerColor = Color.Transparent, contentColor = ui.onBackdrop) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                    color = ui.onBackdrop, modifier = Modifier.weight(1f))
                CtaButton(stringResource(R.string.done), onBack, height = 44.dp)
            }
            com.ludosovereign.designsystem.SectionCard(stringResource(R.string.theme)) {
                com.ludosovereign.designsystem.ChoiceRow(BoardThemes.ALL.map { it.id }, themeId, { BoardThemes.byId(it).name }, onTheme)
                com.ludosovereign.designsystem.LabeledSwitch(stringResource(R.string.colorblind), colorblind, onColorblind,
                    stringResource(R.string.colorblind_desc))
            }
            com.ludosovereign.designsystem.SectionCard(stringResource(R.string.gameplay)) {
                com.ludosovereign.designsystem.LabeledSwitch(stringResource(R.string.auto_move), autoMove, onAutoMove, stringResource(R.string.auto_move_desc))
                com.ludosovereign.designsystem.LabeledSlider(stringResource(R.string.animation_speed), sliderForSpeed(animationSpeed),
                    { onAnimationSpeed(speedForSlider(it)) }, "×%.1f".format(animationSpeed))
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
    "" to "System", "en" to "English", "hi" to "हिन्दी", "ta" to "தமிழ்", "te" to "తెలుగు", "kn" to "ಕನ್ನಡ",
    "mr" to "मराठी", "gu" to "ગુજરાતી", "bn" to "বাংলা", "ml" to "മലയാളം", "es" to "Español", "ar" to "العربية", "fr" to "Français",
)
