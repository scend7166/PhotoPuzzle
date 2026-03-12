package com.photopuzzle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.photopuzzle.app.data.models.SizeStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {}
            )
        }
    ) { padding ->
        val s = stats
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── Summary banner ────────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Headline: puzzles solved
                            Column {
                                Text(
                                    "${s?.totalSolved ?: 0}",
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 48.sp
                                )
                                Text(
                                    "puzzles solved",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            // Banner stats row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BannerStat(
                                    icon = Icons.Default.Timer,
                                    label = "Total time",
                                    value = formatTotalTime(s?.totalTimeSeconds ?: 0L),
                                    modifier = Modifier.weight(1f)
                                )
                                BannerStat(
                                    icon = Icons.Default.LocalFireDepartment,
                                    label = "Day streak",
                                    value = "${s?.currentStreakDays ?: 0}",
                                    modifier = Modifier.weight(1f)
                                )
                                BannerStat(
                                    icon = Icons.Default.EmojiEvents,
                                    label = "Avg time",
                                    value = formatTime((s?.averageCompletionSeconds ?: 0.0).toLong()),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // ── Per-size breakdown ────────────────────────────────────────
                item {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "By Puzzle Size",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                items(s?.statsBySize ?: emptyList()) { sizeStats ->
                    SizeStatRow(sizeStats, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
        }
    }
}

// ── Banner stat pill ──────────────────────────────────────────────────────────

@Composable
fun BannerStat(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Per-size row ──────────────────────────────────────────────────────────────

@Composable
fun SizeStatRow(stats: SizeStats, modifier: Modifier = Modifier) {
    Card(shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${stats.pieceCount} pieces", fontWeight = FontWeight.SemiBold)
                Text(
                    "${stats.timesSolved} solved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (stats.bestTimeSeconds != null) formatTime(stats.bestTimeSeconds) else "0m",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 14.sp
                    )
                    Text(
                        "best",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatTime(stats.averageCompletionSeconds.toLong()),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Text(
                        "avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatTotalTime(stats.totalPlayTimeSeconds, hasActivity = stats.timesSolved > 0 || stats.totalPlayTimeSeconds > 0),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Text(
                        "total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Formats a total duration into a human-readable string e.g. "2h 14m" or "45m". */
fun formatTotalTime(totalSeconds: Long, hasActivity: Boolean = true): String {
    if (totalSeconds == 0L || !hasActivity) return "0m"
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else        -> "<1m"
    }
}
