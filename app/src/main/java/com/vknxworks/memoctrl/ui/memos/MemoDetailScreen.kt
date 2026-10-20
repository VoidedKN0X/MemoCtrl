package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.nav.MemoRoute
import com.vknxworks.memoctrl.ui.theme.Gutter
import java.text.DateFormat
import java.util.Date

private sealed interface Lookup {
    data object Loading : Lookup
    data object Missing : Lookup
    data class Found(val memo: MemoEntity) : Lookup
}

/** One memo in full: all its text (selectable), pictures, files and location. */
@Composable
fun MemoDetailScreen(id: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo
    val changes by c.db.changes.collectAsStateWithLifecycle()
    val lookup by produceState<Lookup>(Lookup.Loading, repo, changes, id) {
        value = repo?.getMemo(id)?.takeIf { !it.isDeleted }?.let { Lookup.Found(it) } ?: Lookup.Missing
    }
    var menu by remember { mutableStateOf(false) }
    // Deleted (here or on another device during a sync) while open: go back.
    var seen by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(lookup) {
        when (lookup) {
            is Lookup.Found -> seen = true
            Lookup.Missing -> if (seen) nav.close(MemoRoute::class)
            Lookup.Loading -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        val memo = (lookup as? Lookup.Found)?.memo
        ScreenTopBar("Memo") {
            if (memo != null && !memo.archived) IconButton(onClick = { nav.edit(memo.identifier) }) { Icon(Icons.Outlined.Edit, "Edit") }
            if (memo != null) IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
        }
        when (val l = lookup) {
            Lookup.Loading -> LoadingView()
            Lookup.Missing -> MessageView(Icons.Outlined.SearchOff, "Memo not found", "It may have been deleted.")
            is Lookup.Found -> {
                val m = l.memo
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MemoHeader(
                        time = relativeTime(m.date.toEpochMilli()),
                        pinned = m.pinned,
                        unsynced = m.needsSync,
                        visibility = m.visibility,
                    )
                    MemoBody(m, preview = false, selectable = true)
                    Text(
                        listOfNotNull(
                            DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(m.date.toEpochMilli())),
                            m.visibility.description,
                            if (m.archived) "Archived" else null,
                            if (m.needsSync) "Not synced yet" else null,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (menu) MemoActionsSheet(m, onDismiss = { menu = false })
            }
        }
    }
}
