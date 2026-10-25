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
import com.vknxworks.memoctrl.ui.components.CtrlCard
import com.vknxworks.memoctrl.ui.components.CtrlHeroCard
import com.vknxworks.memoctrl.ui.components.CtrlLabel
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlType
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.memoctrl.R

/** The Tags tab: how much you've written, a heatmap of the days you wrote, and every tag you use. */
@Composable
fun TagsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val memos by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_tags))
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
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // The one big number: how many memos you've written.
                    CtrlHeroCard(
                        label = pluralStringResource(R.plurals.stat_memos, list.size),
                        value = list.size.toString(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(tagCounts.size.toString(), pluralStringResource(R.plurals.stat_tags, tagCounts.size), Modifier.weight(1f))
                        StatCard(days.toString(), pluralStringResource(R.plurals.stat_days, days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), Modifier.weight(1f))
                    }
                    CtrlCard {
                        Heatmap(list)
                    }
                }
            }
            if (tagCounts.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 16.dp),
                    )
                }
            } else {
                item(key = "tags") {
                    Group(stringResource(R.string.tab_tags)) {
                        tagCounts.forEach { (tag, count) ->
                            GroupRow(
                                "#$tag",
                                icon = Icons.Outlined.Tag,
                                onClick = { nav.tag(tag) },
                                trailing = {
                                    Text(
                                        pluralStringResource(R.plurals.tag_count, count, count).uppercase(),
                                        style = CtrlType.MonoStrong,
                                        color = CtrlColors.Muted,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    CtrlCard(modifier) {
        CtrlLabel(label)
        Text(value, style = CtrlType.ReadoutSmall, color = CtrlColors.Text, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
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
    // Shades of the accent from the current palette: they read on the dark and the light card.
    val heat = CtrlColors.Heat
    val todayRing = CtrlColors.Text

    Row(Modifier.fillMaxWidth().height(118.dp)) {
        Column(Modifier.padding(end = 6.dp).height(118.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(dayNames[0].uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
            Text(dayNames[3].uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
            Text(dayNames[6].uppercase(), style = CtrlType.Label, color = CtrlColors.Muted)
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
                            0 -> heat[0]
                            1 -> heat[1]
                            2 -> heat[2]
                            in 3..4 -> heat[3]
                            else -> heat[4]
                        }
                        val topLeft = Offset(left + col * (c + g), row * (c + g))
                        drawRoundRect(color, topLeft, Size(c, c), CornerRadius(c * 0.2f))
                        if (day == today) drawRoundRect(todayRing, topLeft, Size(c, c), CornerRadius(c * 0.2f), style = Stroke(width = 1.dp.toPx()))
                    }
                }
            }
        }
    }
}
