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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/* ============================================================
   The analytics screen: "Overview."

   A lazy list of cards, each one chart or number. Computed on-
   device from the ideas already in memory — no extra fetch.

   v1 answers "what's in the inbox now". Status history (for
   "how fast do we close things") is in expanding_the_project.md.
   ============================================================ */

@Composable
fun AnalyticsScreen(
    ideas: List<Idea>,
    onRefresh: () -> Unit
) {
    val stats = remember(ideas) { Stats.of(ideas) }
    val c = MaterialTheme.toolshed

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
                        text = "Overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text
                    )
                    Text(
                        text = "Computed on this device from the ideas in the inbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim
                    )
                }
                PillButton(text = "Refresh", onClick = onRefresh)
            }
        }

        /* ---------- headline numbers ---------- */

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Ideas",
                    value = stats.total.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Unread",
                    value = stats.unread.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        /* ---------- ideas per week ---------- */

        item {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ideas per week",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text
                    )
                    Text(
                        text = "Last 12 weeks",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.dim
                    )
                    BarChart(
                        data = stats.perWeek,
                        barColor = c.accent,
                        labelColor = c.dim
                    )
                }
            }
        }

        /* ---------- tool vs feature ---------- */

        item {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Tool vs feature",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Donut(
                            slices = listOf(
                                Triple("Tool", stats.byType[0].count, c.accent),
                                Triple("Feature", stats.byType[1].count, c.surface2)
                            ),
                            modifier = Modifier.size(96.dp)
                        )
                        Legend(
                            items = listOf(
                                Triple("Tool", stats.byType[0].count, c.accent),
                                Triple("Feature", stats.byType[1].count, c.surface2)
                            ),
                            textColor = c.text
                        )
                    }
                }
            }
        }

        /* ---------- status breakdown ---------- */

        item {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "By status",
                        style = MaterialTheme.typography.titleMedium,
                        color = c.text
                    )
                    HBars(
                        data = stats.byStatus,
                        barColor = c.accent,
                        trackColor = c.surface2,
                        textColor = c.text
                    )
                }
            }
        }

        /* ---------- top tools ---------- */

        if (stats.topTools.isNotEmpty()) {
            item {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Tools with the most feature ideas",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        HBars(
                            data = stats.topTools,
                            barColor = c.accent,
                            trackColor = c.surface2,
                            textColor = c.text
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Charts show what's in the inbox right now. For how fast ideas move from new to done, see the status-history note in expanding_the_project.md.",
                style = MaterialTheme.typography.bodySmall,
                color = c.dim
            )
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