package top.ekiz.whitenoise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun NoiseCard(
    name: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier =
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface,
                headlineColor =
                    if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
            ),
        headlineContent = { Text(text = name) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    iconTint
                        ?: if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}
