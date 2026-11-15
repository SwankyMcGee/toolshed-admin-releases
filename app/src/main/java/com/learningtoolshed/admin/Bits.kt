package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/* Small shared pieces: the icon buttons in the header, and the
   icons that stand in for the site's emoji (⏱ 🧮 💡 🎨). */

@Composable
internal fun HeaderIconButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val c = MaterialTheme.toolshed
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(c.bg)
            .border(1.dp, c.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides c.accent,
            content = icon
        )
    }
}

@Composable internal fun IconRefresh() =
    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = MaterialTheme.toolshed.accent)

@Composable internal fun IconPalette() =
    Icon(Icons.Outlined.Palette, contentDescription = "Palette", tint = MaterialTheme.toolshed.accent)

@Composable internal fun IconGear() =
    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.toolshed.accent)

/* ---------- the status chips ---------- */

val STATUS_ICONS: Map<String, ImageVector> = mapOf(
    "new" to Icons.Outlined.Create,
    "reading" to Icons.Outlined.Visibility,
    "planned" to Icons.Outlined.CheckCircle,
    "done" to Icons.Outlined.Inventory2,
    "shelved" to Icons.Outlined.Archive
)

@Composable
internal fun StatusChip(
    status: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.surface2 else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val icon = STATUS_ICONS[status]
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) c.accent else c.dim,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.text else c.dim
        )
    }
}

/* ---------- text fields, like .field input ---------- */

@Composable
internal fun TextFieldLike(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    leading: (@Composable () -> Unit)? = null
) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.bg)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) {
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides c.dim,
                content = leading
            )
        }
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                color = c.text,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            ),
            cursorBrush = SolidColor(c.accent),
            minLines = minLines
        )
    }
}

/** Search box: the placeholder is the field itself, so there is
    only one border to draw. */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val c = MaterialTheme.toolshed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(c.bg)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = c.dim,
            modifier = Modifier.size(18.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search ideas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.dim
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = c.text,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(c.accent)
            )
        }
    }
}