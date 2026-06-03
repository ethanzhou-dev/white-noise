package top.ekiz.whitenoise.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import top.ekiz.whitenoise.NoiseType
import top.ekiz.whitenoise.ui.NoiseUiState
import top.ekiz.whitenoise.ui.NoiseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(uiState: NoiseUiState, viewModel: NoiseViewModel) {
    val context = LocalContext.current
    
    // Permission handling for notifications (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (isGranted) {
                viewModel.togglePlayPause()
            }
        }
    )

    fun onPlayPauseClicked() {
        if (!hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.togglePlayPause()
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    val colorNoises = listOf(
        Pair(NoiseType.BLACK, "黑噪音"),
        Pair(NoiseType.DEEP_BROWN, "深棕噪音"),
        Pair(NoiseType.BROWN, "棕噪音"),
        Pair(NoiseType.PINK, "粉噪音"),
        Pair(NoiseType.WHITE, "白噪音"),
        Pair(NoiseType.BLUE, "蓝噪音"),
        Pair(NoiseType.VIOLET, "紫噪音"),
        Pair(NoiseType.GREEN, "绿噪音"),
        Pair(NoiseType.GREY, "灰噪音")
    )

    val otherNoises = listOf(
        Pair(NoiseType.VELVET, "天鹅绒噪音"),
        Pair(NoiseType.OCEAN_WAVES, "海浪声"),
        Pair(NoiseType.BINAURAL_BEATS, "双耳节拍"),
        Pair(NoiseType.WIND, "风声"),
        Pair(NoiseType.AIRPLANE_CABIN, "飞机机舱"),
        Pair(NoiseType.HEARTBEAT, "心跳声")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when (selectedTab) {
                    0 -> "颜色噪音"
                    1 -> "其他声音"
                    else -> "设置"
                }) },
                actions = {
                    if (selectedTab == 0 || selectedTab == 1) {
                        IconButton(onClick = { onPlayPauseClicked() }) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "暂停" else "播放"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, contentDescription = "颜色") },
                    label = { Text("颜色") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.List, contentDescription = "其他") },
                    label = { Text("其他") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        if (selectedTab == 0) {
            SoundsScreen(
                innerPadding = paddingValues,
                currentType = uiState.noiseType,
                noises = colorNoises,
                onTypeSelected = { viewModel.setNoiseType(it) }
            )
        } else if (selectedTab == 1) {
            SoundsScreen(
                innerPadding = paddingValues,
                currentType = uiState.noiseType,
                noises = otherNoises,
                onTypeSelected = { viewModel.setNoiseType(it) }
            )
        } else {
            SettingsScreen(
                innerPadding = paddingValues,
                volume = uiState.volume,
                onVolumeChanged = { viewModel.setVolume(it) },
                balance = uiState.balance,
                onBalanceChanged = { viewModel.setBalance(it) },
                sleepTimer = uiState.sleepTimerMinutes,
                onSleepTimerChanged = { viewModel.setSleepTimerMinutes(it) },
                isTimerRunning = uiState.isTimerRunning,
                onStartTimer = { viewModel.startTimer() },
                onPauseTimer = { viewModel.pauseTimer() },
                onCancelTimer = { viewModel.cancelTimer() },
                remainingTimeMillis = uiState.activeRemainingMillis,
                totalTimeMillis = uiState.totalTimerMillis,
                themeMode = uiState.themeMode,
                onThemeModeChanged = { viewModel.setThemeMode(it) },
                isSpatialAudioEnabled = uiState.isSpatialAudioEnabled,
                onSpatialAudioEnabledChanged = { viewModel.setSpatialAudioEnabled(it) }
            )
        }
    }
}
