package top.ekiz.whitenoise.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.ekiz.whitenoise.ui.theme.spacing

@Composable
fun SliderSettingRow(
    icon: ImageVector,
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
            Column(modifier = Modifier.padding(top = MaterialTheme.spacing.small)) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                    modifier = Modifier.fillMaxWidth()
                )
                if (labels != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small),
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
