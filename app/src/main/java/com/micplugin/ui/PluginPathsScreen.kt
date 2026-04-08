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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.micplugin.plugin.PluginFormat
import com.micplugin.plugin.PluginPathPrefs

@Composable
fun PluginPathsScreen(navController: NavController, vm: AudioViewModel = hiltViewModel()) {
    val context   = LocalContext.current
    val lv2Paths  by vm.lv2Paths.collectAsState()
    val clapPaths by vm.clapPaths.collectAsState()
    val vst3Paths by vm.vst3Paths.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var addingFormat  by remember { mutableStateOf(PluginFormat.LV2) }

    Column(
        Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Surface(color = StudioColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = StudioColors.TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("PLUGIN PATHS", style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary, letterSpacing = 2.sp)
                    Text("Add folders where plugin files (.so / .clap / .lv2) live",
                        fontSize = 10.sp, color = StudioColors.TextMuted)
                }
                IconButton(onClick = { vm.rescan() }) {
                    Icon(Icons.Default.Refresh, "Rescan", tint = StudioColors.Accent)
                }
            }
        }

        // Info card
        Surface(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            shape    = RoundedCornerShape(8.dp),
            color    = Color(0xFF1565C0).copy(alpha = 0.1f),
            border   = BorderStroke(0.5.dp, Color(0xFF1565C0).copy(alpha = 0.4f)),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Where to put plugin files", fontSize = 11.sp,
                    color = Color(0xFF90CAF9), fontFamily = FontFamily.Monospace)
                Text("LV2:  file.so or file.lv2", fontSize = 10.sp, color = StudioColors.TextMuted, fontFamily = FontFamily.Monospace)
                Text("CLAP: file.clap or file.so", fontSize = 10.sp, color = StudioColors.TextMuted, fontFamily = FontFamily.Monospace)
                Text("VST3: file.so", fontSize = 10.sp, color = StudioColors.TextMuted, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("Default internal path (always scanned):", fontSize = 10.sp, color = StudioColors.TextMuted)
                Text("Android/data/com.micplugin/files/plugins/{lv2,clap,vst3}",
                    fontSize = 9.sp, color = StudioColors.Accent, fontFamily = FontFamily.Monospace)
            }
        }

        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Suggested quick-add paths
            PathSection(
                title   = "LV2 Paths",
                color   = Color(0xFF3DFCAC),
                paths   = vm.allPluginPaths(context, PluginFormat.LV2).map { it.absolutePath },
                userPaths = lv2Paths,
                onAdd   = { showAddDialog = true; addingFormat = PluginFormat.LV2 },
                onRemove = { vm.removePluginPath(context, PluginFormat.LV2, it) },
            )
            PathSection(
                title   = "CLAP Paths",
                color   = Color(0xFF7C5CFC),
                paths   = vm.allPluginPaths(context, PluginFormat.CLAP).map { it.absolutePath },
                userPaths = clapPaths,
                onAdd   = { showAddDialog = true; addingFormat = PluginFormat.CLAP },
                onRemove = { vm.removePluginPath(context, PluginFormat.CLAP, it) },
            )
            PathSection(
                title   = "VST3 Paths",
                color   = Color(0xFFFFD700),
                paths   = vm.allPluginPaths(context, PluginFormat.VST3).map { it.absolutePath },
                userPaths = vst3Paths,
                onAdd   = { showAddDialog = true; addingFormat = PluginFormat.VST3 },
                onRemove = { vm.removePluginPath(context, PluginFormat.VST3, it) },
            )

            // Rescan button
            Button(
                onClick  = { vm.rescan() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = StudioColors.Accent),
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rescan All Plugin Paths")
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showAddDialog) {
        AddPathDialog(
            format    = addingFormat,
            onAdd     = { path ->
                vm.addPluginPath(context, addingFormat, path)
                showAddDialog = false
                vm.rescan()
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun PathSection(
    title: String,
    color: Color,
    paths: List<String>,       // all paths including defaults
    userPaths: List<String>,   // only user-added (deletable)
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 11.sp, color = color, letterSpacing = 1.sp)
            IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Add, "Add path", tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Surface(
            shape  = RoundedCornerShape(8.dp),
            color  = StudioColors.Card,
            border = BorderStroke(0.5.dp, StudioColors.Border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                paths.forEach { path ->
                    val isUser = path in userPaths
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isUser) Icons.Default.FolderOpen else Icons.Default.Folder,
                            null, tint = if (isUser) color else StudioColors.TextMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            path, fontSize = 9.sp, color = StudioColors.TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        if (isUser) {
                            IconButton(onClick = { onRemove(path) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = StudioColors.MeterRed,
                                    modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddPathDialog(
    format: PluginFormat,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val suggestions = PluginPathPrefs.suggestedPaths()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = StudioColors.Card,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add ${format.displayName} Path", style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary)

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Folder path", fontSize = 11.sp) },
                    placeholder = { Text("/sdcard/MicUp/plugins/lv2", fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = StudioColors.Accent,
                        unfocusedBorderColor = StudioColors.Border,
                        focusedTextColor     = StudioColors.TextPrimary,
                        unfocusedTextColor   = StudioColors.TextPrimary,
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                )

                // Quick-pick suggestions
                Text("Quick pick:", fontSize = 10.sp, color = StudioColors.TextMuted)
                Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEach { suggestion ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { text = suggestion }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.FolderOpen, null, tint = StudioColors.Accent,
                                modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(suggestion, fontSize = 9.sp, color = StudioColors.TextMuted,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = StudioColors.TextMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick  = { if (text.isNotBlank()) onAdd(text.trim()) },
                        enabled  = text.isNotBlank(),
                        colors   = ButtonDefaults.buttonColors(containerColor = StudioColors.Accent),
                    ) { Text("Add & Rescan") }
                }
            }
        }
    }
}
