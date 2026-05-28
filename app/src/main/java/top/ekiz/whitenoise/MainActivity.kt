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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
            top.ekiz.whitenoise.ui.theme.WhiteNoiseTheme {
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
    
    // Local states
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var noiseType by remember { mutableStateOf(NoiseType.WHITE) }
    
    // Sync local state from DataStore initially
    LaunchedEffect(initialVolume, initialNoiseType) {
        volume = initialVolume
        noiseType = initialNoiseType
        // If service is running, update it
        service?.setVolume(volume)
        service?.setNoiseType(noiseType)
    }

    // Polling isPlaying state as service can be stopped from notification
    LaunchedEffect(isBound, service) {
        while (true) {
            if (isBound && service != null) {
                isPlaying = service.isPlaying()
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
            // Apply current volume and type before starting just in case
            service?.setVolume(volume)
            service?.setNoiseType(noiseType)
            service?.startPlayback()
        }
        isPlaying = service?.isPlaying() == true
    }

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("噪音生成器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(androidx.compose.material.icons.Icons.Filled.Home, contentDescription = "声音") },
                    label = { Text("声音") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { onPlayPauseClicked() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = if (isPlaying) "停止" else "播放",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
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
                    onVolumeChanged = { newVolume ->
                        volume = newVolume
                        service?.setVolume(newVolume)
                        coroutineScope.launch {
                            dataStore.saveVolume(newVolume)
                        }
                    }
                )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "选择噪音种类",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        noises.forEach { (type, name) ->
            NoiseCard(
                name = name,
                isSelected = type == currentType,
                onClick = { onTypeSelected(type) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun NoiseCard(name: String, isSelected: Boolean, onClick: () -> Unit) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onClick() },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(volume: Float, onVolumeChanged: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "音量设置: ${(volume * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Slider(
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
