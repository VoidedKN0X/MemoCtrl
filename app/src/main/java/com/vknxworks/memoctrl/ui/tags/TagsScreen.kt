package com.vknxworks.memoctrl.ui.tags

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.TabTitle
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import com.vknxworks.memoctrl.util.extractTags
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** The Tags tab: how much you've written, a heatmap of the days you wrote, and every tag you use. */
@Composable
fun TagsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val memos by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle("Tags")
        val list = memos ?: run {
            LoadingView()
            return@Column
        }
        val tagCounts = remember(list) {
            val counts = HashMap<String, Int>()
            list.forEach { m -> extractTags(m.content).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
            counts.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }
        }
        val start = session?.account?.toUser()?.startDate
        val days = remember(start) {
            start?.let { ChronoUnit.DAYS.between(it.atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).coerceAtLeast(0L) } ?: 0L
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current)) {
            item(key = "stats") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Stat(list.size.toString(), if (list.size == 1) "Memo" else "Memos")
                            Stat(tagCounts.size.toString(), if (tagCounts.size == 1) "Tag" else "Tags")
                            Stat(days.toString(), if (days == 1L) "Day" else "Days")
                        }
                        Heatmap(list)
                    }
                }
            }
            if (tagCounts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No tags yet. Add one to a memo by writing #something.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 16.dp),
                    )
                }
            } else {
                item(key = "tags") {
                    Group("Tags") {
                        tagCounts.forEach { (tag, count) ->
                            GroupRow(
                                "#$tag",
                                if (count == 1) "1 memo" else "$count memos",
                                icon = Icons.Outlined.Tag,
                                showChevron = true,
                                onClick = { nav.tag(tag) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A square per day, weeks as columns, today at the bottom right. Darker means more memos that day. */
@Composable
private fun Heatmap(memos: List<MemoEntity>) {
    val zone = ZoneId.systemDefault()
    val counts = remember(memos) {
        memos.groupingBy { it.date.atZone(zone).toLocalDate() }.eachCount()
    }
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val dayNames = remember(firstDay) {
        List(7) { i -> firstDay.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val todayRing = MaterialTheme.colorScheme.onSurface

    Row(Modifier.fillMaxWidth().height(118.dp)) {
        Column(Modifier.padding(end = 6.dp).height(118.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(dayNames[0], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dayNames[3], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dayNames[6], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.weight(1f).height(118.dp)) {
            val gap = 3.dp
            val cell = (maxHeight - gap * 6) / 7
            val columns = ((maxWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
            Canvas(Modifier.fillMaxSize()) {
                val c = cell.toPx()
                val g = gap.toPx()
                val totalWidth = columns * c + (columns - 1) * g
                val left = size.width - totalWidth
                for (col in 0 until columns) {
                    val weekOffset = (columns - 1 - col).toLong()
                    for (row in 0 until 7) {
                        val day = weekStart.minusWeeks(weekOffset).plusDays(row.toLong())
                        if (day.isAfter(today)) continue
                        val n = counts[day] ?: 0
                        val color = when (n) {
                            0 -> empty
                            1 -> accent.copy(alpha = 0.35f)
                            2 -> accent.copy(alpha = 0.55f)
                            in 3..4 -> accent.copy(alpha = 0.78f)
                            else -> accent
                        }
                        val topLeft = Offset(left + col * (c + g), row * (c + g))
                        drawRoundRect(color, topLeft, Size(c, c), CornerRadius(c * 0.22f))
                        if (day == today) drawRoundRect(todayRing, topLeft, Size(c, c), CornerRadius(c * 0.22f), style = Stroke(width = 1.dp.toPx()))
                    }
                }
            }
        }
    }
}
