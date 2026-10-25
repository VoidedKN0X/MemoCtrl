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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.vknxworks.memoctrl.util.cleanTagName
import android.widget.Toast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.memoctrl.ui.components.ActionSheet
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.SheetAction
import com.vknxworks.memoctrl.ui.components.TextInputDialog
import com.vknxworks.memoctrl.ui.components.runTask
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
    var menuFor by remember { mutableStateOf<String?>(null) }
    TagActions(menuFor, onDismiss = { menuFor = null })

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
                                onLongClick = { menuFor = tag },
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

/** What you're doing to a tag after long-pressing it. */
private enum class TagStep { MENU, RENAME, CONFIRM_RENAME, DELETE }

/**
 * Long-press a tag: rename it or delete it. Both say first how many memos change, and work offline:
 * the memos are changed on the phone and sync like any other edit.
 */
@Composable
fun TagActions(tag: String?, onDismiss: () -> Unit) {
    if (tag == null) return
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    var step by remember(tag) { mutableStateOf(TagStep.MENU) }
    var newName by remember(tag) { mutableStateOf<String?>(null) }
    var count by remember(tag) { mutableStateOf<Int?>(null) }
    LaunchedEffect(tag, repo) { count = runCatching { repo.countMemosWithTag(tag) }.getOrDefault(0) }
    val n = count ?: 0

    fun done(message: String) {
        onDismiss()
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    when (step) {
        TagStep.MENU -> ActionSheet(
            title = "#$tag",
            subtitle = count?.let { pluralStringResource(R.plurals.tag_count, it, it) },
            actions = listOf(
                // "Open" goes to the tag's memos, like a tap.
                SheetAction(stringResource(R.string.tag_open), Icons.Outlined.Tag) { nav.tag(tag) },
                SheetAction(stringResource(R.string.tag_rename), Icons.Outlined.Edit) { step = TagStep.RENAME },
                SheetAction(stringResource(R.string.tag_delete), Icons.Outlined.Delete, destructive = true) { step = TagStep.DELETE },
            ),
            onDismiss = { if (step == TagStep.MENU) onDismiss() },
        )
        TagStep.RENAME -> {
            TextInputDialog(
                title = stringResource(R.string.tag_rename_title, tag),
                label = stringResource(R.string.tag_rename_label),
                confirm = stringResource(R.string.next),
                initial = tag,
                onDismiss = onDismiss,
            ) { typed ->
                val clean = cleanTagName(typed)
                when {
                    clean == null -> Toast.makeText(context, context.getString(R.string.tag_invalid), Toast.LENGTH_SHORT).show()
                    clean == tag -> onDismiss()
                    else -> {
                        newName = clean
                        step = TagStep.CONFIRM_RENAME
                    }
                }
            }
        }
        TagStep.CONFIRM_RENAME -> {
            val target = newName ?: return
            CtrlAlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.tag_rename_confirm_title, tag, target)) },
                text = {
                    Text(
                        if (count == null) stringResource(R.string.tag_counting)
                        else pluralStringResource(R.plurals.tag_rename_confirm_body, n, n, tag, target),
                    )
                },
                confirmButton = {
                    TextButton(enabled = count != null && n > 0, onClick = {
                        val msg = context.resources.getQuantityString(R.plurals.tag_renamed, n, n, target)
                        c.runTask {
                            repo.renameTag(tag, target)
                        }
                        done(msg)
                    }) { Text(stringResource(R.string.tag_rename)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        TagStep.DELETE -> ConfirmDialog(
            stringResource(R.string.tag_delete_title, tag),
            if (count == null) stringResource(R.string.tag_counting)
            else pluralStringResource(R.plurals.tag_delete_body, n, n, tag),
            stringResource(R.string.tag_delete),
            onDismiss = onDismiss,
        ) {
            if (count == null) return@ConfirmDialog
            val msg = context.resources.getQuantityString(R.plurals.tag_deleted, n, n, tag)
            c.runTask { repo.renameTag(tag, null) }
            done(msg)
        }
    }
}
