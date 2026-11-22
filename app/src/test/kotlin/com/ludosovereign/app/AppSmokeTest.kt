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
import org.junit.Rule
import org.junit.Test
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

    @Test
    fun walksEveryScreen() {
        settle(800)
        shot("01_home")

        rule.onNodeWithText("Play Ludo").performClick()
        settle(300)
        shot("02_setup_ludo")
        scrollTo("Start match")
        rule.onNodeWithText("Start match").performClick()
        settle(600)
        shot("03_board_start")
        // Human seat: roll a few times and let bots answer.
        repeat(12) {
            rule.onNodeWithContentDescription("Dice", substring = true).performClick()
            settle(1200)
        }
        shot("04_board_midgame")
        rule.onNodeWithContentDescription("Back").performClick()
        settle(500)
        shot("05_home_resume")

        rule.onNodeWithText("Snakes & Ladders").performClick()
        settle(300)
        shot("06_setup_snakes")
        scrollTo("Start match")
        rule.onNodeWithText("Start match").performClick()
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
        rule.onNodeWithText("Midnight Sovereign").performClick()
        settle(300)
        rule.onNodeWithText("Done").performClick()
        settle(300)

        // Six-player hex board, dark theme, let it run for a while.
        rule.onNodeWithText("Play Ludo").performClick()
        settle(300)
        rule.onNodeWithText("Hex 6").performClick()
        settle(200)
        rule.onNodeWithText("6").performClick()
        settle(200)
        rule.onAllNodes(hasText("Bot")).onFirst().performClick() // seat 1 becomes a bot too
        settle(200)
        shot("11_setup_hex")
        scrollTo("Start match")
        rule.onNodeWithText("Start match").performClick()
        settle(600)
        settle(25_000) // all six seats are bots: they should make real progress unattended
        shot("12_hex_midgame_dark")
    }

    @Test @Config(qualifiers = "ta-w411dp-h891dp-xxhdpi")
    fun tamilHome() { settle(800); shot("13_home_tamil") }

    @Test @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun arabicHomeIsRtl() { settle(800); shot("14_home_arabic_rtl") }
}
