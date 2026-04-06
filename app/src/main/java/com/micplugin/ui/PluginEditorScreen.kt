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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.micplugin.plugin.ParamType
import com.micplugin.plugin.PluginParam
import com.micplugin.plugin.PluginSlot
import java.util.UUID

@Composable
fun PluginEditorScreen(
    navController: NavController,
    slotIdStr: String,
    vm: AudioViewModel = hiltViewModel(),
) {
    val slotId  = remember { UUID.fromString(slotIdStr) }
    val slots   by vm.pluginSlots.collectAsState()
    val slot    = slots.find { it.id == slotId }

    if (slot == null) {
        navController.popBackStack()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Surface(color = StudioColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = StudioColors.TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(slot.descriptor.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PluginFormatBadge(slot.descriptor.format)
                        Text("v${slot.descriptor.version}",
                            fontSize = 9.sp, color = StudioColors.TextMuted)
                    }
                }
                // Bypass toggle
                Switch(
                    checked  = slot.enabled,
                    onCheckedChange = { vm.togglePlugin(slot.id) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = StudioColors.Accent,
                        uncheckedTrackColor = StudioColors.Border,
                    ),
                )
            }
        }

        if (slot.descriptor.format.name == "VST3") {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                color    = StudioColors.MeterAmber.copy(alpha = 0.1f),
                shape    = RoundedCornerShape(6.dp),
                border   = BorderStroke(0.5.dp, StudioColors.MeterAmber),
            ) {
                Text("⚠ VST3 Experimental — parameter control may be limited.",
                    fontSize = 10.sp, color = StudioColors.MeterAmber,
                    modifier = Modifier.padding(10.dp))
            }
        }

        // ── Parameter list ────────────────────────────────────────────────────
        if (slot.descriptor.params.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Tune, null,
                        tint = StudioColors.TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No parameters exposed by this plugin.",
                        color = StudioColors.TextMuted)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                slot.descriptor.params.forEach { param ->
                    PluginParamControl(
                        param   = param,
                        value   = slot.paramValues[param.id] ?: param.default,
                        onChange = { vm.setPluginParam(slot.id, param.id, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginParamControl(
    param: PluginParam,
    value: Float,
    onChange: (Float) -> Unit,
) {
    when (param.type) {
        ParamType.BOOL -> {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(param.name, style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextPrimary)
                Switch(
                    checked = value > 0.5f,
                    onCheckedChange = { onChange(if (it) 1f else 0f) },
                    colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent),
                )
            }
        }
        ParamType.ENUM -> {
            // Simple integer step slider for enum
            Column {
                Text(param.name, style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted, fontSize = 10.sp)
                Slider(
                    value = value,
                    onValueChange = onChange,
                    valueRange = param.min..param.max,
                    steps = (param.max - param.min).toInt() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = StudioColors.Accent,
                        activeTrackColor = StudioColors.Accent,
                        inactiveTrackColor = StudioColors.Border,
                    ),
                )
            }
        }
        ParamType.FLOAT -> {
            ParamSlider(
                label = param.name,
                value = value,
                onValueChange = onChange,
                valueRange = param.min..param.max,
            )
        }
    }
}
