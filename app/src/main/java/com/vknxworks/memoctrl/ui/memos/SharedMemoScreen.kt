package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.ui.components.ErrorView
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.theme.CtrlType
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

/**
 * A memo read straight from the server: one that someone shared (opened from Explore), or one a memo of
 * yours links to that isn't on the phone. Its task boxes can be ticked when the server lets you change
 * the memo; reactions and comments are open to everyone who can read it.
 */
@Composable
fun SharedMemoScreen(remoteId: String) {
    val context = LocalContext.current
    val c = context.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    val remote = s.remote
    var memo by remember(remoteId) { mutableStateOf<RemoteMemo?>(null) }
    var error by remember(remoteId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val serverChanges by c.live.changes.collectAsStateWithLifecycle()

    LaunchedEffect(remote, remoteId, attempt, serverChanges) {
        try {
            memo = remote.getMemo(remoteId)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A reload that fails keeps what is already on screen.
            if (memo == null) error = e.friendlyMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        val m = memo
        ScreenTopBar(stringResource(R.string.memo)) {
            if (m != null) {
                IconButton(onClick = { copyText(context, m.content) }) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.copy_text)) }
                IconButton(onClick = { shareText(context, remote.memoUrl(m.remoteId)) }) { Icon(Icons.Outlined.Link, stringResource(R.string.share_link)) }
            }
        }
        when {
            m == null && error != null -> ErrorView(error.orEmpty()) { error = null; attempt++ }
            m == null -> LoadingView()
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MemoHeader(
                    time = relativeTime(m.date.toEpochMilli()),
                    pinned = m.pinned,
                    unsynced = false,
                    visibility = m.visibility,
                    author = m.creator?.name?.takeIf { it.isNotBlank() },
                )
                RemoteMemoBody(m, remote.host, onChanged = { memo = it }, selectable = true)
                val hasTasks = remember(m.content) { TASK_BOX.containsMatchIn(m.content) }
                if (hasTasks && !rememberCanChange(m)) {
                    Text(
                        stringResource(R.string.tasks_read_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinkPreviews(m.content, remote)
                Text(
                    listOfNotNull(
                        DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(m.date.toEpochMilli())),
                        m.visibility.description,
                        if (m.archived) stringResource(R.string.archived) else null,
                    ).joinToString("  ·  "),
                    style = CtrlType.Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                MemoSocial(
                    memoRemoteId = m.remoteId,
                    visibility = m.visibility,
                    archived = m.archived,
                    mayLink = m.creatorId != null && m.creatorId == s.account.remoteIdentifier,
                )
            }
        }
    }
}

/** A Markdown task item: "- [ ]", "* [x]" or "1. [ ]" at the start of a line. */
private val TASK_BOX = Regex("""(?m)^\s*(?:[-*+]|\d+[.)])\s+\[[ xX]]\s""")
