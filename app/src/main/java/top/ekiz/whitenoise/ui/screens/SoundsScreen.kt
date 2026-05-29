package top.ekiz.whitenoise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.ekiz.whitenoise.NoiseType
import top.ekiz.whitenoise.ui.components.NoiseCard

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
