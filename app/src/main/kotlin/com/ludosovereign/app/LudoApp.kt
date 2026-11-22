package com.ludosovereign.app

import android.app.Application
import android.content.Context
import com.ludosovereign.data.LudoDatabase
import com.ludosovereign.data.ProfileRepository
import com.ludosovereign.data.RoomHistoryRepository
import com.ludosovereign.data.RoomSaveRepository
import com.ludosovereign.data.SettingsRepository
import com.ludosovereign.engine.snl.SnlState
import com.ludosovereign.feature.board.LudoLaunch
import com.ludosovereign.sound.GameFeedback
import com.ludosovereign.sound.HapticController
import com.ludosovereign.sound.SoundEngine
import com.ludosovereign.sound.Volumes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hand-rolled DI container: everything is created lazily so cold start only pays for what the first
 * frame needs (the database and the synthesiser warm up on background threads).
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { LudoDatabase.create(context) }
    val saves by lazy { RoomSaveRepository(db) }
    val history by lazy { RoomHistoryRepository(db) }
    val profiles by lazy { ProfileRepository(db) }
    val settings = SettingsRepository(context)
    val sound by lazy { SoundEngine(context, appScope) }
    val haptics by lazy { HapticController(context) }
    val feedback by lazy { GameFeedback(sound, haptics) }

    /** Hand-off from the setup/home screens to the board screens. */
    @Volatile var pendingLudo: LudoLaunch? = null
    @Volatile var pendingSnl: SnlState? = null
}

class LudoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Keep audio/haptics in sync with settings; first pack renders in the background.
        container.appScope.launch {
            container.settings.settings.distinctUntilChanged().collect { s ->
                container.sound.volumes = Volumes(s.masterVolume, s.sfxVolume, s.jingleVolume, s.uiVolume)
                if (container.sound.packId != s.soundPackId) container.sound.selectPack(s.soundPackId)
                else container.sound.prepare(s.soundPackId)
                container.haptics.enabled = s.hapticsEnabled
                container.haptics.strength = s.hapticStrength
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as LudoApp).container
