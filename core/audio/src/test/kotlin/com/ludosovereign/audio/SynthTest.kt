package com.ludosovereign.audio

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthTest {
    private val synth = ProceduralSynth()

    @Test fun sixPacksRenderEveryEvent() {
        assertEquals(6, SoundPackRegistry.ALL.size)
        var totalBytes = 0L
        for (pack in SoundPackRegistry.ALL) for (e in SoundEvent.entries) {
            val pcm = synth.render(pack, e)
            val secs = pcm.size / synth.sampleRate.toDouble()
            assertTrue(pcm.isNotEmpty() && secs <= 3.0, "${pack.id}/$e length $secs s")
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue(peak > 1000, "${pack.id}/$e is silent")
            totalBytes += pcm.size * 2
        }
        println("all packs: ${totalBytes / 1024} KiB of PCM generated at runtime (0 bytes in the APK)")
    }

    @Test fun deterministic() {
        val a = synth.render(SoundPackRegistry.ZEN_BELLS, SoundEvent.FORTRESS_LOCK)
        val b = synth.render(SoundPackRegistry.ZEN_BELLS, SoundEvent.FORTRESS_LOCK)
        assertTrue(a.contentEquals(b))
    }

    @Test fun wavHeaderIsValid() {
        val wav = Wav.encode(ShortArray(100), 22_050)
        assertEquals(244, wav.size)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals("WAVE", String(wav, 8, 4))
        // Write a sample set for listening: build/wav/<pack>/<event>.wav
        for (pack in SoundPackRegistry.ALL) for (e in listOf(SoundEvent.FORTRESS_LOCK, SoundEvent.CAPTURE, SoundEvent.WIN, SoundEvent.DICE_ROLL)) {
            val dir = File("build/wav/${pack.id}").apply { mkdirs() }
            File(dir, "${e.name.lowercase()}.wav").writeBytes(Wav.encode(synth.render(pack, e), synth.sampleRate))
        }
    }

    @Test fun renderIsFastEnoughForColdStart() {
        val t0 = System.nanoTime()
        SoundPackRegistry.ALL.forEach { p -> SoundEvent.entries.forEach { synth.render(p, it) } }
        val ms = (System.nanoTime() - t0) / 1e6
        println("render all packs: %.0f ms".format(ms))
        assertTrue(ms < 5000)
    }
}
