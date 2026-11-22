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

    val ALL = listOf(CLASSIC_WOOD, ARCADE, ZEN_BELLS, ROYAL_BRASS, NEON_SYNTH, SOFT_NATURE)
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
        if (pack.brightness < 1.0) buf.lowPass(200.0 + 7000.0 * pack.brightness * pack.brightness)
        if (pack.echo > 0) buf.echo(0.11, pack.echo)
        return buf.toPcm(pack.bitCrush)
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

        fun toPcm(bitCrush: Int): ShortArray {
            val n = end
            var peak = 1e-9
            for (i in 0 until n) peak = maxOf(peak, abs(data[i]))
            val gain = if (peak > 0.95) 0.95 / peak else 1.0
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
