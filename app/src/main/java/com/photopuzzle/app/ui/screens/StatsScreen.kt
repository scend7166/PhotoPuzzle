package com.photopuzzle.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.photopuzzle.app.data.models.SizeStats
import kotlin.math.roundToInt

enum class SortField(val label: String) {
    SIZE("Size"), BEST("Best"), AVG("Avg"), TOTAL("Total"), WIN_PCT("Win %")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    var sortField by remember { mutableStateOf(SortField.SIZE) }
    var sortAsc   by remember { mutableStateOf(true) }

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
            // ── Summary banner ────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                label = "Streak",
                                value = "${s?.currentStreakDays ?: 0}d",
                                modifier = Modifier.weight(1f)
                            )
                            BannerStat(
                                icon = Icons.Default.EmojiEvents,
                                label = "Best streak",
                                value = "${s?.longestStreakDays ?: 0}d",
                                modifier = Modifier.weight(1f)
                            )
                            BannerStat(
                                icon = Icons.Default.Verified,
                                label = "Completion",
                                value = "${((s?.overallCompletionRate ?: 0f) * 100).roundToInt()}%",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ── Per-size section header + sort chips ──────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "By Puzzle Size",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SortField.entries.forEach { field ->
                        SortChip(
                            field = field,
                            isActive = sortField == field,
                            ascending = sortAsc,
                            onClick = {
                                if (sortField == field) sortAsc = !sortAsc
                                else { sortField = field; sortAsc = true }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Sorted + annotated list ───────────────────────────────────────
            val allStats = s?.statsBySize ?: emptyList()

            // Favourite = size with most total attempts
            val favSize = allStats
                .filter { it.timesSolved > 0 || it.totalPlayTimeSeconds > 0L }
                .maxByOrNull { st ->
                    if (st.completionRate > 0f) (st.timesSolved / st.completionRate).toInt()
                    else st.timesSolved
                }?.takeIf { it.timesSolved > 0 }?.pieceCount

            // Unplayed sizes always sink to bottom; active sizes sorted by chosen field
            val sortedStats = allStats.sortedWith(
                compareBy<SizeStats> { st ->
                    if (st.timesSolved == 0 && st.totalPlayTimeSeconds == 0L) 1 else 0
                }.thenBy { st ->
                    when (sortField) {
                        SortField.SIZE    -> if (sortAsc) st.pieceCount.toDouble() else -st.pieceCount.toDouble()
                        SortField.BEST    -> if (sortAsc) (st.bestTimeSeconds ?: Long.MAX_VALUE).toDouble()
                                            else -(st.bestTimeSeconds ?: 0L).toDouble()
                        SortField.AVG     -> if (sortAsc) st.averageCompletionSeconds else -st.averageCompletionSeconds
                        SortField.TOTAL   -> if (sortAsc) st.totalPlayTimeSeconds.toDouble() else -st.totalPlayTimeSeconds.toDouble()
                        SortField.WIN_PCT -> if (sortAsc) st.completionRate.toDouble() else -st.completionRate.toDouble()
                    }
                }
            )

            items(sortedStats) { sizeStats ->
                SizeStatRow(
                    stats = sizeStats,
                    isFavourite = sizeStats.pieceCount == favSize,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ── Sort chip ─────────────────────────────────────────────────────────────────

@Composable
fun SortChip(field: SortField, isActive: Boolean, ascending: Boolean, onClick: () -> Unit) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.surfaceVariant
    val contentColor   = if (isActive) MaterialTheme.colorScheme.onPrimary
                         else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(99.dp),
        color = containerColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                field.label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor
            )
            if (isActive) {
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (ascending) "Ascending" else "Descending",
                    tint = contentColor,
                    modifier = Modifier.size(11.dp)
                )
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
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

// ── Per-size row ──────────────────────────────────────────────────────────────

@Composable
fun SizeStatRow(stats: SizeStats, isFavourite: Boolean = false, modifier: Modifier = Modifier) {
    val hasData  = stats.timesSolved > 0
    val dimAlpha = if (hasData) 1f else 0.5f

    val cardModifier = modifier
        .fillMaxWidth()
        .then(
            if (isFavourite) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(12.dp))
            else Modifier
        )

    Card(shape = RoundedCornerShape(12.dp), modifier = cardModifier) {
        Column(modifier = Modifier.padding(14.dp, 12.dp, 14.dp, 12.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: title + badges + subtitle lines
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${stats.pieceCount} pieces",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = dimAlpha)
                        )
                        if (stats.isRecentPB) {
                            Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    "PB",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isFavourite) {
                            Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    "favourite",
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (hasData) {
                        val pct = (stats.completionRate * 100).roundToInt()
                        val attempted = if (stats.completionRate > 0f)
                            (stats.timesSolved / stats.completionRate).roundToInt()
                        else stats.timesSolved
                        Text(
                            "${stats.timesSolved} solved · $attempted attempted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha)
                        )
                        Text(
                            "$pct% completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha)
                        )
                    } else {
                        Text(
                            "not played yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha)
                        )
                    }
                }

                // Right: best / avg / total
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricCol(
                        label = "best",
                        value = if (hasData) formatTime(stats.bestTimeSeconds ?: 0L) else "—",
                        color = if (hasData) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    MetricCol(
                        label = "avg",
                        value = if (hasData) formatTime(stats.averageCompletionSeconds.toLong()) else "—",
                        color = if (hasData) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    MetricCol(
                        label = "total",
                        value = formatTotalTime(stats.totalPlayTimeSeconds, hasActivity = hasData),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha)
                    )
                }
            }

            // Progress bar: best as % of avg
            Spacer(Modifier.height(10.dp))
            val best = stats.bestTimeSeconds ?: 0L
            val avg  = stats.averageCompletionSeconds.toLong().coerceAtLeast(1L)
            val barFraction = if (hasData && avg > 0) (best.toFloat() / avg).coerceIn(0f, 1f) else 0f
            val pctLabel = if (hasData) "best is ${(barFraction * 100).roundToInt()}% of avg" else "no data yet"

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(99.dp))
                ) {
                    if (hasData) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barFraction)
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(99.dp))
                        )
                    }
                }
                Text(
                    pctLabel,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dimAlpha)
                )
            }
        }
    }
}

@Composable
private fun MetricCol(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

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
