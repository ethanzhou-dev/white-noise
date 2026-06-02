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
fun SoundsScreen(
    innerPadding: PaddingValues, 
    currentType: NoiseType, 
    noises: List<Pair<NoiseType, String>>,
    onTypeSelected: (NoiseType) -> Unit
) {

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
