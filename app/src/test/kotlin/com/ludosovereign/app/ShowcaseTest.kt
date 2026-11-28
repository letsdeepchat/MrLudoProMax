package com.ludosovereign.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ludosovereign.data.SaveSlot
import com.ludosovereign.designsystem.BoardTheme
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LudoSovereignTheme
import com.ludosovereign.engine.BoardTopology
import com.ludosovereign.engine.BoardTopology.Companion.BASE
import com.ludosovereign.engine.GameState
import com.ludosovereign.engine.MatchConfig
import com.ludosovereign.engine.Phase
import com.ludosovereign.engine.PlayerKind
import com.ludosovereign.engine.PlayerSpec
import com.ludosovereign.engine.RuleSet
import com.ludosovereign.feature.board.BoardCanvas
import com.ludosovereign.feature.board.MatchSummary
import com.ludosovereign.session.MatchResult
import com.ludosovereign.session.MatchStats
import com.ludosovereign.session.PlayerMatchStats
import com.ludosovereign.feature.home.HomeScreen
import com.ludosovereign.session.AnimationTimings
import com.ludosovereign.session.GameKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders hand-built positions (finished pawns, ✓ badges, a Fortress, a mixed stack on a star,
 * pawns on start squares) and the home screen across themes, for visual review in build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ShowcaseTest {
    // Hosted in the app's own activity (its content is replaced), so the test also runs on the
    // release unit-test variant, which has no test-only ComponentActivity in its manifest.
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun show(content: @Composable () -> Unit) {
        rule.runOnUiThread { rule.activity.setContent(content = content) }
        rule.waitForIdle()
    }

    private val out = File("build/screens").apply { mkdirs() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun players(n: Int) = List(n) { PlayerSpec("P${it + 1}", PlayerKind.HUMAN, colorIndex = BoardThemes.seatColorIndex(it)) }

    /** Classic board: Red 2 home + a pawn on a star shared with Yellow; Green Fortress; Blue (bottom-right) 3 home. */
    private fun classicShowcase(): GameState {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), players(4))
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = intArrayOf(
            h, h, 8, BASE,      // Red: two finished, one on the star at cell 8
            h, 20, 20, 53,      // Green: one finished, a Fortress on cell 33, one in the home stretch
            h, h, h, 8,         // Blue: three finished, one on its star (cell 34)
            BASE, BASE, 0, 21,  // Yellow: two in the yard, one on its start square, one sharing Red's star
        )
        return GameState(cfg, tokens, current = 0, phase = Phase.AWAITING_MOVE, dice = 3)
    }

    private fun hexShowcase(): GameState {
        val cfg = MatchConfig.create(BoardTopology.HEX_6, RuleSet.classic(), players(6))
        val h = BoardTopology.HEX_6.homeProgress
        val tokens = intArrayOf(
            h, h, 10, BASE,
            h, 30, 30, BASE,
            h, h, h, 79,
            5, BASE, BASE, BASE,
            h, h, h, h,
            BASE, 40, BASE, BASE,
        )
        return GameState(cfg, tokens, current = 0, phase = Phase.AWAITING_MOVE, dice = 3)
    }

    @Test fun boardsAcrossThemes() {
        var theme by mutableStateOf(BoardThemes.CLASSIC_ROYAL)
        var game by mutableStateOf(classicShowcase())
        show {
            LudoSovereignTheme(theme, colorblind = false) {
                Column(Modifier.fillMaxSize().systemBarsPadding(), verticalArrangement = Arrangement.Center) {
                    BoardCanvas(
                        game = game, events = emptyList(), eventSeq = 0, selectable = setOf(2), activeSeat = 0,
                        timings = AnimationTimings(), onTokenTap = {}, onHop = {},
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }
            }
        }
        assertEquals(2, game.tokensHome(0))
        val themes: List<BoardTheme> = listOf(
            BoardThemes.CLASSIC_ROYAL, BoardThemes.EMERALD_JUNGLE, BoardThemes.ROYAL_IVORY,
            BoardThemes.MIDNIGHT_SOVEREIGN, BoardThemes.NEON_ARCADE, BoardThemes.GRAPHITE_CONTRAST,
        )
        for (t in themes) { theme = t; shot("20_board_${t.id}") }
        theme = BoardThemes.CLASSIC_ROYAL
        game = hexShowcase()
        shot("21_hex_classic_royal")
    }

    /** 3P: Red finished (Ultra), Green mid-race with the Auto-Bot on, the unused seat shows × wells. */
    private fun threePlayerPodium(): GameState {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic().copy(playForAllPlacements = true), players(3))
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = intArrayOf(
            h, h, h, h,
            30, 52, 8, BASE,
            15, 21, BASE, h,
        )
        return GameState(cfg, tokens, current = 1, phase = Phase.AWAITING_ROLL, finishOrder = intArrayOf(0))
    }

    @Test fun podiumAutoBotAndSummary() {
        val game = threePlayerPodium()
        show {
            LudoSovereignTheme(BoardThemes.CLASSIC_ROYAL, colorblind = false) {
                Column(Modifier.fillMaxSize().systemBarsPadding(), verticalArrangement = Arrangement.Center) {
                    BoardCanvas(
                        game = game, events = emptyList(), eventSeq = 0, selectable = emptySet(), activeSeat = game.seatOf(1),
                        timings = AnimationTimings(), onTokenTap = {}, onHop = {},
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        autoPlayers = setOf(1), onAutoToggle = {},
                    )
                }
            }
        }
        shot("23_board_3p_podium_auto")

        val stats = MatchStats(listOf(
            PlayerMatchStats(rolls = 60, sixes = 11, captures = 4, timesCaptured = 1, safeLandings = 9, bonusTurns = 15, missedCuts = 1, decisions = 22, accuracySum = 19.6),
            PlayerMatchStats(rolls = 64, sixes = 9, captures = 2, timesCaptured = 3, safeLandings = 6, bonusTurns = 11, missedCuts = 3, decisions = 25, accuracySum = 16.9, tripleSixForfeits = 1),
            PlayerMatchStats(rolls = 66, sixes = 12, captures = 1, timesCaptured = 3, safeLandings = 4, bonusTurns = 13, missedCuts = 4, decisions = 20, accuracySum = 10.8),
        ))
        val result = MatchResult(GameKind.LUDO, "CLASSIC", "CLASSIC_4", listOf("P1", "P2", "P3"), listOf(true, true, false),
            listOf(null, null, null), listOf(0, 2, 1), 0, stats, null, 1L)
        show {
            LudoSovereignTheme(BoardThemes.CLASSIC_ROYAL, colorblind = false) {
                MatchSummary(result, game, onDone = {})
            }
        }
        shot("24_match_summary")
    }

    @Test fun homeAcrossThemes() {
        var theme by mutableStateOf(BoardThemes.CLASSIC_ROYAL)
        val slots = listOf(
            SaveSlot(0, "Classic · 4P · turn 136 · Bot 3 leads", 0L, false, GameKind.LUDO),
            SaveSlot(1, "Quick · 2P · turn 12", 1_700_000_000_000L, false, GameKind.LUDO),
        )
        show {
            LudoSovereignTheme(theme, colorblind = false) {
                HomeScreen(slots, "Player", "Pawn", {}, {}, {}, {}, {}, {}, {})
            }
        }
        for (t in BoardThemes.ALL) { theme = t; shot("22_home_${t.id}") }
    }
}
