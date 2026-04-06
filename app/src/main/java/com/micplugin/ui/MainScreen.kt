package com.micplugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.micplugin.plugin.PluginFormat
import com.micplugin.service.VirtualMicTier

@Composable
fun MainScreen(navController: NavController, vm: AudioViewModel = hiltViewModel()) {
    val levels      by vm.levels.collectAsState()
    val status      by vm.engineStatus.collectAsState()
    val gateState   by vm.gateState.collectAsState()
    val eqState     by vm.eqState.collectAsState()
    val compState   by vm.compState.collectAsState()
    val reverbState by vm.reverbState.collectAsState()
    val pitchState  by vm.pitchState.collectAsState()
    val bypass      by vm.masterBypass.collectAsState()
    val presets     by vm.presets.collectAsState()
    val selectedPreset by vm.selectedPreset.collectAsState()
    val pluginSlots by vm.pluginSlots.collectAsState()
    val tier        by vm.virtualMicTier.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName     by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "MIC PLUGIN",
                style = MaterialTheme.typography.titleLarge,
                color = StudioColors.TextPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            // Virtual mic tier badge
            VirtualMicBadge(tier)
            Spacer(Modifier.width(10.dp))
            // Power button
            IconButton(
                onClick = { vm.toggleEngine() },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (status.isRunning) StudioColors.Accent else StudioColors.Card,
                        RoundedCornerShape(6.dp),
                    ),
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew, "Power",
                    tint = if (status.isRunning) Color.White else StudioColors.TextMuted,
                )
            }
            Spacer(Modifier.width(6.dp))
            // Settings
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Default.Settings, "Settings", tint = StudioColors.TextMuted)
            }
        }

        // ── Preset chips ──────────────────────────────────────────────────────
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(presets) { preset ->
                val isSelected = selectedPreset?.name == preset.name
                FilterChip(
                    selected  = isSelected,
                    onClick   = { vm.applyPreset(preset) },
                    label     = { Text(preset.name, fontSize = 10.sp) },
                    colors    = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioColors.AccentDim,
                        selectedLabelColor     = Color.White,
                        containerColor         = StudioColors.Card,
                        labelColor             = StudioColors.TextMuted,
                    ),
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick  = { showSaveDialog = true },
                    label    = { Text("+ Save", fontSize = 10.sp) },
                    colors   = FilterChipDefaults.filterChipColors(
                        containerColor = StudioColors.Card,
                        labelColor     = StudioColors.Accent,
                    ),
                )
            }
        }

        // ── Metering strip ────────────────────────────────────────────────────
        MeteringStrip(
            inputDb  = levels.inputDb,
            outputDb = levels.outputDb,
            grDb     = levels.gainReductionDb,
        )

        // Status row
        if (status.isRunning) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusPill("${status.sampleRate / 1000}kHz")
                StatusPill("${status.framesPerBurst}fr")
                StatusPill("~${status.latencyMs.toInt()}ms")
                StatusPill("${pluginSlots.size} plugins")
            }
        }

        // Master bypass toggle
        if (status.isRunning) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setMasterBypass(!bypass) }
                    .background(if (bypass) StudioColors.MeterAmber.copy(alpha = 0.1f) else Color.Transparent,
                        RoundedCornerShape(6.dp))
                    .border(1.dp,
                        if (bypass) StudioColors.MeterAmber else StudioColors.Border,
                        RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MASTER BYPASS", style = MaterialTheme.typography.labelSmall,
                    color = if (bypass) StudioColors.MeterAmber else StudioColors.TextMuted,
                    letterSpacing = 1.sp)
                Switch(checked = bypass, onCheckedChange = { vm.setMasterBypass(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StudioColors.MeterAmber,
                        checkedTrackColor = StudioColors.MeterAmber.copy(alpha = 0.3f),
                    ))
            }
        }

        // ── Noise Gate ────────────────────────────────────────────────────────
        EffectCard(
            title   = "Noise Gate",
            enabled = gateState.enabled,
            onToggle = vm::setGateEnabled,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamSlider("Threshold", gateState.thresholdDb, vm::setGateThreshold,
                    -80f..-0f, " dB")
                ParamSlider("Attack", gateState.attackMs, vm::setGateAttack,
                    0.1f..50f, " ms")
                ParamSlider("Release", gateState.releaseMs, vm::setGateRelease,
                    10f..500f, " ms")
            }
        }

        // ── Equalizer ─────────────────────────────────────────────────────────
        EffectCard(
            title   = "10-Band EQ",
            enabled = eqState.enabled,
            onToggle = vm::setEqEnabled,
        ) {
            val freqLabels = listOf("31","63","125","250","500","1k","2k","4k","8k","16k")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                freqLabels.forEachIndexed { i, freq ->
                    EqBandFader(
                        freq       = freq,
                        gainDb     = eqState.bands.getOrElse(i) { 0f },
                        onGainChange = { vm.setEqBand(i, it) },
                    )
                }
            }
        }

        // ── Compressor ────────────────────────────────────────────────────────
        EffectCard(
            title   = "Compressor",
            enabled = compState.enabled,
            onToggle = vm::setCompEnabled,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    KnobWidget("THRESH", compState.thresholdDb, vm::setCompThreshold,
                        -60f..0f, unit = "dB")
                    KnobWidget("RATIO", compState.ratio, vm::setCompRatio,
                        1f..20f, unit = ":1")
                    KnobWidget("ATTACK", compState.attackMs, vm::setCompAttack,
                        0.1f..100f, unit = "ms")
                    KnobWidget("RELEASE", compState.releaseMs, vm::setCompRelease,
                        10f..1000f, unit = "ms")
                    KnobWidget("MAKEUP", compState.makeupDb, vm::setCompMakeup,
                        0f..24f, unit = "dB")
                }
                // GR readout
                val gr = levels.gainReductionDb
                Text("GR: ${if (gr > 0.1f) "-%.1f dB".format(gr) else "0.0 dB"}",
                    fontSize = 9.sp, color = StudioColors.GainReduction)
            }
        }

        // ── Reverb ────────────────────────────────────────────────────────────
        EffectCard(
            title   = "Reverb",
            enabled = reverbState.enabled,
            onToggle = vm::setReverbEnabled,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamSlider("Mix", reverbState.mix, vm::setReverbMix, 0f..1f)
                ParamSlider("Room Size", reverbState.roomSize, vm::setReverbRoom, 0f..1f)
                ParamSlider("Damping", reverbState.damping, vm::setReverbDamp, 0f..1f)
            }
        }

        // ── Pitch Shifter ─────────────────────────────────────────────────────
        EffectCard(
            title   = "Pitch Shifter",
            enabled = pitchState.enabled,
            onToggle = vm::setPitchEnabled,
        ) {
            ParamSlider("Semitones", pitchState.semitones, vm::setPitchSemitones,
                -12f..12f, " st")
        }

        // ── Plugin Chain ─────────────────────────────────────────────────────
        SectionLabel("Plugin Chain")
        pluginSlots.forEachIndexed { idx, slot ->
            PluginSlotCard(
                slot     = slot,
                onToggle = { vm.togglePlugin(slot.id) },
                onRemove = { vm.removePlugin(slot.id) },
                onEdit   = { navController.navigate("plugin_editor/${slot.id}") },
            )
        }
        // Add plugin FAB row
        OutlinedButton(
            onClick = { navController.navigate("plugin_browser") },
            modifier = Modifier.fillMaxWidth(),
            border   = BorderStroke(1.dp, StudioColors.AccentDim),
        ) {
            Icon(Icons.Default.Add, null, tint = StudioColors.Accent)
            Spacer(Modifier.width(6.dp))
            Text("Add Plugin", color = StudioColors.Accent)
        }

        Spacer(Modifier.height(20.dp))
    }

    // ── Save preset dialog ────────────────────────────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor   = StudioColors.Card,
            title  = { Text("Save Preset", color = StudioColors.TextPrimary) },
            text   = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioColors.Accent,
                        focusedLabelColor  = StudioColors.Accent,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName)
                        showSaveDialog = false
                        presetName = ""
                    }
                }) { Text("Save", color = StudioColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = StudioColors.TextMuted) }
            },
        )
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = StudioColors.Card) {
        Text(text, fontSize = 8.sp, color = StudioColors.TextMuted,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun VirtualMicBadge(tier: VirtualMicTier) {
    val color = Color(tier.badgeColorHex)
    Surface(
        shape  = RoundedCornerShape(5.dp),
        color  = color.copy(alpha = 0.1f),
        border = BorderStroke(0.5.dp, color),
    ) {
        Text(
            tier.displayName, fontSize = 8.sp, color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun PluginSlotCard(
    slot: com.micplugin.plugin.PluginSlot,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(7.dp),
        color    = StudioColors.Card,
        border   = BorderStroke(
            0.5.dp,
            if (slot.enabled) StudioColors.Border else StudioColors.Border.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.DragHandle, null,
                tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(slot.descriptor.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slot.enabled) StudioColors.TextPrimary else StudioColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                PluginFormatBadge(slot.descriptor.format)
            }
            Switch(
                checked = slot.enabled, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = StudioColors.AccentDim,
                    uncheckedTrackColor = StudioColors.Border,
                ),
                modifier = Modifier.height(20.dp),
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Tune, "Edit", tint = StudioColors.TextMuted,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "Remove", tint = StudioColors.MeterRed,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
