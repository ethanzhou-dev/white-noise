package top.ekiz.whitenoise

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var noiseService: NoiseService? = null
    private var isBound = mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as NoiseService.LocalBinder
            noiseService = binder.getService()
            isBound.value = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound.value = false
            noiseService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, NoiseService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound.value) {
            unbindService(connection)
            isBound.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dataStore = SettingsDataStore(this)
        
        setContent {
            val themeMode by dataStore.themeModeFlow.collectAsState(initial = "System")
            val darkTheme = when (themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            
            top.ekiz.whitenoise.ui.theme.WhiteNoiseTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen(isBound.value, noiseService, dataStore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(isBound: Boolean, service: NoiseService?, dataStore: SettingsDataStore) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // States from DataStore
    val initialVolume by dataStore.volumeFlow.collectAsState(initial = 0.5f)
    val initialNoiseType by dataStore.noiseTypeFlow.collectAsState(initial = NoiseType.WHITE)
    val initialBalance by dataStore.balanceFlow.collectAsState(initial = 0f)
    val initialStereoWidth by dataStore.stereoWidthFlow.collectAsState(initial = 1f)
    val initialSleepTimer by dataStore.sleepTimerFlow.collectAsState(initial = 0)
    val initialThemeMode by dataStore.themeModeFlow.collectAsState(initial = "System")
    
    // Local states
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var balance by remember { mutableFloatStateOf(0f) }
    var stereoWidth by remember { mutableFloatStateOf(1f) }
    var noiseType by remember { mutableStateOf(NoiseType.WHITE) }
    
    // Sync local state from DataStore initially
    LaunchedEffect(initialVolume, initialNoiseType, initialBalance, initialStereoWidth) {
        volume = initialVolume
        noiseType = initialNoiseType
        balance = initialBalance
        stereoWidth = initialStereoWidth
        
        service?.setVolume(volume)
        service?.setNoiseType(noiseType)
        service?.setBalance(balance)
        service?.setStereoWidth(stereoWidth)
    }

    var remainingTimeMillis by remember { mutableLongStateOf(0L) }
    var totalTimeMillis by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }
    
    LaunchedEffect(isBound, service) {
        while (true) {
            if (isBound && service != null) {
                isPlaying = service.isPlaying()
                remainingTimeMillis = service.getSleepRemainingMillis()
                totalTimeMillis = service.getSleepTotalMillis()
                isTimerRunning = service.isSleepTimerRunning()
            }
            delay(500)
        }
    }

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
        
        if (isPlaying) {
            service?.stopPlayback()
        } else {
            val startIntent = Intent(context, NoiseService::class.java)
            ContextCompat.startForegroundService(context, startIntent)
            
            // Apply all current states before starting
            service?.setVolume(volume)
            service?.setNoiseType(noiseType)
            service?.setBalance(balance)
            service?.setStereoWidth(stereoWidth)
            service?.setSleepTimer(initialSleepTimer)
            if (initialSleepTimer > 0) {
                service?.startSleepTimer()
            }
            
            service?.startPlayback()
        }
        isPlaying = service?.isPlaying() == true
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
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放"
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
                currentType = noiseType,
                onTypeSelected = { newType ->
                    noiseType = newType
                    service?.setNoiseType(newType)
                    coroutineScope.launch {
                        dataStore.saveNoiseType(newType)
                    }
                }
            )
        } else {
            SettingsScreen(
                innerPadding = paddingValues,
                volume = volume,
                onVolumeChanged = {
                    volume = it
                    service?.setVolume(it)
                    coroutineScope.launch { dataStore.saveVolume(it) }
                },
                balance = balance,
                onBalanceChanged = {
                    balance = it
                    service?.setBalance(it)
                    coroutineScope.launch { dataStore.saveBalance(it) }
                },
                stereoWidth = stereoWidth,
                onStereoWidthChanged = {
                    stereoWidth = it
                    service?.setStereoWidth(it)
                    coroutineScope.launch { dataStore.saveStereoWidth(it) }
                },
                sleepTimer = initialSleepTimer,
                onSleepTimerChanged = {
                    coroutineScope.launch { dataStore.saveSleepTimer(it) }
                    service?.setSleepTimer(it)
                },
                isTimerRunning = isTimerRunning,
                onStartTimer = { service?.startSleepTimer() },
                onPauseTimer = { service?.pauseSleepTimer() },
                remainingTimeMillis = remainingTimeMillis,
                totalTimeMillis = totalTimeMillis,
                themeMode = initialThemeMode,
                onThemeModeChanged = {
                    coroutineScope.launch { dataStore.saveThemeMode(it) }
                }
            )
        }
    }
}

@Composable
fun SoundsScreen(innerPadding: PaddingValues, currentType: NoiseType, onTypeSelected: (NoiseType) -> Unit) {
    val noises = listOf(
        Pair(NoiseType.WHITE, "白噪音"),
        Pair(NoiseType.PINK, "粉噪音"),
        Pair(NoiseType.BROWN, "棕噪音"),
        Pair(NoiseType.BLUE, "蓝噪音"),
        Pair(NoiseType.VIOLET, "紫噪音")
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
        items(noises) { (type, name) ->
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
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    innerPadding: PaddingValues,
    volume: Float, onVolumeChanged: (Float) -> Unit,
    balance: Float, onBalanceChanged: (Float) -> Unit,
    stereoWidth: Float, onStereoWidthChanged: (Float) -> Unit,
    sleepTimer: Int, onSleepTimerChanged: (Int) -> Unit,
    isTimerRunning: Boolean, onStartTimer: () -> Unit, onPauseTimer: () -> Unit,
    remainingTimeMillis: Long, totalTimeMillis: Long,
    themeMode: String, onThemeModeChanged: (String) -> Unit
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
        // 音频控制模块
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
        
        SliderSettingRow(
            icon = Icons.Filled.SurroundSound,
            contentDescription = "立体声宽度",
            value = stereoWidth,
            onValueChange = onStereoWidthChanged,
            valueRange = 0f..1f,
            labels = listOf("单声道", "全立体")
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 定时关闭模块
        Text(
            text = "定时关闭",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        )
        
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            var customMinutesInput by remember(sleepTimer) { mutableStateOf(if (sleepTimer > 0) sleepTimer.toString() else "") }
            
            OutlinedTextField(
                value = customMinutesInput,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { char -> char.isDigit() }
                    customMinutesInput = filtered
                    val minutes = filtered.toIntOrNull() ?: 0
                    onSleepTimerChanged(minutes)
                },
                label = { Text("定时关闭时长") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Text("分钟", modifier = Modifier.padding(end = 16.dp)) },
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartTimer,
                    enabled = !isTimerRunning && (sleepTimer > 0),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("开始")
                }
                OutlinedButton(
                    onClick = onPauseTimer,
                    enabled = isTimerRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("暂停")
                }
            }

            if (totalTimeMillis > 0L) {
                val progress = if (totalTimeMillis > 0) remainingTimeMillis.toFloat() / totalTimeMillis.toFloat() else 0f
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

        // 外观与显示模块
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
    labels: List<String>? = null
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
                    steps = 9,
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
