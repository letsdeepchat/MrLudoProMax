package com.ludosovereign.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Every sound the game can make. Nothing ships as an audio file: all of it is synthesised. */
enum class SoundEvent(val channel: AudioChannel) {
    DICE_ROLL(AudioChannel.SFX),
    DICE_LAND(AudioChannel.SFX),
    STEP(AudioChannel.SFX),
    RELEASE(AudioChannel.SFX),
    CAPTURE(AudioChannel.SFX),
    FORTRESS_LOCK(AudioChannel.SFX),
    FORTRESS_STANDOFF(AudioChannel.SFX),
    HOME_STRETCH(AudioChannel.SFX),
    /** A pawn landed on a safe square (star or coloured start square). */
    SAFE_SPOT(AudioChannel.SFX),
    HOME(AudioChannel.SFX),
    SIX(AudioChannel.SFX),
    TRIPLE_SIX(AudioChannel.SFX),
    LADDER(AudioChannel.SFX),
    SNAKE(AudioChannel.SFX),
    WIN(AudioChannel.JINGLE),
    LOSE(AudioChannel.JINGLE),
    TURN(AudioChannel.UI),
    TIMER_TICK(AudioChannel.UI),
    UI_TAP(AudioChannel.UI),
}

enum class AudioChannel { SFX, JINGLE, UI }

enum class Waveform { SINE, TRIANGLE, SQUARE, SAW, BELL }

/**
 * Which recipe set a pack uses. [LEGACY] packs share one recipe and differ only by synthesis
 * parameters; the five effect profiles each have their own recipes for the gameplay cues (dice,
 * steps, release, capture, safe square, home stretch, home, six, turn) so they sound genuinely
 * different, not just re-tuned.
 */
enum class SoundStyle { LEGACY, CASUAL_CHIMES, TRADITIONAL, MODERN_CASUAL, RETRO_ARCADE, MINIMALIST }

/**
 * A sound pack is a small set of synthesis parameters, so six packs cost a few hundred bytes of code
 * instead of megabytes of samples (keeps the APK far below the 25 MB budget).
 */
data class SoundPack(
    val id: String,
    val displayName: String,
    val waveform: Waveform,
    /** Root note in Hz; melodies are built on it. */
    val rootHz: Double,
    /** Musical scale as semitone offsets used for jingles. */
    val scale: IntArray,
    /** 0..1: low-pass amount (0 = dull, 1 = bright). */
    val brightness: Double,
    /** Multiplies every decay time. */
    val decay: Double,
    /** Feedback echo mix 0..1. */
    val echo: Double,
    /** Reduce bit depth for a retro feel (0 = off). */
    val bitCrush: Int = 0,
    /** Colour of noise used for dice and impacts: 0 = white, 1 = very dark. */
    val noiseDarkness: Double = 0.5,
    val style: SoundStyle = SoundStyle.LEGACY,
    /** Output level after normalisation (Minimalist plays quieter than the rest). */
    val gain: Double = 1.0,
    /** One line for the Audio Studio. */
    val description: String = "",
)

object SoundPackRegistry {
    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
    private val PENTA = intArrayOf(0, 2, 4, 7, 9, 12, 14, 16)
    private val DORIAN = intArrayOf(0, 2, 3, 5, 7, 9, 10, 12)

    val CLASSIC_WOOD = SoundPack("wood", "Classic Wood", Waveform.TRIANGLE, 196.0, MAJOR, 0.45, 0.8, 0.05, noiseDarkness = 0.7)
    val ARCADE = SoundPack("arcade", "Arcade 8-Bit", Waveform.SQUARE, 261.6, MAJOR, 0.9, 0.6, 0.0, bitCrush = 5, noiseDarkness = 0.1)
    val ZEN_BELLS = SoundPack("zen", "Zen Bells", Waveform.BELL, 293.7, PENTA, 0.6, 1.6, 0.25, noiseDarkness = 0.8)
    val ROYAL_BRASS = SoundPack("royal", "Royal Brass", Waveform.SAW, 174.6, MAJOR, 0.55, 1.0, 0.12, noiseDarkness = 0.6)
    val NEON_SYNTH = SoundPack("neon", "Neon Synth", Waveform.SAW, 220.0, DORIAN, 0.85, 0.9, 0.35, noiseDarkness = 0.3)
    val SOFT_NATURE = SoundPack("nature", "Soft Nature", Waveform.SINE, 329.6, PENTA, 0.35, 1.3, 0.18, noiseDarkness = 0.9)

