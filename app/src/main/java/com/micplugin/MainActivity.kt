package com.micplugin

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.micplugin.service.AudioProcessingService
import com.micplugin.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) startAudioService()
        else requestBatteryOptimizationExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionLauncher.launch(permissionsToRequest)
        setContent {
            MicPluginTheme {
                val navController = rememberNavController()
                Scaffold(
                    containerColor = StudioColors.Background,
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(StudioColors.Background)
                            .padding(padding),
                    ) {
                        NavHost(navController, startDestination = "main") {
                            composable("main") {
                                MainScreen(navController)
                            }
                            composable("plugin_browser") {
                                PluginBrowserScreen(navController)
                            }
                            composable(
                                "plugin_editor/{slotId}",
                                arguments = listOf(navArgument("slotId") {
                                    type = NavType.StringType
                                }),
                            ) { back ->
                                val slotId = back.arguments?.getString("slotId") ?: return@composable
                                PluginEditorScreen(navController, slotId)
                            }
                            composable("settings") {
                                SettingsScreen(navController)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startAudioService() {
        AudioProcessingService.start(this)
        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= 23) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }
}
