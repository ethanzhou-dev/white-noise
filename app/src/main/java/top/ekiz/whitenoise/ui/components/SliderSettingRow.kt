package top.ekiz.whitenoise.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
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
        leadingContent = { Icon(imageVector = icon, contentDescription = contentDescription) },
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
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = MaterialTheme.spacing.small)
                    ) {
                        labels.forEachIndexed { index, label ->
                            val textAlign = when (index) {
                                0 -> TextAlign.Start
                                labels.lastIndex -> TextAlign.End
                                else -> TextAlign.Center
                            }
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                textAlign = textAlign,
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