    // ---- Sound effect profiles (1.4.0) ---------------------------------------------------------
    // All five are synthesised from scratch here; none contains or imitates recorded audio from any
    // other game. "King-style" only names the genre: bright, bubbly casual-mobile chimes.
    val CASUAL_CHIMES = SoundPack("chimes", "Casual Chimes (King-style)", Waveform.BELL, 523.3, PENTA, 0.9, 0.9, 0.18,
        noiseDarkness = 0.15, style = SoundStyle.CASUAL_CHIMES,
        description = "Bright, bubbly chimes and pops in the style of casual mobile board games")
    val TRADITIONAL = SoundPack("traditional", "Traditional", Waveform.TRIANGLE, 220.0, MAJOR, 0.4, 1.0, 0.04,
        noiseDarkness = 0.85, style = SoundStyle.TRADITIONAL,
        description = "Acoustic: dice in a wooden cup, wooden pawns knocking on the board")
    val MODERN_CASUAL = SoundPack("modern", "Modern Casual", Waveform.SINE, 392.0, MAJOR, 1.0, 0.6, 0.0,
        noiseDarkness = 0.3, style = SoundStyle.MODERN_CASUAL,
        description = "Crisp, polished UI clicks, swooshes and confirmations")
    val RETRO_ARCADE = SoundPack("retro", "Retro Arcade", Waveform.SQUARE, 261.6, MAJOR, 1.0, 0.7, 0.0,
        bitCrush = 4, noiseDarkness = 0.0, style = SoundStyle.RETRO_ARCADE,
        description = "Chiptune blips, zaps and power-up arpeggios")
    val MINIMALIST = SoundPack("minimal", "Minimalist", Waveform.SINE, 349.2, PENTA, 0.25, 0.7, 0.0,
        noiseDarkness = 0.9, style = SoundStyle.MINIMALIST, gain = 0.45,
        description = "Subtle, muted cues that stay out of the way")

    /** The Sound Effect Pack Selector's profiles, in display order. */
    val PROFILES = listOf(CASUAL_CHIMES, TRADITIONAL, MODERN_CASUAL, RETRO_ARCADE, MINIMALIST)
    /** The original parameter-only packs, still selectable under "More packs". */
    val LEGACY = listOf(CLASSIC_WOOD, ARCADE, ZEN_BELLS, ROYAL_BRASS, NEON_SYNTH, SOFT_NATURE)

    val ALL = PROFILES + LEGACY
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: CLASSIC_WOOD
}

/**
 * Tiny offline synthesiser: oscillators, ADSR-ish envelopes, coloured noise, pitch sweeps, a one-pole
 * low-pass, echo and bit crushing, rendered to 16-bit mono PCM. Deterministic (seeded noise) so the
 * same pack always sounds the same, and pure Kotlin so it is unit-tested on the JVM.
 */
class ProceduralSynth(val sampleRate: Int = 22_050) {

