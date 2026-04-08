package com.micplugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.micplugin.CrashReporter
import com.micplugin.service.ShizukuState
import com.micplugin.service.VirtualMicTier

@Composable
fun SettingsScreen(navController: NavController, vm: AudioViewModel = hiltViewModel()) {
    val status      by vm.engineStatus.collectAsState()
    val tier        by vm.virtualMicTier.collectAsState()
    val shizukuState by vm.shizukuState.collectAsState()
    val context     = LocalContext.current

    var crashReports by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var viewingCrash by remember { mutableStateOf<java.io.File?>(null) }

    LaunchedEffect(Unit) { crashReports = CrashReporter.getCrashReports(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Surface(color = StudioColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = StudioColors.TextPrimary)
                }
                Text("SETTINGS", style = MaterialTheme.typography.titleMedium,
                    color = StudioColors.TextPrimary, letterSpacing = 2.sp)
            }
        }

        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Audio ─────────────────────────────────────────────────────────
            SettingsSection("Audio") {
                SettingsInfoRow("Sample Rate", "${status.sampleRate} Hz")
                SettingsInfoRow("Frames/Burst", "${status.framesPerBurst}")
                SettingsInfoRow("Latency (est.)", "~${status.latencyMs.toInt()} ms")
            }

            // ── Shizuku ───────────────────────────────────────────────────────
            SettingsSection("Shizuku (ADB Access)") {
                val (stateColor, stateLabel) = when (shizukuState) {
                    ShizukuState.READY       -> Color(0xFF3DFCAC) to "READY"
                    ShizukuState.NEED_GRANT  -> Color(0xFFFFD700) to "NEED PERMISSION"
                    ShizukuState.UNAVAILABLE -> Color(0xFFFF6B6B) to "NOT RUNNING"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Status", fontSize = 12.sp, color = StudioColors.TextMuted)
                        Text(stateLabel, fontSize = 11.sp, color = stateColor)
                    }
                    if (shizukuState == ShizukuState.NEED_GRANT) {
                        Button(
                            onClick = { vm.requestShizukuPermission() },
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B4D8)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text("Grant", fontSize = 11.sp)
                        }
                    }
                }
                if (shizukuState == ShizukuState.UNAVAILABLE) {
                    Text(
                        "Install Shizuku from Play Store, start it via ADB or wireless debugging, then reopen this app.",
                        fontSize = 10.sp, color = StudioColors.TextMuted, lineHeight = 14.sp,
                    )
                }
                if (shizukuState == ShizukuState.READY) {
                    Text(
                        "Shizuku active — ALSA loopback and appops routing available.",
                        fontSize = 10.sp, color = StudioColors.TextMuted, lineHeight = 14.sp,
                    )
                }
            }

            // ── Virtual Mic ───────────────────────────────────────────────────
            SettingsSection("Virtual Microphone") {
                Surface(
                    shape  = RoundedCornerShape(7.dp),
                    color  = Color(tier.badgeColorHex).copy(alpha = 0.05f),
                    border = BorderStroke(0.5.dp, Color(tier.badgeColorHex).copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ACTIVE TIER", fontSize = 8.sp, color = StudioColors.TextMuted, letterSpacing = 1.sp)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape  = RoundedCornerShape(4.dp),
                                color  = Color(tier.badgeColorHex).copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, Color(tier.badgeColorHex)),
                            ) {
                                Text(tier.displayName, fontSize = 9.sp, color = Color(tier.badgeColorHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(tier.description, fontSize = 11.sp, color = StudioColors.TextMuted)
                    }
                }

                VirtualMicTier.values().forEach { t ->
                    TierRow(
                        tier     = t,
                        isActive = t == tier,
                        isLocked = when (t) {
                            VirtualMicTier.SHIZUKU_ADB -> shizukuState != ShizukuState.READY
                            VirtualMicTier.ROOT_MAGISK -> !com.micplugin.service.VirtualMicService.isRooted()
                            else -> false
                        }
                    )
                }
            }

            // ── Plugins ───────────────────────────────────────────────────────
            SettingsSection("Plugins") {
                SettingsActionRow("Rescan Plugin Directories", Icons.Default.Refresh) { vm.rescan() }
                SettingsInfoRow("Scan Paths", "files/plugins/{lv2,clap,vst3}")
            }

            // ── About ─────────────────────────────────────────────────────────
            SettingsSection("About") {
                SettingsInfoRow("Version", "1.0.0")
                SettingsInfoRow("Build", "MicPlugin · Oboe 1.8.1 · CLAP 1.2.1")
                SettingsInfoRow("Native", "C++17 · NDK 26 · AAudio/OpenSL ES")
            }

            // ── Crash Reports ─────────────────────────────────────────────────
            SettingsSection("Crash Reports") {
                if (crashReports.isEmpty()) {
                    Text("No crashes recorded.", fontSize = 12.sp, color = StudioColors.TextMuted)
                } else {
                    crashReports.forEach { file ->
                        Row(Modifier.fillMaxWidth().clickable { viewingCrash = file },
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name.removePrefix("crash_").removeSuffix(".txt"),
                                fontSize = 11.sp, color = StudioColors.MeterRed)
                            Icon(Icons.Default.ChevronRight, null, tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    SettingsActionRow("Clear All Crash Reports", Icons.Default.DeleteForever) {
                        CrashReporter.clearAll(context); crashReports = emptyList()
                    }
                }
            }
        }
    }

    viewingCrash?.let { file ->
        Dialog(onDismissRequest = { viewingCrash = null }) {
            Surface(shape = RoundedCornerShape(12.dp), color = StudioColors.Card,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Crash Report", style = MaterialTheme.typography.titleSmall, color = StudioColors.MeterRed)
                        IconButton(onClick = { viewingCrash = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = StudioColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp)); HorizontalDivider(color = StudioColors.Border); Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(file.readText(), fontSize = 9.sp, color = StudioColors.TextMuted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { file.delete(); crashReports = CrashReporter.getCrashReports(context); viewingCrash = null },
                        modifier = Modifier.align(Alignment.End)) { Text("Delete", color = StudioColors.MeterRed) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Surface(shape = RoundedCornerShape(8.dp), color = StudioColors.Card,
            border = BorderStroke(0.5.dp, StudioColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
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
private fun SettingsActionRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = StudioColors.Accent)
        Icon(icon, null, tint = StudioColors.Accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TierRow(tier: VirtualMicTier, isActive: Boolean, isLocked: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = isActive, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Color(tier.badgeColorHex)))
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(tier.displayName, fontSize = 12.sp,
                color = if (isActive) Color(tier.badgeColorHex) else StudioColors.TextMuted)
        }
        if (isLocked) {
            Icon(Icons.Default.Lock, null, tint = StudioColors.TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}
