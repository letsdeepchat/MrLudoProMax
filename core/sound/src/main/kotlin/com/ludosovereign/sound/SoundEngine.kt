package com.ludosovereign.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ludosovereign.audio.AudioChannel
import com.ludosovereign.audio.ProceduralSynth
import com.ludosovereign.audio.SoundEvent
import com.ludosovereign.audio.SoundPackRegistry
import com.ludosovereign.audio.Wav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class Volumes(
    val master: Float = 0.9f,
    val sfx: Float = 1f,
    val jingle: Float = 0.8f,
    val ui: Float = 0.6f,
) {
    fun forChannel(c: AudioChannel): Float = master * when (c) {
        AudioChannel.SFX -> sfx
        AudioChannel.JINGLE -> jingle
        AudioChannel.UI -> ui
    }
}

/**
 * Low-latency playback of procedurally synthesised sounds. A pack is rendered once to small WAV files
 * in the cache directory (on a background thread, never blocking cold start), then loaded into a
 * [SoundPool]. Switching packs renders the new one lazily; previously rendered packs stay cached.
 */
class SoundEngine(context: Context, private val scope: CoroutineScope) {
    private val cacheDir = File(context.cacheDir, "sfx/v$SYNTH_VERSION")
    private val synth = ProceduralSynth()
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val ready = ConcurrentHashMap.newKeySet<Int>()
    private val preparing = ConcurrentHashMap.newKeySet<String>()

    @Volatile var volumes = Volumes()
    @Volatile var enabled = true
    @Volatile var packId: String = SoundPackRegistry.CLASSIC_WOOD.id
        private set

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
    }

    fun selectPack(id: String) {
        packId = SoundPackRegistry.byId(id).id
        prepare(packId)
    }

    /** Renders and loads a pack in the background. Safe to call repeatedly. */
    fun prepare(id: String) {
        if (!preparing.add(id)) return
        scope.launch(Dispatchers.IO) {
            val pack = SoundPackRegistry.byId(id)
            val dir = File(cacheDir, pack.id).apply { mkdirs() }
            // Most frequent sounds first so the first dice roll is already ready.
            val order = listOf(SoundEvent.DICE_ROLL, SoundEvent.DICE_LAND, SoundEvent.STEP, SoundEvent.UI_TAP) +
                SoundEvent.entries.filterNot { it in setOf(SoundEvent.DICE_ROLL, SoundEvent.DICE_LAND, SoundEvent.STEP, SoundEvent.UI_TAP) }
            for (event in order) {
                val file = File(dir, "${event.name.lowercase()}.wav")
                if (!file.exists() || file.length() < 64) {
                    val tmp = File(dir, file.name + ".tmp")
                    tmp.writeBytes(Wav.encode(synth.render(pack, event), synth.sampleRate))
                    tmp.renameTo(file)
                }
                soundIds[key(pack.id, event)] = pool.load(file.path, 1)
            }
        }
    }

    fun play(event: SoundEvent, pack: String = packId, rate: Float = 1f) {
        if (!enabled) return
        val id = soundIds[key(pack, event)] ?: run { prepare(pack); return }
        if (id !in ready) return
        val v = volumes.forChannel(event.channel).coerceIn(0f, 1f)
        if (v <= 0f) return
        pool.play(id, v, v, if (event.channel == AudioChannel.JINGLE) 2 else 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()

    private fun key(pack: String, e: SoundEvent) = "$pack/${e.name}"

    companion object { const val SYNTH_VERSION = 1 }
}