    fun render(pack: SoundPack, event: SoundEvent): ShortArray {
        val buf = Buffer(sampleRate, seconds = 3.0)
        val r = pack.rootHz
        val d = pack.decay
        if (pack.style != SoundStyle.LEGACY && StyledRecipes.render(buf, pack, event)) return finish(buf, pack)
        when (event) {
            SoundEvent.DICE_ROLL -> {
                val rnd = Random(7)
                var t = 0.0
                repeat(9) { i ->
                    buf.noise(t, 0.035, 0.55 - i * 0.03, pack.noiseDarkness, seed = i)
                    buf.tone(t, 0.04, r * 4 * (1 + rnd.nextDouble() * 0.3), 0.12, pack.waveform, 0.03)
                    t += 0.035 + rnd.nextDouble() * 0.03
                }
            }
            SoundEvent.DICE_LAND -> {
                buf.sweep(0.0, 0.18 * d, 180.0, 70.0, 0.8, Waveform.SINE)
                buf.noise(0.0, 0.05, 0.5, pack.noiseDarkness, seed = 3)
            }
            SoundEvent.STEP -> buf.tone(0.0, 0.07 * d, r * 2, 0.45, pack.waveform, 0.05 * d)
            SoundEvent.RELEASE -> {
                buf.sweep(0.0, 0.2 * d, r, r * 2, 0.5, pack.waveform)
                buf.tone(0.12, 0.2 * d, r * 3, 0.35, pack.waveform, 0.15 * d)
            }
            SoundEvent.CAPTURE -> {
                buf.sweep(0.0, 0.45 * d, r * 4, r * 0.5, 0.7, pack.waveform)
                buf.noise(0.0, 0.3 * d, 0.45, pack.noiseDarkness, seed = 9)
                buf.sweep(0.05, 0.3, 120.0, 45.0, 0.6, Waveform.SINE)
            }
            SoundEvent.FORTRESS_LOCK -> {
                // Metallic clank: inharmonic partials + click + low thunk.
                buf.noise(0.0, 0.02, 0.7, 0.0, seed = 11)
                buf.tone(0.0, 0.6 * d, r * 5.0, 0.35, Waveform.SINE, 0.5 * d)
                buf.tone(0.0, 0.6 * d, r * 5.0 * 1.414, 0.25, Waveform.SINE, 0.4 * d)
                buf.tone(0.0, 0.5 * d, r * 5.0 * 2.32, 0.15, Waveform.SINE, 0.25 * d)
                buf.sweep(0.0, 0.15, 140.0, 80.0, 0.7, Waveform.SINE)
                buf.tone(0.12, 0.25 * d, r * 2, 0.3, pack.waveform, 0.2 * d)
            }
            SoundEvent.FORTRESS_STANDOFF -> {
                buf.tone(0.0, 0.25, r * 1.5, 0.4, pack.waveform, 0.2)
                buf.tone(0.12, 0.3, r * 1.5 * 1.06, 0.4, pack.waveform, 0.25)
            }
            SoundEvent.HOME_STRETCH -> buf.sweep(0.0, 0.3 * d, r * 2, r * 3, 0.4, pack.waveform)
            SoundEvent.SAFE_SPOT -> {
                buf.tone(0.0, 0.18 * d, r * 3, 0.35, pack.waveform, 0.12 * d)
                buf.tone(0.07, 0.25 * d, r * 4, 0.3, pack.waveform, 0.18 * d)
            }
            SoundEvent.HOME -> buf.melody(pack, listOf(0, 2, 4, 7), 0.09, 0.5)
            SoundEvent.SIX -> {
                buf.tone(0.0, 0.25 * d, r * 4, 0.4, pack.waveform, 0.2 * d)
                buf.tone(0.08, 0.35 * d, r * 4 * 1.5, 0.4, pack.waveform, 0.3 * d)
            }
            SoundEvent.TRIPLE_SIX -> buf.melody(pack, listOf(4, 3, 0), 0.14, 0.45, octave = 0.5)
            SoundEvent.LADDER -> buf.sweep(0.0, 0.6 * d, r, r * 4, 0.45, pack.waveform, vibratoHz = 9.0)
            SoundEvent.SNAKE -> {
                buf.sweep(0.0, 0.8 * d, r * 3, r * 0.6, 0.5, pack.waveform, vibratoHz = 6.0)
                buf.noise(0.0, 0.6, 0.15, 0.2, seed = 21)
            }
            SoundEvent.WIN -> buf.melody(pack, listOf(0, 2, 4, 5, 7, 7, 9, 7, 12), 0.13, 0.5, last = 0.7)
            SoundEvent.LOSE -> buf.melody(pack, listOf(7, 5, 3, 0), 0.22, 0.45, octave = 0.5, last = 0.6)
            SoundEvent.TURN -> buf.tone(0.0, 0.12, r * 3, 0.3, Waveform.SINE, 0.1)
            SoundEvent.TIMER_TICK -> buf.noise(0.0, 0.015, 0.5, 0.0, seed = 5)
            SoundEvent.UI_TAP -> buf.tone(0.0, 0.03, r * 6, 0.25, Waveform.SINE, 0.02)
        }
        return finish(buf, pack)
    }

    private fun finish(buf: Buffer, pack: SoundPack): ShortArray {
        if (pack.brightness < 1.0) buf.lowPass(200.0 + 7000.0 * pack.brightness * pack.brightness)
        if (pack.echo > 0) buf.echo(0.11, pack.echo)
        return buf.toPcm(pack.bitCrush, pack.gain)
    }

