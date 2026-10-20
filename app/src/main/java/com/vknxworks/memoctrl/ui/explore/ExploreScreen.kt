package com.vknxworks.memoctrl.ui.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.api.MemosRemote
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.ui.components.ErrorView
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.TabTitle
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.memos.ExploreCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Memos shared on the server, a page at a time. Kept while you move between tabs. */
class ExploreModel : ViewModel() {
    val memos = mutableStateListOf<RemoteMemo>()
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    private var nextToken: String? = null
    private var remote: MemosRemote? = null
    private var job: Job? = null

    fun bind(r: MemosRemote) {
        if (remote === r) return
        remote = r
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        job?.cancel()
        nextToken = null
        endReached = false
        error = null
        if (initial) memos.clear() else refreshing = true
        loading = false
        loadMore(replace = !initial)
    }

    fun loadMore(replace: Boolean = false) {
        val r = remote ?: return
        if (loading || (endReached && !replace)) return
        loading = true
        job = viewModelScope.launch {
            try {
                val (page, token) = r.listExploreMemos(PAGE_SIZE, if (replace) null else nextToken)
                if (replace) memos.clear()
                val known = memos.mapTo(HashSet()) { it.remoteId }
                memos.addAll(page.filterNot { it.remoteId in known })
                nextToken = token
                endReached = token == null
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                loading = false
                refreshing = false
            }
        }
    }
}

/** The Explore tab: what people on your server shared with everyone (public and signed-in only). */
@Composable
fun ExploreScreen(model: ExploreModel = viewModel()) {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val remote = session?.remote ?: return
    LaunchedEffect(remote) { model.bind(remote) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle("Explore")
        when {
            model.memos.isEmpty() && model.loading -> LoadingView()
            model.memos.isEmpty() && model.error != null -> ErrorView(model.error.orEmpty()) { model.refresh(initial = true) }
            model.memos.isEmpty() && model.endReached -> MessageView(
                Icons.Outlined.Public,
                "Nothing shared yet",
                "Memos that are public or visible to signed-in users on your server show up here.",
            )
            else -> PullToRefreshBox(
                isRefreshing = model.refreshing,
                onRefresh = { model.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp + LocalBottomClearance.current)) {
                    itemsIndexed(model.memos, key = { _, m -> m.remoteId }) { i, memo ->
                        if (i >= model.memos.size - 5) LaunchedEffect(model.memos.size) { model.loadMore() }
                        ExploreCard(memo, serverUrl = remote.host)
                    }
                    item(key = "footer") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                model.loading -> CircularProgressIndicator(Modifier.size(28.dp))
                                model.error != null -> TextButton(onClick = { model.loadMore() }) { Text("Couldn't load more. Try again") }
                                model.endReached -> Text(
                                    "That's everything",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
