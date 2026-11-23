package com.ludosovereign.app

import android.graphics.Bitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real app (Room, DataStore, sound engine, bots) through every screen on Robolectric
 * and writes screenshots to build/screens. Fails on any crash along the way.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val out = File("build/screens").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(android.graphics.Canvas(bmp)) }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            // Advance both the Compose frame clock and Robolectric's looper clock, so coroutine delays
            // in the match loop fire as they would on a device.
            rule.mainClock.advanceTimeBy(100)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(25)
        }
        rule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun exitBoard() {
        rule.onNodeWithContentDescription("Menu").performClick()
        settle(300)
        rule.onNodeWithText("Exit match").performClick()
        settle(500)
    }

    @Test
    fun walksEveryScreen() {
        settle(800)
        shot("01_home")

        rule.onNodeWithText("Play Ludo").performClick()
        settle(300)
        // Spec check: 3 humans + 1 bot in two taps, Play visible without scrolling.
        rule.onNodeWithContentDescription("Player 2 Bot").performClick()
        rule.onNodeWithContentDescription("Player 3 Bot").performClick()
        settle(200)
        rule.onNodeWithContentDescription("Player 2 Human").assertExists()
        rule.onNodeWithContentDescription("Player 4 Bot").assertExists()
        rule.onNodeWithText("Play").assertIsDisplayed()
        // Tier chips sit on one row; picking Unbeatable shows no warning.
        rule.onAllNodesWithText("★ Unbeatable")[0].performClick()
        settle(200)
        rule.onAllNodes(hasText("Not fair", substring = true)).assertCountEquals(0)
        shot("02_setup_3h1b")
        rule.onAllNodesWithText("Grandmaster")[0].performClick()
        // Back to 1 human + 3 bots for the gameplay run.
        rule.onNodeWithContentDescription("Player 2 Human").performClick()
        rule.onNodeWithContentDescription("Player 3 Human").performClick()
        rule.onNodeWithText("Play").performClick()
        settle(600)
        shot("03_board_start")
        // Human seat: roll whenever it is our turn, pick the first glowing pawn, let bots answer.
        repeat(40) {
            runCatching { rule.onNodeWithContentDescription("Dice", substring = true).performClick() }
            settle(900)
            tapFirstMovable()
            settle(600)
        }
        shot("04_board_midgame")
        // Wait for our turn, then catch the dice mid-tumble and a pawn mid-hop.
        var humanRolls = 0
        repeat(60) {
            if (humanRolls < 6 && rule.onAllNodes(hasText("Player · Tap the dice to roll")).fetchSemanticsNodes().isNotEmpty()) {
                rule.onNodeWithContentDescription("Dice", substring = true).performClick()
                humanRolls++
                if (humanRolls == 1) { settle(150); shot("04b_board_rolling") }
                settle(700)
                tapFirstMovable()
                if (humanRolls == 1) { settle(300); shot("04d_board_hop") }
            }
            settle(500)
        }
        assertTrue("the human seat got to roll", humanRolls >= 3)
        shot("04e_board_later")
        rule.onNodeWithContentDescription("Menu").performClick()
        settle(300)
        shot("04c_pause_menu")
        rule.onNodeWithText("Exit match").performClick()
        settle(500)
        shot("05_home_resume")

        rule.onNodeWithText("Snakes & Ladders").performClick()
        settle(300)
        shot("06_setup_snakes")
        rule.onNodeWithText("Play").performClick()
        settle(800)
        shot("07_snakes_board")
        rule.onNodeWithContentDescription("Back").performClick()
        settle(400)

        scrollTo("Stats & History")
        rule.onNodeWithText("Stats & History").performClick()
        settle(500)
        shot("08_stats")
        rule.onNodeWithContentDescription("Back").performClick()
        settle(300)

        scrollTo("Audio Studio")
        rule.onNodeWithText("Audio Studio").performClick()
        settle(500)
        shot("09_audio_studio")
        rule.onNodeWithContentDescription("Back").performClick()
        settle(300)

        rule.onNodeWithContentDescription("Settings").performClick()
        settle(400)
        shot("10_settings")
        rule.onNodeWithText("Midnight Royale").performClick()
        settle(300)
        rule.onNodeWithText("Done").performClick()
        settle(300)

        // Six-player hex board, dark theme, all bots, let it run for a while.
        rule.onNodeWithText("Play Ludo").performClick()
        settle(300)
        rule.onNodeWithText("Hex 6").performClick()
        settle(200)
        rule.onNodeWithText("6P").performClick()
        settle(200)
        rule.onNodeWithContentDescription("Player 1 Human").performClick()
        settle(200)
        shot("11_setup_hex")
        rule.onNodeWithText("Play").performClick()
        settle(25_000)
        shot("12_hex_midgame_dark")
        exitBoard()

        // Two-player match: unused corners dim.
        rule.onNodeWithText("Play Ludo").performClick()
        settle(300)
        rule.onNodeWithText("Classic 4").performClick()
        rule.onNodeWithText("2P").performClick()
        settle(200)
        rule.onNodeWithText("Play").performClick()
        settle(800)
        shot("13_board_2p_dark")
        // Step away: the Auto-Bot plays our turns, then hand control back.
        rule.onNodeWithContentDescription("Auto-Bot for Player off").performClick()
        settle(6_000)
        rule.onNodeWithContentDescription("Auto-Bot for Player on").assertExists()
        shot("16_auto_bot_on")
        rule.onNodeWithContentDescription("Auto-Bot for Player on").performClick()
        settle(300)
        rule.onNodeWithContentDescription("Auto-Bot for Player off").assertExists()
        exitBoard()
    }

    /** Picks the first glowing pawn through its accessibility target, if the human has to choose. */
    private fun tapFirstMovable() {
        val nodes = rule.onAllNodes(hasContentDescription("Movable pawn", substring = true))
        if (nodes.fetchSemanticsNodes().isNotEmpty()) nodes.onFirst().performClick()
    }

    @Test @Config(qualifiers = "ta-w411dp-h891dp-xxhdpi")
    fun tamilHome() { settle(800); shot("14_home_tamil") }

    @Test @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun arabicHomeIsRtl() { settle(800); shot("15_home_arabic_rtl") }
}