    /**
     * Per-profile recipes for the gameplay cues. Returns false for events a profile leaves to the
     * shared recipe (jingles, snakes and ladders, timers), which still pick up the profile's
     * waveform, root, filtering and gain.
     */
    private object StyledRecipes {
        fun render(b: Buffer, p: SoundPack, e: SoundEvent): Boolean {
            val r = p.rootHz
            when (p.style) {
                SoundStyle.CASUAL_CHIMES -> when (e) {
                    SoundEvent.DICE_ROLL -> { // light plastic rattle with sparkly ticks
                        var t = 0.0
                        repeat(8) { i -> b.noise(t, 0.022, 0.4, 0.05, seed = 40 + i); b.tone(t, 0.03, r * (3.0 + (i % 3) * 0.5), 0.12, Waveform.SINE, 0.02); t += 0.045 }
                    }
                    SoundEvent.DICE_LAND -> { b.sweep(0.0, 0.09, r * 2.5, r * 1.6, 0.5, Waveform.SINE); b.noise(0.0, 0.03, 0.3, 0.1, seed = 41) }
                    SoundEvent.STEP -> b.sweep(0.0, 0.08, r * 2.2, r * 1.7, 0.42, Waveform.SINE, release = 0.05) // bubbly "plip"
                    SoundEvent.RELEASE -> b.arp(r, listOf(0, 4, 7), 0.05, 0.4, Waveform.BELL)
                    SoundEvent.CAPTURE -> { // cartoon whoop down, then a pop
                        b.sweep(0.0, 0.32, r * 3, r * 0.75, 0.55, Waveform.TRIANGLE, vibratoHz = 14.0)
                        b.noise(0.3, 0.06, 0.55, 0.05, seed = 42); b.tone(0.3, 0.12, r * 0.5, 0.5, Waveform.SINE, 0.06)
                    }
                    SoundEvent.SAFE_SPOT -> { b.tone(0.0, 0.25, r * 2, 0.35, Waveform.BELL, 0.2); b.tone(0.06, 0.35, r * 3, 0.3, Waveform.BELL, 0.3) }
                    SoundEvent.HOME_STRETCH -> b.sweep(0.0, 0.25, r * 1.5, r * 2.5, 0.35, Waveform.BELL)
                    SoundEvent.HOME -> { b.arp(r, listOf(0, 4, 7, 12, 16), 0.07, 0.42, Waveform.BELL, ring = 0.5); b.noise(0.3, 0.25, 0.12, 0.0, seed = 43) }
                    SoundEvent.SIX -> b.arp(r * 2, listOf(0, 7, 12), 0.05, 0.35, Waveform.BELL)
                    SoundEvent.TURN -> b.tone(0.0, 0.2, r * 2, 0.25, Waveform.BELL, 0.15)
                    else -> return false
                }
                SoundStyle.TRADITIONAL -> when (e) {
                    SoundEvent.DICE_ROLL -> { // dice rattling in a wooden cup
                        var t = 0.0
                        repeat(11) { i -> b.knock(t, 300.0 + (i * 37) % 160, 0.35 - i * 0.015, 50 + i); t += 0.028 + (i % 3) * 0.012 }
                    }
                    SoundEvent.DICE_LAND -> { b.knock(0.0, 180.0, 0.7, 60); b.knock(0.09, 210.0, 0.35, 61) }
                    SoundEvent.STEP -> b.knock(0.0, 520.0, 0.45, 62) // wooden pawn tapping the board
                    SoundEvent.RELEASE -> { b.knock(0.0, 440.0, 0.4, 63); b.knock(0.12, 660.0, 0.4, 64) }
                    SoundEvent.CAPTURE -> { // knocked-off pawn: heavy thunk and a clatter
                        b.sweep(0.0, 0.25, 140.0, 60.0, 0.8, Waveform.SINE)
                        var t = 0.08
                        repeat(4) { i -> b.knock(t, 700.0 - i * 90, 0.3, 65 + i); t += 0.05 + i * 0.02 }
                    }
                    SoundEvent.SAFE_SPOT -> { b.tone(0.0, 0.3, r * 2, 0.4, Waveform.SINE, 0.12); b.tone(0.08, 0.35, r * 2 * 1.5, 0.35, Waveform.SINE, 0.15) } // marimba pair
                    SoundEvent.HOME_STRETCH -> b.arp(r, listOf(0, 4), 0.08, 0.35, Waveform.SINE, ring = 0.12)
                    SoundEvent.HOME -> b.arp(r, listOf(0, 4, 7, 12), 0.1, 0.45, Waveform.SINE, ring = 0.25)
                    SoundEvent.SIX -> { b.knock(0.0, 600.0, 0.4, 70); b.tone(0.05, 0.3, r * 3, 0.3, Waveform.SINE, 0.15) }
                    SoundEvent.TURN -> b.knock(0.0, 360.0, 0.3, 71)
                    else -> return false
                }
                SoundStyle.MODERN_CASUAL -> when (e) {
                    SoundEvent.DICE_ROLL -> { var t = 0.0; repeat(7) { i -> b.click(t, 2400.0 - i * 120, 0.3); t += 0.05 } }
                    SoundEvent.DICE_LAND -> { b.click(0.0, 1600.0, 0.5); b.sweep(0.0, 0.08, 220.0, 120.0, 0.45, Waveform.SINE) }
                    SoundEvent.STEP -> b.click(0.0, 1250.0, 0.42)
                    SoundEvent.RELEASE -> { b.whoosh(0.0, 0.18, up = true, 0.35); b.tone(0.12, 0.12, r * 2, 0.35, Waveform.SINE, 0.06) }
                    SoundEvent.CAPTURE -> { b.whoosh(0.0, 0.22, up = false, 0.5); b.sweep(0.18, 0.2, 110.0, 50.0, 0.8, Waveform.SINE); b.click(0.18, 900.0, 0.4) }
                    SoundEvent.SAFE_SPOT -> { b.tone(0.0, 0.1, r * 2, 0.35, Waveform.SINE, 0.06); b.tone(0.08, 0.14, r * 2 * 1.26, 0.35, Waveform.SINE, 0.08) }
                    SoundEvent.HOME_STRETCH -> b.whoosh(0.0, 0.2, up = true, 0.3)
                    SoundEvent.HOME -> { b.arp(r, listOf(0, 4, 7, 12), 0.06, 0.4, Waveform.SINE, ring = 0.14); b.click(0.0, 2000.0, 0.25) }
                    SoundEvent.SIX -> { b.tone(0.0, 0.08, r * 3, 0.3, Waveform.SINE, 0.05); b.tone(0.06, 0.12, r * 4, 0.3, Waveform.SINE, 0.06) }
                    SoundEvent.TURN -> b.click(0.0, 1800.0, 0.25)
                    else -> return false
                }
                SoundStyle.RETRO_ARCADE -> when (e) {
                    SoundEvent.DICE_ROLL -> { var t = 0.0; repeat(10) { i -> b.tone(t, 0.025, r * (2 + (i * 5) % 7 * 0.25), 0.25, Waveform.SQUARE, 0.02); t += 0.035 } }
                    SoundEvent.DICE_LAND -> { b.sweep(0.0, 0.08, r * 2, r, 0.4, Waveform.SQUARE); b.noise(0.0, 0.04, 0.4, 0.0, seed = 80) }
                    SoundEvent.STEP -> b.tone(0.0, 0.04, r * 3, 0.32, Waveform.SQUARE, 0.03) // blip
                    SoundEvent.RELEASE -> b.sweep(0.0, 0.16, r, r * 4, 0.35, Waveform.SQUARE)
                    SoundEvent.CAPTURE -> { b.sweep(0.0, 0.35, r * 6, r * 0.4, 0.4, Waveform.SQUARE); b.noise(0.05, 0.3, 0.35, 0.0, seed = 81) } // zap + explosion
                    SoundEvent.SAFE_SPOT -> b.arp(r * 2, listOf(0, 4, 7, 12), 0.035, 0.3, Waveform.SQUARE) // power-up
                    SoundEvent.HOME_STRETCH -> b.arp(r * 2, listOf(0, 7), 0.05, 0.3, Waveform.SQUARE)
                    SoundEvent.HOME -> b.arp(r * 2, listOf(0, 4, 7, 12, 7, 12), 0.06, 0.32, Waveform.SQUARE, ring = 0.12) // extra-life jingle
                    SoundEvent.SIX -> { b.tone(0.0, 0.06, r * 4, 0.3, Waveform.SQUARE, 0.04); b.tone(0.06, 0.18, r * 6, 0.3, Waveform.SQUARE, 0.12) } // coin
                    SoundEvent.TURN -> b.tone(0.0, 0.05, r * 2, 0.25, Waveform.SQUARE, 0.04)
                    else -> return false
                }
                SoundStyle.MINIMALIST -> when (e) {
                    SoundEvent.DICE_ROLL -> { var t = 0.0; repeat(4) { i -> b.noise(t, 0.02, 0.25, 0.9, seed = 90 + i); t += 0.07 } }
                    SoundEvent.DICE_LAND -> b.tone(0.0, 0.07, 160.0, 0.5, Waveform.SINE, 0.04)
                    SoundEvent.STEP -> b.tone(0.0, 0.04, r * 1.5, 0.35, Waveform.SINE, 0.02)
                    SoundEvent.RELEASE -> b.tone(0.0, 0.1, r * 2, 0.35, Waveform.SINE, 0.06)
                    SoundEvent.CAPTURE -> b.sweep(0.0, 0.18, 180.0, 90.0, 0.6, Waveform.SINE)
                    SoundEvent.SAFE_SPOT -> b.tone(0.0, 0.16, r * 2, 0.3, Waveform.SINE, 0.1)
                    SoundEvent.HOME_STRETCH -> b.tone(0.0, 0.12, r * 1.5, 0.3, Waveform.SINE, 0.08)
                    SoundEvent.HOME -> { b.tone(0.0, 0.18, r * 2, 0.35, Waveform.SINE, 0.12); b.tone(0.12, 0.25, r * 3, 0.3, Waveform.SINE, 0.16) }
                    SoundEvent.SIX -> b.tone(0.0, 0.1, r * 3, 0.25, Waveform.SINE, 0.06)
                    SoundEvent.TURN -> b.tone(0.0, 0.06, r * 2, 0.18, Waveform.SINE, 0.04)
                    else -> return false
                }
                SoundStyle.LEGACY -> return false
            }
            return true
        }
    }

