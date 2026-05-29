package top.ekiz.whitenoise.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import top.ekiz.whitenoise.ui.NoiseUiState
import top.ekiz.whitenoise.ui.NoiseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(uiState: NoiseUiState, viewModel: NoiseViewModel) {
    val context = LocalContext.current
    
    // Permission handling for notifications (Android 13+)
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
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
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.togglePlayPause()
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "白噪音" else "设置") },
                actions = {
                    if (selectedTab == 0) {
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
                    icon = { Icon(Icons.Filled.Home, contentDescription = "声音") },
                    label = { Text("声音") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { paddingValues ->
        if (selectedTab == 0) {
            SoundsScreen(
                innerPadding = paddingValues,
                currentType = uiState.noiseType,
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
