package com.ludosovereign.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ludosovereign.data.AppSettings
import com.ludosovereign.data.LanguageStore
import com.ludosovereign.designsystem.BoardThemes
import com.ludosovereign.designsystem.LudoSovereignTheme
import com.ludosovereign.feature.audiostudio.AudioStudioScreen
import com.ludosovereign.feature.board.LudoBoardScreen
import com.ludosovereign.feature.board.LudoBoardViewModel
import com.ludosovereign.feature.board.LudoLaunch
import com.ludosovereign.feature.board.SnlBoardScreen
import com.ludosovereign.feature.board.SnlViewModel
import com.ludosovereign.feature.home.HomeScreen
import com.ludosovereign.feature.home.SettingsScreen
import com.ludosovereign.feature.setup.GameType
import com.ludosovereign.feature.setup.SetupResult
import com.ludosovereign.feature.setup.SetupScreen
import com.ludosovereign.feature.stats.ReplayScreen
import com.ludosovereign.feature.stats.StatsScreen
import com.ludosovereign.session.GameKind
import com.ludosovereign.session.MatchResult
import com.ludosovereign.session.Progression
import com.ludosovereign.session.SessionSettings
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val tag = LanguageStore.get(newBase)
        if (tag.isEmpty()) return super.attachBaseContext(newBase)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle(AppSettings())
            LudoSovereignTheme(BoardThemes.byId(settings.themeId), settings.colorblindPalette) {
                AppNav(settings, onLanguage = { tag -> LanguageStore.set(this, tag); recreate() })
            }
        }
    }

    @Composable
    private fun AppNav(settings: AppSettings, onLanguage: (String) -> Unit) {
        val c = container
        val nav = rememberNavController()
        val scope = rememberCoroutineScope()
        val update: ((AppSettings) -> AppSettings) -> Unit = { f -> scope.launch { c.settings.update(f) } }
        val defaultName = stringResource(R.string.default_profile)
        val profiles by c.profiles.observeProfiles().collectAsStateWithLifecycle(emptyList())
        LaunchedEffect(Unit) {
            val p = c.profiles.ensureDefaultProfile(defaultName)
            if (settings.activeProfileId < 0) c.settings.update { it.copy(activeProfileId = p.id) }
        }
        val activeProfile = profiles.firstOrNull { it.id == settings.activeProfileId } ?: profiles.firstOrNull()
        val sessionSettings = { fast: Boolean, undo: Boolean ->
            SessionSettings(
                autoMove = settings.autoMove,
                undoEnabled = undo,
                rollAnimationMs = (380 / settings.animationSpeed).toLong(),
                moveAnimationMs = (350 / settings.animationSpeed).toLong(),
                fastAnimations = fast,
                animationSpeed = settings.animationSpeed,
            )
        }
        var undoForMatch by remember { mutableStateOf(false) }
        var fastForMatch by remember { mutableStateOf(false) }

        NavHost(nav, startDestination = "home") {
            composable("home") {
                val slots by c.saves.observeSlots().collectAsStateWithLifecycle(emptyList())
                HomeScreen(
                    slots = slots,
                    profileName = activeProfile?.name ?: defaultName,
                    rankName = Progression.rankFor(activeProfile?.xp ?: 0).name.lowercase().replaceFirstChar { it.uppercase() },
                    onResume = { slot ->
                        scope.launch {
                            val cp = c.saves.load(slot) ?: return@launch
                            if (slot != 0) c.saves.save(0, cp) // resumed slot becomes the live auto-save
                            if (cp.kind == GameKind.LUDO) {
                                c.pendingLudo = LudoLaunch.Resume(cp); undoForMatch = false; nav.navigate("ludo")
                            } else {
                                c.pendingSnl = SnlViewModel.resume(cp); nav.navigate("snl")
                            }
                        }
                    },
                    onDeleteSlot = { slot -> scope.launch { c.saves.clear(slot) } },
                    onPlayLudo = { nav.navigate("setup/LUDO") },
                    onPlaySnakes = { nav.navigate("setup/SNAKES") },
                    onStats = { nav.navigate("stats") },
                    onAudio = { nav.navigate("audio") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("setup/{type}") { entry ->
                val type = GameType.valueOf(entry.arguments?.getString("type") ?: "LUDO")
                SetupScreen(
                    initialType = type,
                    profileName = activeProfile?.name ?: defaultName,
                    profileId = activeProfile?.id,
                    themeId = settings.themeId,
                    onThemeChange = { id -> update { it.copy(themeId = id) } },
                    undoDefault = settings.undoInPassAndPlay,
                    onBack = { nav.popBackStack() },
                    onStart = { r ->
                        when (r) {
                            is SetupResult.Ludo -> {
                                c.pendingLudo = LudoLaunch.New(r.config); undoForMatch = r.undo
                                fastForMatch = r.fast
                                nav.navigate("ludo") { popUpTo("home") }
                            }
                            is SetupResult.Snakes -> { c.pendingSnl = r.state; nav.navigate("snl") { popUpTo("home") } }
                        }
                    },
                )
            }
            composable("ludo") {
                val launch = remember { c.pendingLudo } ?: run { nav.popBackStack(); return@composable }
                val vm: LudoBoardViewModel = viewModel(
                    factory = LudoBoardViewModel.Factory(launch, sessionSettings(fastForMatch, undoForMatch), c.saves, c.history, c.feedback),
                )
                LudoBoardScreen(vm, undoForMatch, onExit = { nav.popBackStack("home", inclusive = false) },
                    onAudio = { nav.navigate("audio") }, onSettings = { nav.navigate("settings") })
            }
            composable("snl") {
                val initial = remember { c.pendingSnl } ?: run { nav.popBackStack(); return@composable }
                val vm: SnlViewModel = viewModel(factory = SnlViewModel.Factory(initial, sessionSettings(false, false), c.saves, c.history, c.feedback))
                SnlBoardScreen(vm, onExit = { nav.popBackStack("home", inclusive = false) })
            }
            composable("stats") {
                val history by c.history.observeHistory().collectAsStateWithLifecycle(emptyList())
                val pid = activeProfile?.id ?: -1L
                val stats by remember(pid) { if (pid >= 0) c.profiles.observeStats(pid) else flowOf(null) }.collectAsStateWithLifecycle(null)
                StatsScreen(
                    profiles = profiles, activeProfileId = pid, stats = stats, history = history,
                    onSelectProfile = { id -> update { it.copy(activeProfileId = id) } },
                    onCreateProfile = { name -> scope.launch { val id = c.profiles.create(name, profiles.size % 6); c.settings.update { it.copy(activeProfileId = id) } } },
                    onOpenReplay = { id -> nav.navigate("replay/$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("replay/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: -1L
                var result by remember { mutableStateOf<MatchResult?>(null) }
                LaunchedEffect(id) { result = c.history.result(id) }
                result?.log?.let { ReplayScreen(it, onBack = { nav.popBackStack() }) }
            }
            composable("audio") {
                AudioStudioScreen(settings, update, onPreview = { pack, e ->
                    if (pack != c.sound.packId) c.sound.selectPack(pack)
                    c.sound.play(e, pack); c.haptics.perform(e)
                }, onBack = { nav.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    themeId = settings.themeId, colorblind = settings.colorblindPalette, autoMove = settings.autoMove,
                    animationSpeed = settings.animationSpeed, language = LanguageStore.get(this@MainActivity),
                    onTheme = { id -> update { it.copy(themeId = id) } },
                    onColorblind = { v -> update { it.copy(colorblindPalette = v) } },
                    onAutoMove = { v -> update { it.copy(autoMove = v) } },
                    onAnimationSpeed = { v -> update { it.copy(animationSpeed = v) } },
                    onLanguage = onLanguage,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
