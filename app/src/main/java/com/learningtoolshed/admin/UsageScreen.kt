package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* ============================================================
   The Usage tab.

   Answers "what is actually being used": page views and time
   spent, rolled up per tool and per resource page. The data is
   the PageViews sheet, grouped by path and day by the backend.

   Sorted by views, biggest first, everywhere. That is the order
   the question is asked in: "what's most used?" The daily chart
   stays chronological, because a trend only reads left-to-right.
   ============================================================ */

@Composable
fun UsageScreen(
    state: InboxState,
    onRefresh: () -> Unit
) {
    val c = MaterialTheme.toolshed
    val usage = remember(state.pageViews) { UsageStats.of(state.pageViews) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Usage",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text
                    )
                    Text(
                        text = "Page views and time spent, from the site's own tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim
                    )
                }
                if (state.loadingUsage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = c.accent
                    )
                } else {
                    HeaderIconButton(label = "Refresh", onClick = onRefresh) { IconRefresh() }
                }
            }
        }

        /* ---------- nothing yet / error ---------- */

        if (state.loadingUsage && state.pageViews.isEmpty()) {
            item {
                Card {
                    Text(
                        text = "Reading the usage sheet…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.dim
                    )
                }
            }
        }

        if (!state.loadingUsage && state.pageViews.isEmpty()) {
            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.usageError ?: "No visits recorded yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        Text(
                            text = "Usage appears here once the site's tracking has recorded a visit. Open a page on the site and come back.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                    }
                }
            }
        }

        if (state.pageViews.isNotEmpty()) {
            /* ---------- headline numbers ---------- */

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        label = "Page views",
                        value = usage.views.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Time spent",
                        value = formatDuration(usage.seconds),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            /* ---------- daily views ---------- */

            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Views per day",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        Text(
                            text = "Last 30 days" + dateRange(usage),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                        BarChart(
                            data = usage.perDay,
                            barColor = c.accent,
                            labelColor = c.dim
                        )
                    }
                }
            }

            /* ---------- tools ---------- */

            if (usage.byTool.isNotEmpty()) {
                item {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "By tool",
                                style = MaterialTheme.typography.titleMedium,
                                color = c.text
                            )
                            Text(
                                text = "Every page belonging to each tool, summed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.dim
                            )
                            UsageBars(
                                stats = usage.byTool,
                                textColor = c.text,
                                trackColor = c.surface2,
                                barColor = c.accent
                            )
                        }
                    }
                }
            }

            /* ---------- resources ---------- */

            val withViews = usage.byResource.filter { it.views > 0 }
            if (withViews.isNotEmpty()) {
                item {
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Resource pages",
                                style = MaterialTheme.typography.titleMedium,
                                color = c.text
                            )
                            Text(
                                text = "Staff guides, most-read first. Average time is per view.",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.dim
                            )
                            ResourceRows(
                                stats = withViews,
                                textColor = c.text,
                                dimColor = c.dim
                            )
                        }
                    }
                }
            }

            /* ---------- top pages ---------- */

            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Most-used pages",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        UsageBars(
                            stats = usage.topPages,
                            textColor = c.text,
                            trackColor = c.surface2,
                            barColor = c.accent
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Time is capped at an hour per visit, so a tab left open overnight counts as an hour, not a day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.dim
                )
            }
        }
    }
}

/** Horizontal bars: label, count, and average time on the right.
    The bar is a fraction of its track rather than a fixed number of
    dp, so its length stays true whatever the screen width is. */
@Composable
private fun UsageBars(
    stats: List<UsageStat>,
    textColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color,
    barColor: androidx.compose.ui.graphics.Color
) {
    val max = stats.maxOfOrNull { it.views }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stats.forEach { stat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.width(92.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stat.views.toFloat() / max)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Text(
                    text = stat.views.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(34.dp)
                )
            }
        }
    }
}

/** Resource pages: one row each, with views and average time. */
@Composable
private fun ResourceRows(
    stats: List<UsageStat>,
    textColor: androidx.compose.ui.graphics.Color,
    dimColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { stat ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.toolshed.surface2)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${stat.views} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = dimColor
                )
                Box(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(stat.averageSeconds) + " avg",
                    style = MaterialTheme.typography.bodySmall,
                    color = dimColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val c = MaterialTheme.toolshed
    Card(modifier = modifier, primary = true) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = c.accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
        }
    }
}

private fun dateRange(usage: Usage): String {
    if (usage.firstDay.isBlank() || usage.lastDay.isBlank()) return ""
    return " · ${usage.firstDay} to ${usage.lastDay}"
}