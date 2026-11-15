package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/* ============================================================
   The update banner.

   A small card pinned above the bottom navigation when a newer
   release is available. Tap "Download" to open the APK in the
   browser; tap "Dismiss" to hide it until next launch.

   It deliberately does NOT auto-download or auto-install — that
   would need REQUEST_INSTALL_PACKAGES permission and a scary
   prompt. Opening the download URL in the browser is the safest
   path and keeps the app sideload-friendly.
   ============================================================ */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

@Composable
fun UpdateBanner(
    versionName: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.toolshed
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)
                .border(1.dp, c.accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Update available — v$versionName",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Text(
                text = "Download the latest APK from the releases page.",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )

            /* The two actions sit on their own line and wrap when the
               screen is narrow. Beside the message they needed ~220dp,
               which a phone in portrait doesn't have. */
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillButton(text = "Download", filled = true, onClick = onDownload)
                PillButton(text = "Dismiss", onClick = onDismiss)
            }
        }
    }
}