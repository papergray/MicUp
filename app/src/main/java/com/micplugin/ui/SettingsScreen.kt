package com.micplugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.micplugin.service.VirtualMicTier

@Composable
fun SettingsScreen(navController: NavController, vm: AudioViewModel = hiltViewModel()) {
    val status by vm.engineStatus.collectAsState()
    val tier   by vm.virtualMicTier.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Surface(color = StudioColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = StudioColors.TextPrimary)
                }
                Text("SETTINGS", style = MaterialTheme.typography.titleMedium,
                    color = StudioColors.TextPrimary, letterSpacing = 2.sp)
            }
        }

        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Audio section ─────────────────────────────────────────────────
            SettingsSection("Audio") {
                SettingsInfoRow("Sample Rate", "${status.sampleRate} Hz")
                SettingsInfoRow("Frames/Burst", "${status.framesPerBurst}")
                SettingsInfoRow("Latency (est.)", "~${status.latencyMs.toInt()} ms")
            }

            // ── Virtual Mic section ───────────────────────────────────────────
            SettingsSection("Virtual Microphone") {
                // Tier card
                Surface(
                    shape  = RoundedCornerShape(7.dp),
                    color  = Color(tier.badgeColorHex).copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, Color(tier.badgeColorHex).copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ACTIVE TIER", fontSize = 8.sp, color = StudioColors.TextMuted,
                                letterSpacing = 1.sp)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(tier.badgeColorHex).copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, Color(tier.badgeColorHex)),
                            ) {
                                Text(tier.displayName, fontSize = 9.sp,
                                    color = Color(tier.badgeColorHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(tier.description, fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                }

                VirtualMicTier.values().forEach { t ->
                    TierRow(t, isActive = t == tier)
                }
            }

            // ── Plugins section ───────────────────────────────────────────────
            SettingsSection("Plugins") {
                SettingsActionRow("Rescan Plugin Directories", Icons.Default.Refresh) {
                    vm.rescan()
                }
                SettingsInfoRow("Scan Paths",
                    "files/plugins/{lv2,clap,vst3}")
            }

            // ── About section ─────────────────────────────────────────────────
            SettingsSection("About") {
                SettingsInfoRow("Version", "1.0.0")
                SettingsInfoRow("Build", "MicPlugin · Oboe 1.8.1 · CLAP 1.2.1")
                SettingsInfoRow("Native", "C++17 · NDK 26 · AAudio/OpenSL ES")
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = StudioColors.Card,
            border = BorderStroke(0.5.dp, StudioColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = StudioColors.TextMuted)
        Text(value, fontSize = 12.sp, color = StudioColors.TextPrimary)
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = StudioColors.Accent)
        Icon(icon, null, tint = StudioColors.Accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TierRow(tier: VirtualMicTier, isActive: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isActive,
            onClick  = null,
            colors   = RadioButtonDefaults.colors(selectedColor = Color(tier.badgeColorHex)),
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(tier.displayName, fontSize = 12.sp,
                color = if (isActive) Color(tier.badgeColorHex) else StudioColors.TextMuted)
        }
    }
}