    private class Buffer(val rate: Int, seconds: Double) {
        val data = DoubleArray((rate * seconds).toInt())
        var end = 0

        private fun idx(t: Double) = (t * rate).toInt()

        private fun osc(w: Waveform, phase: Double): Double {
            val p = phase - kotlin.math.floor(phase)
            return when (w) {
                Waveform.SINE -> sin(2 * PI * p)
                Waveform.TRIANGLE -> 4 * abs(p - 0.5) - 1
                Waveform.SQUARE -> if (p < 0.5) 0.8 else -0.8
                Waveform.SAW -> 2 * p - 1
                Waveform.BELL -> sin(2 * PI * p) + 0.4 * sin(2 * PI * p * 2.76) + 0.2 * sin(2 * PI * p * 5.4)
            }
        }

        fun tone(start: Double, dur: Double, hz: Double, amp: Double, w: Waveform, release: Double = dur * 0.6) {
            sweep(start, dur, hz, hz, amp, w, release = release)
        }

        fun sweep(
            start: Double, dur: Double, fromHz: Double, toHz: Double, amp: Double, w: Waveform,
            vibratoHz: Double = 0.0, release: Double = dur * 0.6,
        ) {
            val s0 = idx(start)
            val n = idx(dur)
            var phase = 0.0
            val attack = 0.004 * rate
            for (i in 0 until n) {
                val k = s0 + i
                if (k >= data.size) break
                val f = i.toDouble() / n
                var hz = fromHz * (toHz / fromHz).pow(f)
                if (vibratoHz > 0) hz *= 1 + 0.03 * sin(2 * PI * vibratoHz * i / rate)
                phase += hz / rate
                val env = minOf(1.0, i / attack) * exp(-i.toDouble() / (release.coerceAtLeast(0.01) * rate) * 2.2)
                data[k] += osc(w, phase) * amp * env
            }
            end = maxOf(end, minOf(data.size, s0 + n))
        }

