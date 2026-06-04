package top.ekiz.whitenoise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import top.ekiz.whitenoise.domain.NoiseType
import top.ekiz.whitenoise.ui.components.NoiseCard
import top.ekiz.whitenoise.ui.theme.spacing

@Composable
fun SoundsScreen(
    innerPadding: PaddingValues, 
    currentType: NoiseType, 
    noises: List<Pair<NoiseType, String>>,
    onTypeSelected: (NoiseType) -> Unit
) {

    LazyColumn(
        contentPadding = PaddingValues(
            start = MaterialTheme.spacing.medium,
            end = MaterialTheme.spacing.medium,
            top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.medium,
            bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium
        ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(noises) { pair ->
            val type = pair.first
            val name = pair.second
            
            val icon = when (type) {
                NoiseType.OCEAN_WAVES -> Icons.Filled.Waves
                NoiseType.WIND -> Icons.Filled.Air
                NoiseType.HEARTBEAT -> Icons.Filled.Favorite
                NoiseType.AIRPLANE_CABIN -> Icons.Filled.Flight
                NoiseType.VELVET -> Icons.Filled.Grain
                NoiseType.WOMB_SOUNDS -> Icons.Filled.ChildCare
                NoiseType.ISOCHRONIC_TONES -> Icons.Filled.GraphicEq
                NoiseType.SOLFEGGIO_FREQUENCIES -> Icons.Filled.Spa
                NoiseType.WHITE, NoiseType.PINK, NoiseType.BROWN, NoiseType.DEEP_BROWN, 
                NoiseType.BLUE, NoiseType.VIOLET, NoiseType.GREY, NoiseType.GREEN, NoiseType.BLACK -> Icons.Filled.Lens
                else -> Icons.Filled.Headset
            }

            val iconTint = when (type) {
                NoiseType.WHITE -> Color(0xFFE6E4E1)
                NoiseType.PINK -> Color(0xFFC6A4A4)
                NoiseType.BROWN -> Color(0xFFA38C7D)
                NoiseType.DEEP_BROWN -> Color(0xFF75665E)
                NoiseType.BLUE -> Color(0xFF92A8B5)
                NoiseType.VIOLET -> Color(0xFFA69FAD)
                NoiseType.GREY -> Color(0xFF9E9E9E)
                NoiseType.GREEN -> Color(0xFF9EAD9B)
                NoiseType.BLACK -> Color(0xFF4A4F54)
                else -> null
            }

            NoiseCard(
                name = name,
                icon = icon,
                iconTint = iconTint,
                isSelected = type == currentType,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}
