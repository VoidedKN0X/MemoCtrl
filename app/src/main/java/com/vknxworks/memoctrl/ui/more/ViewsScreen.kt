package com.vknxworks.memoctrl.ui.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.SavedView
import com.vknxworks.memoctrl.ui.components.ActionSheet
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.CtrlTextField
import com.vknxworks.memoctrl.ui.components.ErrorView
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.components.SheetAction
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.memos.ExploreCard
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.nav.SharedMemoRoute
import com.vknxworks.memoctrl.ui.nav.ViewMemosRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 30

/**
 * Saved views: the named filters Memos keeps on the server ("shortcuts" on Memos 0.27 to 0.30, "views"
 * on 0.31). They are the same ones the Memos website shows; adding, changing or removing one here
 * changes it there too. Tapping one lists your memos that match, worked out by the server.
 */
@Composable
fun ViewsScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val remote = session?.remote ?: return
    var views by remember { mutableStateOf<List<SavedView>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<SavedView?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<SavedView?>(null) }
    var deleting by remember { mutableStateOf<SavedView?>(null) }

    LaunchedEffect(remote, reload) {
        try {
            views = remote.listViews()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (views == null) error = e.friendlyMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.views_title)) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.view_add)) }
        }
        val list = views
        when {
            list == null && error != null -> ErrorView(error.orEmpty()) { error = null; reload++ }
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(
                Icons.Outlined.FilterList,
                stringResource(R.string.views_empty),
                stringResource(R.string.views_empty_body),
                actionLabel = stringResource(R.string.view_add),
                onAction = { adding = true },
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                Group(footer = stringResource(R.string.views_footer)) {
                    list.forEach { v ->
                        GroupRow(
                            v.title.ifBlank { stringResource(R.string.view_untitled) },
                            v.filter,
                            icon = Icons.Outlined.FilterList,
                            showChevron = true,
                            onClick = { nav.navigate(ViewMemosRoute(v.title, v.filter)) },
                            onLongClick = { menuFor = v },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { v ->
        ActionSheet(
            title = v.title,
            subtitle = null,
            actions = listOf(
                SheetAction(stringResource(R.string.edit), Icons.Outlined.Edit) { editing = v },
                SheetAction(stringResource(R.string.delete), Icons.Outlined.Delete, destructive = true) { deleting = v },
            ),
            onDismiss = { menuFor = null },
        )
    }

    if (adding || editing != null) {
        val existing = editing
        ViewDialog(
            existing,
            onDismiss = { adding = false; editing = null },
            save = { title, filter ->
                if (existing == null) remote.createView(title, filter) else remote.updateView(existing.name, title, filter)
                adding = false
                editing = null
                reload++
            },
        )
    }

    deleting?.let { v ->
        ConfirmDialog(
            stringResource(R.string.view_delete_title, v.title),
            stringResource(R.string.view_delete_body),
            stringResource(R.string.delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            scope.launch {
                try {
                    remote.deleteView(v.name)
                    reload++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    android.widget.Toast.makeText(context, e.friendlyMessage(), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** Name and filter of a view. The server checks the filter and says what's wrong with it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewDialog(existing: SavedView?, onDismiss: () -> Unit, save: suspend (title: String, filter: String) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var filter by remember { mutableStateOf(existing?.filter.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    CtrlAlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(if (existing == null) R.string.view_add else R.string.view_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CtrlTextField(title, { title = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.view_name), enabled = !saving)
                CtrlTextField(filter, { filter = it; error = null }, Modifier.fillMaxWidth(), label = stringResource(R.string.view_filter), singleLine = false, enabled = !saving)
                Text(stringResource(R.string.view_filter_help), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Pieces of Memos' own filter language that every supported server understands.
                    listOf("pinned == true", "content.contains(\"\")", "visibility == \"PUBLIC\"").forEach { piece ->
                        AssistChip(
                            onClick = { filter = if (filter.isBlank()) piece else "$filter && $piece" },
                            label = { Text(piece, style = MaterialTheme.typography.labelSmall) },
                            enabled = !saving,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && title.isNotBlank() && filter.isNotBlank(),
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            save(title.trim(), filter.trim())
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            error = e.friendlyMessage()
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Your memos that match a saved view, a page at a time, newest first. */
@Composable
fun ViewMemosScreen(title: String, filter: String) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    val remote = s.remote
    val memos = remember { mutableStateListOf<Pair<RemoteMemo, MemoEntity?>>() }
    var token by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var wanted by remember { mutableIntStateOf(1) }
    val changes by c.db.changes.collectAsStateWithLifecycle()

    LaunchedEffect(remote, filter, wanted) {
        if (end || loading) return@LaunchedEffect
        loading = true
        try {
            val (page, next) = remote.listMemosByFilter(filter, PAGE_SIZE, token)
            val known = memos.mapTo(HashSet()) { it.first.remoteId }
            // Your own memos are on the phone too: those open and behave like in the timeline.
            memos.addAll(page.filterNot { it.remoteId in known }.map { it to c.db.getMemoByRemoteId(it.remoteId, s.repo.accountKey)?.takeIf { m -> !m.isDeleted } })
            token = next
            end = next == null
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.friendlyMessage()
        } finally {
            loading = false
        }
    }
    // A memo edited or ticked here shows its new text.
    LaunchedEffect(changes) {
        for (i in memos.indices) {
            val (r, local) = memos[i]
            if (local != null) memos[i] = r to s.repo.getMemo(local.identifier)?.takeIf { !it.isDeleted }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title.ifBlank { stringResource(R.string.view_untitled) })
        when {
            memos.isEmpty() && error != null -> ErrorView(error.orEmpty()) { error = null; wanted++ }
            memos.isEmpty() && !end -> LoadingView()
            memos.isEmpty() -> MessageView(Icons.Outlined.FilterList, stringResource(R.string.view_no_memos), stringResource(R.string.view_no_memos_body))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
                itemsIndexed(memos, key = { _, m -> m.first.remoteId }) { i, (remoteMemo, local) ->
                    if (i >= memos.size - 5 && !end && !loading && error == null) LaunchedEffect(memos.size) { wanted++ }
                    if (local != null) MemoCard(local, onOpen = { nav.memo(local.identifier) })
                    else ExploreCard(remoteMemo, serverUrl = remote.host, onOpen = { nav.navigate(SharedMemoRoute(remoteMemo.remoteId)) })
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(28.dp))
                            error != null -> TextButton(onClick = { error = null; wanted++ }) { Text(stringResource(R.string.explore_load_more_failed)) }
                        }
                    }
                }
            }
        }
    }
}
