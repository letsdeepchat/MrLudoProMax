package com.ludosovereign.feature.audiostudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ludosovereign.audio.SoundEvent
import com.ludosovereign.audio.SoundPackRegistry
import com.ludosovereign.data.AppSettings
import com.ludosovereign.designsystem.ChoiceRow
import com.ludosovereign.designsystem.LabeledSlider
import com.ludosovereign.designsystem.LabeledSwitch
import com.ludosovereign.designsystem.SectionCard

/**
 * Live sound-pack preview plus per-channel volume and haptic controls. Every pack is synthesised on
 * device, so previews of a newly selected pack render in the background on first use.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioStudioScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onPreview: (packId: String, event: SoundEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = com.ludosovereign.designsystem.LocalBoardTheme.current.ui.onBackdrop, topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.audio_studio), fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = com.ludosovereign.designsystem.backdropTopBarColors(),
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(stringResource(R.string.sound_pack)) {
                ChoiceRow(SoundPackRegistry.ALL.map { it.id }, settings.soundPackId, { SoundPackRegistry.byId(it).displayName },
                    { id -> onUpdate { s -> s.copy(soundPackId = id) }; onPreview(id, SoundEvent.FORTRESS_LOCK) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoundEvent.entries.forEach { e ->
                        AssistChip(onClick = { onPreview(settings.soundPackId, e) },
                            label = { Text(e.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            SectionCard(stringResource(R.string.volume)) {
                LabeledSlider(stringResource(R.string.master), settings.masterVolume, { v -> onUpdate { it.copy(masterVolume = v) } })
                LabeledSlider(stringResource(R.string.effects), settings.sfxVolume, { v -> onUpdate { it.copy(sfxVolume = v) } })
                LabeledSlider(stringResource(R.string.jingles), settings.jingleVolume, { v -> onUpdate { it.copy(jingleVolume = v) } })
                LabeledSlider(stringResource(R.string.interface_sounds), settings.uiVolume, { v -> onUpdate { it.copy(uiVolume = v) } })
            }
            SectionCard(stringResource(R.string.haptics)) {
                LabeledSwitch(stringResource(R.string.haptics_on), settings.hapticsEnabled, { v -> onUpdate { it.copy(hapticsEnabled = v) } })
                LabeledSlider(stringResource(R.string.haptic_strength), settings.hapticStrength, { v -> onUpdate { it.copy(hapticStrength = v) } })
            }
        }
    }
}
