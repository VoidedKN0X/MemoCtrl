package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.components.TabTitle
import com.vknxworks.memoctrl.ui.nav.EditorRoute
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.util.hasTag
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

/** The Memos tab: your timeline, pinned memos first, and a button to write a new one. */
@Composable
fun MemosScreen() {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val sync = rememberManualSync()
        TabTitle(stringResource(R.string.tab_memos)) { SyncButton(sync) }
        MemoTimeline(sync = sync, filter = null)
    }
}

/** Memos with one tag (and its sub-tags), opened by tapping a #tag anywhere. */
@Composable
fun TagMemosScreen(tag: String) {
    Column(Modifier.fillMaxSize()) {
        val sync = rememberManualSync()
        ScreenTopBar("#$tag") { SyncButton(sync) }
        MemoTimeline(sync = sync, filter = { hasTag(it.content, tag) }, newMemoText = "#$tag ")
    }
}

@Composable
private fun MemoTimeline(sync: ManualSync, filter: ((MemoEntity) -> Boolean)?, newMemoText: String? = null) {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val all by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = null)
    val memos = remember(all, filter) { all?.let { list -> if (filter == null) list else list.filter(filter) } }
    val listState = rememberLazyListState()
    var pulled by remember { mutableStateOf(false) }
    val expandedFab by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // A memo you just wrote appears at the top: scroll up to show it.
    var topId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(memos?.firstOrNull()?.identifier) {
        val first = memos?.firstOrNull()?.identifier
        if (topId != null && first != null && first != topId) listState.animateScrollToItem(0)
        topId = first
    }

    Box(Modifier.fillMaxSize()) {
        when {
            memos == null -> LoadingView()
            else -> PullToRefreshBox(
                isRefreshing = pulled && sync.running,
                onRefresh = {
                    pulled = true
                    sync.start(onDone = { pulled = false })
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (memos.isEmpty()) {
                    MessageView(
                        Icons.Outlined.Edit,
                        stringResource(if (filter == null) R.string.memos_empty else R.string.memos_empty_tag),
                        stringResource(if (filter == null) R.string.memos_empty_body else R.string.memos_empty_tag_body),
                        modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                    )
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp + LocalBottomClearance.current),
                    ) {
                        items(memos, key = { it.identifier }) { memo ->
                            MemoCard(memo, modifier = Modifier.animateItem(), onOpen = { nav.memo(memo.identifier) })
                        }
                    }
                }
            }
        }
        val clearance = LocalBottomClearance.current
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(EditorRoute(text = newMemoText)) },
            expanded = expandedFab,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.new_memo)) },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = if (clearance > 0.dp) clearance + 12.dp else 16.dp),
        )
    }
}
