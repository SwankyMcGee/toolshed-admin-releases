package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/* ============================================================
   The update cards.

   Two states of the same story:

   - UpdateBanner: a newer release exists. One tap starts the download.
   - DownloadProgressCard: the download is running. No tap needed — the
     installer opens itself when the bytes land.
   - UpdateFailedCard: it didn't, and says why.

   The download is not auto-started, because an app quietly pulling an
   APK over someone's mobile data is worse than one tap. Everything
   after that tap is automatic: no browser, no file manager, no digging
   the APK out of Downloads.

   What the user still sees at the end is Android's own install prompt,
   with its Install button. That one is not ours to remove: Android
   requires the user to approve an install that didn't come from Play,
   and the package installer enforces it no matter how the app asks.
   Play Protect may scan it too, for the same reason.
   ============================================================ */

/** "Downloading 43% — the installer will open on its own." */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DownloadProgressCard(
    onCancel: () -> Unit
) {
    val c = MaterialTheme.toolshed
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
            text = "Downloading the update…",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = c.accent,
            trackColor = c.surface2
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "The installer opens when it finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim,
                modifier = Modifier.weight(1f)
            )
            PillButton(text = "Cancel", onClick = onCancel)
        }
    }
}

@Composable
fun UpdateFailedCard(
    reason: String,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.toolshed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "The update didn't download",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = c.dim
        )
        PillButton(text = "Dismiss", onClick = onDismiss)
    }
}

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