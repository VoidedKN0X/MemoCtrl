package com.vknxworks.memoctrl.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.ui.components.CtrlLabel
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlShapes
import com.vknxworks.memoctrl.ui.theme.CtrlType
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month at a time: every day shows how many memos you wrote, and tapping a day lists them. Works from
 * the memos kept on the phone, so also offline and without a server. Archived memos aren't counted.
 */
@Composable
fun CalendarScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val all by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = null)
    val zone = remember { ZoneId.systemDefault() }
    val byDay by produceState<Map<LocalDate, List<MemoEntity>>?>(null, all) {
        val list = all
        value = if (list == null) null else withContext(Dispatchers.Default) { list.groupBy { it.date.atZone(zone).toLocalDate() } }
    }
    val today = remember { LocalDate.now() }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = remember(monthText) { YearMonth.parse(monthText) }
    val selected = remember(selectedText) { LocalDate.parse(selectedText) }
    val locale = remember { Locale.getDefault() }
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.calendar_title)) {
            if (YearMonth.from(today) != month || selected != today) {
                TextButton(onClick = { monthText = YearMonth.from(today).toString(); selectedText = today.toString() }) {
                    Text(stringResource(R.string.calendar_today))
                }
            }
        }
        val days = byDay
        if (days == null) {
            LoadingView()
        } else {
            val memos = days[selected].orEmpty()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                item(key = "month") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous))
                            }
                            Text(
                                month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next))
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            for (i in 0 until 7) {
                                val day = firstDay.plus(i.toLong())
                                Text(
                                    day.getDisplayName(TextStyle.SHORT, locale).uppercase(locale),
                                    style = CtrlType.Label,
                                    color = CtrlColors.Muted,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                                )
                            }
                        }
                        val lead = leadingBlanks(month.atDay(1).dayOfWeek, firstDay)
                        val cells = lead + month.lengthOfMonth()
                        for (row in 0 until (cells + 6) / 7) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (col in 0 until 7) {
                                    val dayNumber = row * 7 + col - lead + 1
                                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                                        Box(Modifier.weight(1f).aspectRatio(1f))
                                    } else {
                                        val date = month.atDay(dayNumber)
                                        val count = days[date]?.size ?: 0
                                        DayCell(
                                            day = dayNumber,
                                            count = count,
                                            selected = date == selected,
                                            today = date == today,
                                            description = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)) +
                                                ", " + pluralStringResource(R.plurals.calendar_memos, count, count),
                                            modifier = Modifier.weight(1f),
                                        ) { selectedText = date.toString() }
                                    }
                                }
                            }
                            Box(Modifier.padding(top = 4.dp))
                        }
                        CtrlLabel(
                            selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                            Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                        )
                        if (memos.isEmpty()) {
                            Text(
                                stringResource(R.string.calendar_empty_day),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                            )
                        }
                    }
                }
                items(memos, key = { it.identifier }, contentType = { "memo" }) { memo ->
                    MemoCard(memo, onOpen = { nav.memo(memo.identifier) })
                }
            }
        }
    }
}

/** Empty cells before the 1st, for a week that starts on [firstDay]. */
internal fun leadingBlanks(first: DayOfWeek, firstDay: DayOfWeek): Int = ((first.value - firstDay.value) % 7 + 7) % 7

@Composable
private fun DayCell(day: Int, count: Int, selected: Boolean, today: Boolean, description: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .aspectRatio(1f)
            .clip(CtrlShapes.Control)
            .background(if (selected) CtrlColors.Accent else if (count > 0) CtrlColors.ChipFill else Color.Transparent)
            .border(1.dp, if (today) CtrlColors.AccentText else Color.Transparent, CtrlShapes.Control)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (count > 0) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) CtrlColors.OnAccent else MaterialTheme.colorScheme.onSurface,
        )
        if (count > 0) {
            Text(count.toString(), style = CtrlType.Label, color = if (selected) CtrlColors.OnAccent else CtrlColors.AccentText)
        }
    }
}
