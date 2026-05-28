package top.ekiz.whitenoise

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.viewModels
import top.ekiz.whitenoise.ui.NoiseViewModel
import top.ekiz.whitenoise.ui.NoiseUiState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: NoiseViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, NoiseService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                mediaController = controllerFuture?.get()
                viewModel.setMediaController(mediaController)
                mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        viewModel.updatePlaybackState()
                    }
                })
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onStop() {
        super.onStop()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            
            if (uiState.isLoading) {
                return@setContent
            }
            
            val darkTheme = when (uiState.themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            
            top.ekiz.whitenoise.ui.theme.WhiteNoiseTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}

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

@Composable
fun SoundsScreen(innerPadding: PaddingValues, currentType: NoiseType, onTypeSelected: (NoiseType) -> Unit) {
    val noises = listOf(
        Pair(NoiseType.BLACK, "黑噪音"),
        Pair(NoiseType.BROWN, "棕噪音"),
        Pair(NoiseType.PINK, "粉噪音"),
        Pair(NoiseType.WHITE, "白噪音"),
        Pair(NoiseType.BLUE, "蓝噪音"),
        Pair(NoiseType.VIOLET, "紫噪音"),
        Pair(NoiseType.GREEN, "绿噪音"),
        Pair(NoiseType.GREY, "灰噪音")
    )

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innerPadding.calculateTopPadding() + 16.dp,
            bottom = innerPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(noises) { pair ->
            val type = pair.first
            val name = pair.second
            NoiseCard(
                name = name,
                isSelected = type == currentType,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseCard(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Headset,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    volume: Float, onVolumeChanged: (Float) -> Unit,
    balance: Float, onBalanceChanged: (Float) -> Unit,
    sleepTimer: Int, onSleepTimerChanged: (Int) -> Unit,
    isTimerRunning: Boolean, onStartTimer: () -> Unit, onPauseTimer: () -> Unit, onCancelTimer: () -> Unit,
    remainingTimeMillis: Long, totalTimeMillis: Long,
    themeMode: String, onThemeModeChanged: (String) -> Unit,
    isSpatialAudioEnabled: Boolean, onSpatialAudioEnabledChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding()
            )
    ) {
        Text(
            text = "音频控制",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
        
        SliderSettingRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "音量大小",
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..1f
        )
        
        SliderSettingRow(
            icon = Icons.Filled.Headset,
            contentDescription = "左右耳平衡",
            value = balance,
            onValueChange = onBalanceChanged,
            valueRange = -1f..1f,
            labels = listOf("偏左", "居中", "偏右")
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // 定时关闭模块
        Text(
            text = "定时关闭",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )
        
        var showTimePicker by remember { mutableStateOf(false) }
        
        SliderSettingRow(
            icon = Icons.Filled.Schedule,
            contentDescription = "倒计时长: $sleepTimer 分钟",
            value = sleepTimer.coerceAtMost(120).toFloat(),
            onValueChange = { onSleepTimerChanged(it.toInt()) },
            valueRange = 0f..120f,
            labels = listOf("0", "60", "120"),
            steps = 11
        )
        
        val calendar = java.util.Calendar.getInstance()
        if (isTimerRunning || remainingTimeMillis > 0L) {
            calendar.timeInMillis = System.currentTimeMillis() + remainingTimeMillis
        } else {
            calendar.timeInMillis = System.currentTimeMillis() + sleepTimer * 60000L
        }
        val targetHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val targetMinute = calendar.get(java.util.Calendar.MINUTE)
        
        ListItem(
            headlineContent = { Text("指定时间关闭") },
            supportingContent = { Text((if (isTimerRunning) "将在 %02d:%02d 自动关闭 (运行中)" else "将在 %02d:%02d 自动关闭").format(targetHour, targetMinute)) },
            leadingContent = { Icon(Icons.Filled.Schedule, contentDescription = null) },
            trailingContent = {
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("设定时间")
                }
            }
        )
        
        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = targetHour,
                initialMinute = targetMinute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val now = java.util.Calendar.getInstance()
                        val selected = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(java.util.Calendar.MINUTE, timePickerState.minute)
                            set(java.util.Calendar.SECOND, 0)
                        }
                        if (selected.before(now)) {
                            selected.add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                        val diffMinutes = ((selected.timeInMillis - now.timeInMillis) / 60000L).toInt().coerceAtLeast(1)
                        onSleepTimerChanged(diffMinutes)
                        showTimePicker = false
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                },
                text = {
                    TimePicker(state = timePickerState)
                }
            )
        }
        
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            val isPaused = !isTimerRunning && remainingTimeMillis > 0L

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartTimer,
                    enabled = !isTimerRunning && (sleepTimer > 0 || remainingTimeMillis > 0L),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isPaused) "继续" else "开始")
                }
                if (isTimerRunning) {
                    OutlinedButton(
                        onClick = onPauseTimer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("暂停")
                    }
                } else if (isPaused) {
                    OutlinedButton(
                        onClick = onCancelTimer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                } else {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("暂停")
                    }
                }
            }

            if (isTimerRunning || remainingTimeMillis > 0L) {
                val totalForProgress = if (totalTimeMillis > 0L) totalTimeMillis else remainingTimeMillis
                val progress = if (totalForProgress > 0) (remainingTimeMillis.toFloat() / totalForProgress.toFloat()).coerceIn(0f, 1f) else 0f
                val remainingMinutes = remainingTimeMillis / 60000
                val remainingSeconds = (remainingTimeMillis % 60000) / 1000
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("剩余时间: ${"%02d:%02d".format(remainingMinutes, remainingSeconds)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            text = "高级功能",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        )

        ListItem(
            headlineContent = { Text("3D 空间音频 (双耳渲染)") },
            supportingContent = { Text("开启后，白噪音会在脑海周围缓慢环绕，增强沉浸感和放松效果。") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Headset,
                    contentDescription = "空间音频"
                )
            },
            trailingContent = {
                Switch(
                    checked = isSpatialAudioEnabled,
                    onCheckedChange = onSpatialAudioEnabledChanged
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            text = "外观与显示",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
        )
        
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val options = listOf("System" to "跟随系统", "Light" to "浅色", "Dark" to "深色")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChanged(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SliderSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    labels: List<String>? = null,
    steps: Int = 9
) {
    ListItem(
        headlineContent = { Text(contentDescription) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription
            )
        },
        supportingContent = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth()
                )
                if (labels != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        labels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}