        fun noise(start: Double, dur: Double, amp: Double, darkness: Double, seed: Int) {
            val rnd = Random(seed)
            val s0 = idx(start)
            val n = idx(dur)
            var lp = 0.0
            val a = darkness.coerceIn(0.0, 0.97)
            for (i in 0 until n) {
                val k = s0 + i
                if (k >= data.size) break
                lp = a * lp + (1 - a) * (rnd.nextDouble() * 2 - 1)
                val env = exp(-i.toDouble() / n * 4)
                data[k] += lp * amp * env * (1 + a * 2)
            }
            end = maxOf(end, minOf(data.size, s0 + n))
        }

        fun melody(pack: SoundPack, steps: List<Int>, noteDur: Double, amp: Double, octave: Double = 1.0, last: Double = noteDur * 2) {
            var t = 0.0
            steps.forEachIndexed { i, s ->
                // Steps are scale degrees; degrees past the 7th wrap into the next octave.
                val semis = pack.scale[s % 7] + 12 * (s / 7)
                val hz = pack.rootHz * 2 * octave * 2.0.pow(semis / 12.0)
                val dur = if (i == steps.lastIndex) last else noteDur * 1.6
                tone(t, dur * pack.decay, hz, amp, pack.waveform, dur * 0.7 * pack.decay)
                t += noteDur
            }
        }

