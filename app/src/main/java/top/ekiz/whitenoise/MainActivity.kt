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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "白噪音" else "设置", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { onPlayPauseClicked() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
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
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (selectedTab == 0) {
                SoundsScreen(
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
        }
    }
}

@Composable
fun SoundsScreen(currentType: NoiseType, onTypeSelected: (NoiseType) -> Unit) {
    val noises = listOf(
        Pair(NoiseType.WHITE, "白噪音"),
        Pair(NoiseType.PINK, "粉噪音"),
        Pair(NoiseType.BROWN, "棕噪音"),
        Pair(NoiseType.BLUE, "蓝噪音"),
        Pair(NoiseType.VIOLET, "紫噪音")
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
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

@Composable
fun NoiseCard(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    noiseType: NoiseType,
    isPlaying: Boolean,
    onPlayPauseClicked: () -> Unit,
    onBack: () -> Unit
) {
    val noiseName = when (noiseType) {
        NoiseType.WHITE -> "白噪音"
        NoiseType.PINK -> "粉噪音"
        NoiseType.BROWN -> "棕噪音"
        NoiseType.BLUE -> "蓝噪音"
        NoiseType.VIOLET -> "紫噪音"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在播放", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = noiseName,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = onPlayPauseClicked,
                modifier = Modifier.size(120.dp),
                shape = CircleShape
            ) {
                Text(
                    text = if (isPlaying) "暂停" else "播放",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Volume
        SettingSection(title = "音量大小") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                Slider(value = volume, onValueChange = onVolumeChanged, valueRange = 0f..1f, steps = 9, modifier = Modifier.fillMaxWidth())
            }
        }

        // Sleep Timer
        SettingSection(title = "定时关闭") {
            var customMinutesInput by remember(sleepTimer) { mutableStateOf(if (sleepTimer > 0) sleepTimer.toString() else "") }
            
            OutlinedTextField(
                value = customMinutesInput,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { char -> char.isDigit() }
                    customMinutesInput = filtered
                    val minutes = filtered.toIntOrNull() ?: 0
                    onSleepTimerChanged(minutes)
                },
                label = { Text("定时关闭 (分钟)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Text("分钟", modifier = Modifier.padding(end = 16.dp)) },
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onStartTimer,
                    enabled = !isTimerRunning && (sleepTimer > 0)
                ) {
                    Text("开始")
                }
                OutlinedButton(
                    onClick = onPauseTimer,
                    enabled = isTimerRunning
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
                    Text("剩余时间: ${"%02d:%02d".format(remainingMinutes, remainingSeconds)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // Balance
        SettingSection(title = "左右耳平衡") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                Slider(value = balance, onValueChange = onBalanceChanged, valueRange = -1f..1f, steps = 9, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("偏左", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("居中", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("偏右", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Stereo Width
        SettingSection(title = "立体声宽度") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)))
                Slider(value = stereoWidth, onValueChange = onStereoWidthChanged, valueRange = 0f..1f, steps = 9, modifier = Modifier.fillMaxWidth())
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("单声道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("全立体", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Theme Mode
        SettingSection(title = "外观主题") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("System" to "跟随系统", "Light" to "浅色", "Dark" to "深色").forEach { (mode, label) ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChanged(mode) },
                        label = { Text(label) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(88.dp)) // padding for FAB
    }
}

@Composable
fun SettingSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
