package top.ekiz.whitenoise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import top.ekiz.whitenoise.ui.theme.spacing
import top.ekiz.whitenoise.ui.components.SliderSettingRow
import java.util.Calendar

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
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
        )
        
        SliderSettingRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "音量大小",
            value = volume,
            onValueChange = onVolumeChanged,
            valueRange = 0f..1f
        )
        
        SliderSettingRow(
            icon = Icons.Filled.Tune,
            contentDescription = "左右耳平衡",
            value = balance,
            onValueChange = onBalanceChanged,
            valueRange = -1f..1f,
            labels = listOf("偏左", "居中", "偏右")
        )
        
        HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium))

        
        Text(
            text = "定时关闭",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small)
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
        
        val calendar = Calendar.getInstance()
        if (isTimerRunning || remainingTimeMillis > 0L) {
            calendar.timeInMillis = System.currentTimeMillis() + remainingTimeMillis
        } else {
            calendar.timeInMillis = System.currentTimeMillis() + sleepTimer * 60000L
        }
        val targetHour = calendar.get(Calendar.HOUR_OF_DAY)
        val targetMinute = calendar.get(Calendar.MINUTE)
        
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
                        val now = Calendar.getInstance()
                        val selected = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                        }
                        if (selected.before(now)) {
                            selected.add(Calendar.DAY_OF_YEAR, 1)
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
        
        Column(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium)) {
            val isPaused = !isTimerRunning && remainingTimeMillis > 0L

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
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
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("剩余时间: ${"%02d:%02d".format(remainingMinutes, remainingSeconds)}", style = MaterialTheme.typography.bodyMedium)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium))

        Text(
            text = "高级功能",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.small)
        )

        ListItem(
            headlineContent = { Text("3D 空间音频 (双耳渲染)") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.SurroundSound,
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

        HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.spacing.medium))

        Text(
            text = "外观与显示",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium, top = MaterialTheme.spacing.small, bottom = MaterialTheme.spacing.medium)
        )
        
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
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
        
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.extraLarge))
    }
}
