package com.micplugin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import androidx.lifecycle.Lifecycle
import android.provider.Settings
import android.os.Bundle
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
import com.micplugin.plugin.PluginImporter
import com.micplugin.service.AudioProcessingService
import com.micplugin.service.ShizukuManager
import com.micplugin.service.ShizukuState
import com.micplugin.service.VirtualMicService
import com.micplugin.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.AndroidEntryPoint
import rikka.shizuku.Shizuku
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var virtualMicService: VirtualMicService

    private val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }.toTypedArray()

    // Guards so the microphone foreground service is started exactly once, and only
    // while the app is actually in the foreground (required on Android 14+).
    private var audioServiceStarted = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Don't start the service here: when this callback fires the app may still be
        // behind a permission dialog / settings screen, i.e. in the background.
        // onResume() starts it once we are back in the foreground and in an eligible state.
        maybeStartAudioService()
    }

    // Shizuku permission result listener
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            virtualMicService.onShizukuReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        permissionLauncher.launch(permissionsToRequest)
        // Handle file opened from file manager
        handleIncomingPlugin(intent)
        requestAllFilesPermission()
        setContent {
            MicPluginTheme {
                val navController = rememberNavController()
                Scaffold(containerColor = StudioColors.Background) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(StudioColors.Background)
                            .padding(padding),
                    ) {
                        NavHost(navController, startDestination = "main") {
                            composable("main") { MainScreen(navController) }
                            composable("plugin_browser") { PluginBrowserScreen(navController) }
                            composable(
                                "plugin_editor/{slotId}",
                                arguments = listOf(navArgument("slotId") { type = NavType.StringType }),
                            ) { back ->
                                val slotId = back.arguments?.getString("slotId") ?: return@composable
                                PluginEditorScreen(navController, slotId)
                            }
                            composable("settings") { SettingsScreen(navController) }
                            composable("plugin_paths") { PluginPathsScreen(navController) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIncomingPlugin(intent)
    }

    private fun handleIncomingPlugin(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != android.content.Intent.ACTION_VIEW) return
        val activity = this
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            val result = withContext(Dispatchers.IO) {
                PluginImporter.importFromUri(activity, uri)
            }
            if (result.success) {
                val vm = androidx.lifecycle.ViewModelProvider(activity)[com.micplugin.ui.AudioViewModel::class.java]
                vm.rescan()
                android.widget.Toast.makeText(
                    activity,
                    "Plugin imported: ${result.pluginDescriptor?.name}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    activity,
                    "Import failed: ${result.error}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = android.content.Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(android.content.Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Starting the microphone foreground service here guarantees the app is in the
        // foreground (RESUMED), which Android 14+ requires for a "microphone" FGS.
        maybeStartAudioService()
    }

    private fun maybeStartAudioService() {
        if (audioServiceStarted) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        audioServiceStarted = true
        AudioProcessingService.start(this)
        // Prompt for battery-optimization exemption only once, after the service is up.
        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= 23) {
            // Don't bother the user if the app is already exempt — otherwise the battery
            // settings screen pops up on every launch / every time the app is reopened.
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) return
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }
}
