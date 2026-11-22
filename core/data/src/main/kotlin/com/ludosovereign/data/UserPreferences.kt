package com.ludosovereign.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the player can tweak. All features are unlocked; there is nothing to buy. */
data class AppSettings(
    val themeId: String = "royal_ivory",
    val colorblindPalette: Boolean = false,
    val soundPackId: String = "wood",
    val masterVolume: Float = 0.9f,
    val sfxVolume: Float = 1f,
    val jingleVolume: Float = 0.8f,
    val uiVolume: Float = 0.6f,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: Float = 0.7f,
    val autoMove: Boolean = true,
    val undoInPassAndPlay: Boolean = true,
    val animationSpeed: Float = 1f,
    val showAiStats: Boolean = false,
    val activeProfileId: Long = -1,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class SettingsRepository(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val colorblind = booleanPreferencesKey("colorblind")
        val pack = stringPreferencesKey("sound_pack")
        val master = floatPreferencesKey("vol_master")
        val sfx = floatPreferencesKey("vol_sfx")
        val jingle = floatPreferencesKey("vol_jingle")
        val ui = floatPreferencesKey("vol_ui")
        val haptics = booleanPreferencesKey("haptics")
        val hapticStrength = floatPreferencesKey("haptic_strength")
        val autoMove = booleanPreferencesKey("auto_move")
        val undo = booleanPreferencesKey("undo_pnp")
        val animSpeed = floatPreferencesKey("anim_speed")
        val aiStats = booleanPreferencesKey("ai_stats")
        val profile = intPreferencesKey("active_profile")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map(::read)

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeId = p[K.theme] ?: d.themeId,
            colorblindPalette = p[K.colorblind] ?: d.colorblindPalette,
            soundPackId = p[K.pack] ?: d.soundPackId,
            masterVolume = p[K.master] ?: d.masterVolume,
            sfxVolume = p[K.sfx] ?: d.sfxVolume,
            jingleVolume = p[K.jingle] ?: d.jingleVolume,
            uiVolume = p[K.ui] ?: d.uiVolume,
            hapticsEnabled = p[K.haptics] ?: d.hapticsEnabled,
            hapticStrength = p[K.hapticStrength] ?: d.hapticStrength,
            autoMove = p[K.autoMove] ?: d.autoMove,
            undoInPassAndPlay = p[K.undo] ?: d.undoInPassAndPlay,
            animationSpeed = p[K.animSpeed] ?: d.animationSpeed,
            showAiStats = p[K.aiStats] ?: d.showAiStats,
            activeProfileId = (p[K.profile] ?: -1).toLong(),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val cur = read(p)
            val n = transform(cur)
            p[K.theme] = n.themeId
            p[K.colorblind] = n.colorblindPalette
            p[K.pack] = n.soundPackId
            p[K.master] = n.masterVolume
            p[K.sfx] = n.sfxVolume
            p[K.jingle] = n.jingleVolume
            p[K.ui] = n.uiVolume
            p[K.haptics] = n.hapticsEnabled
            p[K.hapticStrength] = n.hapticStrength
            p[K.autoMove] = n.autoMove
            p[K.undo] = n.undoInPassAndPlay
            p[K.animSpeed] = n.animationSpeed
            p[K.aiStats] = n.showAiStats
            p[K.profile] = n.activeProfileId.toInt()
        }
    }
}

/**
 * The chosen UI language lives in plain SharedPreferences because it must be read synchronously in
 * `attachBaseContext`, before any coroutine machinery exists.
 */
object LanguageStore {
    private const val FILE = "locale"
    private const val KEY = "tag"

    /** BCP-47 tags of the bundled translations ("" = follow the system). */
    val SUPPORTED = listOf("", "en", "hi", "ta", "te", "kn", "mr", "gu", "bn", "ml", "es", "ar", "fr")

    fun get(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    fun set(context: Context, tag: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }
}
