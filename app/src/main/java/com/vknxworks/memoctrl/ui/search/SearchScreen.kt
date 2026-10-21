package com.vknxworks.memoctrl.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.TabTitle
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import com.vknxworks.memoctrl.util.matchesSearch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

/** The Search tab: finds memos on this phone by their text, so it works offline too. */
@Composable
fun SearchScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val focus = LocalFocusManager.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val memos by remember(repo) { repo.observeMemos() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val archived by remember(repo) { repo.observeArchived() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val debounced by remember { snapshotFlow { query.trim() }.debounce(200).distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = "")
    val results = remember(memos, debounced) { if (debounced.isEmpty()) emptyList() else memos.filter { matchesSearch(it.content, debounced) } }
    val archivedResults = remember(archived, debounced, settings.searchIncludesArchived) {
        if (debounced.isEmpty() || !settings.searchIncludesArchived) emptyList()
        else archived.filter { matchesSearch(it.content, debounced) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_search))
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 8.dp),
        )
        when {
            debounced.isEmpty() -> MessageView(
                Icons.Filled.Search,
                stringResource(R.string.search_hint),
                stringResource(if (settings.searchIncludesArchived) R.string.search_archive_note else R.string.search_timeline_note),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            results.isEmpty() && archivedResults.isEmpty() -> MessageView(
                Icons.Outlined.SearchOff,
                stringResource(R.string.search_nothing),
                stringResource(R.string.search_nothing_body, debounced),
                modifier = Modifier.padding(bottom = LocalBottomClearance.current),
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current)) {
                items(results, key = { it.identifier }) { m -> MemoCard(m, onOpen = { nav.memo(m.identifier) }) }
                if (archivedResults.isNotEmpty()) {
                    item(key = "archived-header") {
                        Text(
                            stringResource(R.string.archived),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Gutter + 4.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(archivedResults, key = { "a-" + it.identifier }) { m -> MemoCard(m, onOpen = { nav.memo(m.identifier) }) }
                }
            }
        }
    }
}
