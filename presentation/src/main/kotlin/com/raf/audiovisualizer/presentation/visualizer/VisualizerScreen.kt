package com.raf.audiovisualizer.presentation.visualizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.audiovisualizer.presentation.R
import com.raf.audiovisualizer.presentation.visualizer.components.AlbumHeaderCard
import com.raf.audiovisualizer.presentation.visualizer.components.AudioMetricsPanel
import com.raf.audiovisualizer.presentation.visualizer.components.FftSpectrumCanvas
import com.raf.audiovisualizer.presentation.visualizer.components.RuntimePermissionCard
import com.raf.audiovisualizer.presentation.visualizer.components.WaveformOscilloscopeCanvas

/**
 * Main Audio Visualizer screen featuring a clean ambient backdrop, top album & ambient showcase
 * card with selectable AGSL & rhythm-border animations, and a 3-tab bottom bar switching between
 * real-time FFT, Waveform, and Detailed Metrics.
 */
@Composable
fun VisualizerScreen(
    modifier: Modifier = Modifier,
    viewModel: VisualizerViewModel = hiltViewModel(),
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    val primaryColor by animateColorAsState(
        targetValue = Color(uiState.track.primaryColorArgb),
        animationSpec = tween(durationMillis = 520),
        label = "ambientPrimary",
    )
    val secondaryColor by animateColorAsState(
        targetValue = Color(uiState.track.secondaryColorArgb),
        animationSpec = tween(durationMillis = 520),
        label = "ambientSecondary",
    )
    val ambientSurface by animateColorAsState(
        targetValue = Color(uiState.track.ambientSurfaceArgb),
        animationSpec = tween(durationMillis = 620),
        label = "ambientSurface",
    )

    val ambientBackground = Brush.verticalGradient(
        colors = listOf(
            ambientSurface,
            Color(0xFF0C0C14),
            Color(0xFF07070C),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBackground),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0E0E17).copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = primaryColor,
                        indicatorColor = primaryColor,
                        unselectedIconColor = Color.White.copy(alpha = 0.65f),
                        unselectedTextColor = Color.White.copy(alpha = 0.65f),
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == VisualizerTab.FFT,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectTab(VisualizerTab.FFT)
                        },
                        icon = { Icon(imageVector = Icons.Rounded.BarChart, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.tab_fft)) },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == VisualizerTab.WAVEFORM,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectTab(VisualizerTab.WAVEFORM)
                        },
                        icon = { Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.tab_waveform)) },
                        colors = itemColors,
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == VisualizerTab.METRICS,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            viewModel.selectTab(VisualizerTab.METRICS)
                        },
                        icon = { Icon(imageVector = Icons.Rounded.Analytics, contentDescription = null) },
                        label = { Text(text = stringResource(R.string.tab_metrics)) },
                        colors = itemColors,
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AlbumHeaderCard(
                    track = uiState.track,
                    telemetry = uiState.telemetry,
                    animationParams = uiState.animationParams,
                    selectedEffect = uiState.selectedCardEffect,
                    dustTriggerCount = uiState.dustTriggerCount,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    hasNotificationAccess = uiState.hasNotificationAccess,
                    onSelectEffect = viewModel::selectCardEffect,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (!uiState.hasAudioPermission) {
                    RuntimePermissionCard(
                        primaryColor = primaryColor,
                        onRequestAudioPermission = onRequestAudioPermission,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    AnimatedContent(
                        targetState = uiState.selectedTab,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "visualizerTabContent",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) { tab ->
                        when (tab) {
                            VisualizerTab.FFT -> FftSpectrumCanvas(
                                telemetry = uiState.telemetry,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                            VisualizerTab.WAVEFORM -> WaveformOscilloscopeCanvas(
                                telemetry = uiState.telemetry,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                            VisualizerTab.METRICS -> AudioMetricsPanel(
                                telemetry = uiState.telemetry,
                                animationParams = uiState.animationParams,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
