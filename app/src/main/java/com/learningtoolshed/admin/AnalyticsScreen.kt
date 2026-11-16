package com.learningtoolshed.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

   A grid of cards. Each card is one chart or number, computed on-
   device from the ideas already in memory — no extra fetch.

   The grid is adaptive: one column on a phone, two or three on a
   tablet, with the intro and the footnotes spanning the full width.
   Cards with charts are short enough that a second column doesn't
   leave holes.

   "Ideas per tool" has one row per tool in site-manifest.json, so a
   new tool appears here on the next sync with no app release. Ideas
   naming a tool the registry doesn't know get their own card at the
   bottom — the same to-do list the Usage tab shows for unknown pages.

   v1 answers "what's in the inbox now". Status history (for
   "how fast do we close things") is in expanding_the_project.md.
   ============================================================ */

@Composable
fun AnalyticsScreen(
    ideas: List<Idea>,
    manifest: SiteManifest,
    onRefresh: () -> Unit
) {
    val stats = remember(ideas, manifest) { Stats.of(ideas, manifest) }
    val c = MaterialTheme.toolshed

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp, top = 10.dp, bottom = 28.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
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
                HeaderIconButton(label = "Refresh", onClick = onRefresh) { IconRefresh() }
            }
        }

        /* ---------- headline numbers ---------- */

        item { StatCard("Ideas", stats.total.toString()) }
        item { StatCard("Unread", stats.unread.toString()) }

        /* ---------- ideas per week ---------- */

        item(span = { GridItemSpan(maxLineSpan) }) {
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

        item(span = { GridItemSpan(maxLineSpan) }) {
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

        item(span = { GridItemSpan(maxLineSpan) }) {
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

        /* ---------- every tool in the registry ----------
           Generated from the manifest. A zero row is deliberate: it
           says nobody has asked for that tool yet, which is a
           different fact from the tool not existing. */

        if (stats.byTool.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Ideas per tool",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        Text(
                            text = "Every tool in the site registry, biggest first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                        HBars(
                            data = stats.byTool,
                            barColor = c.accent,
                            trackColor = c.surface2,
                            textColor = c.text
                        )
                    }
                }
            }
        }

        /* ---------- ideas naming a tool we don't know ----------
           Either the site is missing a tool someone wants, or the
           name in the form doesn't match the manifest. Both are
           worth seeing, and neither should vanish. */

        if (stats.hasUnmapped) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(primary = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Not in the registry",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.text
                        )
                        Text(
                            text = "These ideas name a tool the site doesn't list — either a tool someone is asking for, or a name that doesn't match site-manifest.json.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.dim
                        )
                        HBars(
                            data = stats.unmapped,
                            barColor = c.accent,
                            trackColor = c.surface2,
                            textColor = c.text
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
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