        /** Short struck-wood knock: a damped low partial plus a dark noise click. */
        fun knock(start: Double, hz: Double, amp: Double, seed: Int) {
            tone(start, 0.06, hz, amp, Waveform.TRIANGLE, 0.025)
            tone(start, 0.04, hz * 2.7, amp * 0.35, Waveform.SINE, 0.015)
            noise(start, 0.012, amp * 0.8, 0.6, seed)
        }

        /** Crisp UI click: a very short high sine with a hard attack. */
        fun click(start: Double, hz: Double, amp: Double) = tone(start, 0.025, hz, amp, Waveform.SINE, 0.008)

        /** Filtered-noise swoosh with a pitched tail, rising or falling. */
        fun whoosh(start: Double, dur: Double, up: Boolean, amp: Double) {
            noise(start, dur, amp * 0.6, 0.55, seed = if (up) 101 else 102)
            sweep(start, dur, if (up) 300.0 else 900.0, if (up) 900.0 else 250.0, amp * 0.5, Waveform.SINE)
        }

        /** Quick arpeggio over semitone offsets from [rootHz]; [ring] lengthens the last note. */
        fun arp(rootHz: Double, semis: List<Int>, step: Double, amp: Double, w: Waveform, ring: Double = 0.0) {
            semis.forEachIndexed { i, st ->
                val last = i == semis.lastIndex
                val dur = if (last) step * 2 + ring else step * 1.5
                tone(i * step, dur, rootHz * 2.0.pow(st / 12.0), amp, w, if (last) dur * 0.7 else step)
            }
        }

        fun lowPass(cutoffHz: Double) {
            val rc = 1.0 / (2 * PI * cutoffHz)
            val dt = 1.0 / rate
            val alpha = dt / (rc + dt)
            var y = 0.0
            for (i in 0 until end) { y += alpha * (data[i] - y); data[i] = y }
        }

        fun echo(delaySec: Double, mix: Double) {
            val d = idx(delaySec)
            val newEnd = minOf(data.size, end + d * 4)
            for (i in d until newEnd) data[i] += data[i - d] * mix * 0.6
            end = newEnd
        }

        fun toPcm(bitCrush: Int, level: Double = 1.0): ShortArray {
            val n = end
            var peak = 1e-9
            for (i in 0 until n) peak = maxOf(peak, abs(data[i]))
            val gain = (if (peak > 0.95) 0.95 / peak else 1.0) * level
            val fade = minOf(n, (0.01 * rate).toInt())
            return ShortArray(n) { i ->
                var v = data[i] * gain
                if (i >= n - fade) v *= (n - i).toDouble() / fade
                if (bitCrush > 0) {
                    val levels = (1 shl bitCrush).toDouble()
                    v = (v * levels).roundToInt() / levels
                }
                (v * Short.MAX_VALUE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}

/** Minimal RIFF/WAVE encoder (16-bit PCM mono) so SoundPool can load synthesised buffers from cache. */
object Wav {
    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataLen = pcm.size * 2
        val out = java.nio.ByteBuffer.allocate(44 + dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()); out.putInt(36 + dataLen); out.put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()); out.putInt(16); out.putShort(1); out.putShort(1)
        out.putInt(sampleRate); out.putInt(sampleRate * 2); out.putShort(2); out.putShort(16)
        out.put("data".toByteArray()); out.putInt(dataLen)
        pcm.forEach { out.putShort(it) }
        return out.array()
    }
}
