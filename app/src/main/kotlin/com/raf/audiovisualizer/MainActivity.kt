package com.raf.audiovisualizer

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.raf.audiovisualizer.presentation.theme.AudioVisualizerTheme
import com.raf.audiovisualizer.presentation.visualizer.VisualizerScreen
import com.raf.audiovisualizer.presentation.visualizer.VisualizerViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for Audio Visualizer managing edge-to-edge system bars, runtime
 * `RECORD_AUDIO` permission prompts, Notification Access settings routing, and foreground capture.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: VisualizerViewModel by viewModels()

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AudioVisualizerTheme {
                VisualizerScreen(
                    viewModel = viewModel,
                    onRequestAudioPermission = {
                        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestNotificationAccess = {
                        runCatching {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForegroundChanged(isForeground = true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onStop() {
        viewModel.onForegroundChanged(isForeground = false)
        super.onStop()
    }
}
