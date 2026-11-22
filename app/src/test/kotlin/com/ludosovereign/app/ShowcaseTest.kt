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

    private fun players(n: Int) = List(n) { PlayerSpec("P${it + 1}", PlayerKind.HUMAN, colorIndex = it) }

    /** Classic board: Red 2 home + a pawn on a star shared with Blue; Green Fortress; Yellow 3 home. */
    private fun classicShowcase(): GameState {
        val cfg = MatchConfig.create(BoardTopology.CLASSIC_4, RuleSet.classic(), players(4))
        val h = BoardTopology.CLASSIC_4.homeProgress
        val tokens = intArrayOf(
            h, h, 8, BASE,      // Red: two finished, one on the star at cell 8
            h, 20, 20, 53,      // Green: one finished, a Fortress on cell 33, one in the home stretch
            h, h, h, 8,         // Yellow: three finished, one on its star (cell 34)
            BASE, BASE, 0, 21,  // Blue: two in the yard, one on its start square, one sharing Red's star
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
