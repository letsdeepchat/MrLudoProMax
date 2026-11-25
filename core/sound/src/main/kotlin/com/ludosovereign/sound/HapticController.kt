package com.ludosovereign.sound

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ludosovereign.audio.SoundEvent

/** Short, distinct haptic signatures per game event, scaled by the user's strength setting. */
class HapticController(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile var enabled = true
    @Volatile var strength = 0.7f

    fun perform(event: SoundEvent) {
        val v = vibrator ?: return
        if (!enabled || strength <= 0f || !v.hasVibrator()) return
        val (timings, amps) = pattern(event) ?: return
        val scaled = IntArray(amps.size) { (amps[it] * strength).toInt().coerceIn(1, 255) }
        val effect = if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, scaled, -1)
        else VibrationEffect.createWaveform(timings, -1)
        v.vibrate(effect)
    }

    private fun pattern(e: SoundEvent): Pair<LongArray, IntArray>? = when (e) {
        SoundEvent.DICE_LAND -> longArrayOf(0, 18) to intArrayOf(0, 200)
        SoundEvent.STEP -> longArrayOf(0, 6) to intArrayOf(0, 90)
        SoundEvent.RELEASE -> longArrayOf(0, 20, 40, 20) to intArrayOf(0, 160, 0, 200)
        SoundEvent.CAPTURE -> longArrayOf(0, 45, 30, 80) to intArrayOf(0, 255, 0, 180)
        SoundEvent.FORTRESS_LOCK -> longArrayOf(0, 12, 35, 12, 35, 30) to intArrayOf(0, 255, 0, 255, 0, 150)
        SoundEvent.FORTRESS_STANDOFF -> longArrayOf(0, 25, 50, 25) to intArrayOf(0, 120, 0, 120)
        SoundEvent.SAFE_SPOT -> longArrayOf(0, 12, 30, 12) to intArrayOf(0, 110, 0, 150)
        SoundEvent.HOME -> longArrayOf(0, 30, 40, 30, 40, 60) to intArrayOf(0, 140, 0, 180, 0, 230)
        SoundEvent.SIX -> longArrayOf(0, 15, 30, 15) to intArrayOf(0, 160, 0, 220)
        SoundEvent.TRIPLE_SIX -> longArrayOf(0, 120) to intArrayOf(0, 200)
        SoundEvent.SNAKE -> longArrayOf(0, 40, 20, 40, 20, 40) to intArrayOf(0, 200, 0, 150, 0, 100)
        SoundEvent.LADDER -> longArrayOf(0, 20, 30, 20, 30, 20) to intArrayOf(0, 100, 0, 160, 0, 220)
        SoundEvent.WIN -> longArrayOf(0, 60, 60, 60, 60, 160) to intArrayOf(0, 180, 0, 220, 0, 255)
        SoundEvent.UI_TAP -> longArrayOf(0, 8) to intArrayOf(0, 70)
        else -> null
    }
}
