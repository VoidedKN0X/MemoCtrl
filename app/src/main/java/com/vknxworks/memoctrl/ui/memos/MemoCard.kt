package com.vknxworks.memoctrl.ui.memos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.settings.EditGesture
import com.vknxworks.memoctrl.ui.components.ActionSheet
import com.vknxworks.memoctrl.ui.components.AttachmentChips
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.ImageGrid
import com.vknxworks.memoctrl.ui.components.LocationRow
import com.vknxworks.memoctrl.ui.components.Markdown
import com.vknxworks.memoctrl.ui.components.MarkdownActions
import com.vknxworks.memoctrl.ui.components.SheetAction
import com.vknxworks.memoctrl.ui.components.runTask
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import com.vknxworks.memoctrl.util.extractPreview

// ---------- Visibility ----------

val MemoVisibility.label: String
    get() = when (this) {
        MemoVisibility.PRIVATE -> "Private"
        MemoVisibility.PROTECTED -> "Signed-in users"
        MemoVisibility.PUBLIC -> "Public"
        MemoVisibility.SPACE -> "Space members"
    }

val MemoVisibility.description: String
    get() = when (this) {
        MemoVisibility.PRIVATE -> "Only you"
        MemoVisibility.PROTECTED -> "Everyone with an account on your server"
        MemoVisibility.PUBLIC -> "Anyone, including people who aren't signed in"
        MemoVisibility.SPACE -> "Members of your space"
    }

val MemoVisibility.icon: ImageVector
    get() = when (this) {
        MemoVisibility.PRIVATE -> Icons.Outlined.Lock
        MemoVisibility.PROTECTED -> Icons.Outlined.Group
        MemoVisibility.PUBLIC -> Icons.Outlined.Public
        MemoVisibility.SPACE -> Icons.Outlined.Badge
    }

fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

// ---------- Cards ----------

/**
 * One memo in a list: when it was written, its text (shortened, with "Show more"), pictures,
 * attachments and location. Pinned memos get an accent outline.
 */
@Composable
fun MemoCard(
    memo: MemoEntity,
    modifier: Modifier = Modifier,
    preview: Boolean = true,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val defaultVisibility = session?.account?.defaultVisibility
    var menu by remember { mutableStateOf(false) }
    val gesture = settings.editGesture
    val edit = { nav.edit(memo.identifier) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (memo.pinned) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else null,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 6.dp),
    ) {
        Column(
            Modifier
                .combinedClickable(
                    onClick = { if (gesture == EditGesture.SINGLE) edit() else onOpen() },
                    onDoubleClick = if (gesture == EditGesture.DOUBLE) edit else null,
                    onLongClick = if (gesture == EditGesture.LONG) edit else { { menu = true } },
                )
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
        ) {
            MemoHeader(
                time = relativeTime(memo.date.toEpochMilli()),
                pinned = memo.pinned,
                unsynced = memo.needsSync,
                visibility = memo.visibility.takeIf { it != defaultVisibility },
                onMenu = { menu = true },
            )
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoBody(memo, preview = preview, onShowMore = onOpen)
            }
        }
    }

    if (menu) MemoActionsSheet(memo, onDismiss = { menu = false })
}

@Composable
fun MemoHeader(
    time: String,
    pinned: Boolean,
    unsynced: Boolean,
    visibility: MemoVisibility?,
    author: String? = null,
    onMenu: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = if (onMenu == null) 10.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (author != null) {
            Spacer(Modifier.width(10.dp))
            Text("@$author", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pinned) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.PushPin, "Pinned", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
        if (visibility != null) {
            Spacer(Modifier.width(8.dp))
            Icon(visibility.icon, visibility.label, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (unsynced) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.CloudOff, "Not synced yet", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.weight(1f))
        if (onMenu != null) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The text, pictures, files and location of a memo. Task checkboxes can be ticked right here. */
@Composable
fun MemoBody(memo: MemoEntity, preview: Boolean, onShowMore: (() -> Unit)? = null, selectable: Boolean = false) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val (text, cut) = remember(memo.content, preview) { if (preview) extractPreview(memo.content) else memo.content to false }
    val actions = remember(memo.identifier, memo.content) {
        MarkdownActions(
            onCheckbox = { checked, start, end -> toggleTask(c, memo, checked, start, end) },
            onTag = { nav.tag(it) },
        )
    }
    if (text.isNotBlank()) {
        Markdown(text, serverUrl = session?.account?.host, actions = actions, selectable = selectable)
    }
    if (cut && onShowMore != null) {
        Text(
            "Show more",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onShowMore).padding(vertical = 2.dp),
        )
    }
    ImageGrid(memo.resources.filter { it.isImage })
    AttachmentChips(memo.resources.filterNot { it.isImage })
    memo.location?.let { LocationRow(it) }
}

/** Ticks or unticks a "- [ ]" task: only the box itself changes in the memo's text. */
private fun toggleTask(c: com.vknxworks.memoctrl.AppContainer, memo: MemoEntity, checked: Boolean, start: Int, end: Int) {
    if (start < 0 || end > memo.content.length || start >= end) return
    val box = memo.content.substring(start, end)
    val replaced = if (checked) box.replace("[ ]", "[x]") else box.replace("[x]", "[ ]").replace("[X]", "[ ]")
    val content = memo.content.replaceRange(start, end, replaced)
    c.runTask { c.repo.updateMemo(memo.identifier, content = content) }
}

/** The ⋮ menu of a memo. */
@Composable
fun MemoActionsSheet(memo: MemoEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    var deleting by remember { mutableStateOf(false) }
    if (deleting) {
        ConfirmDialog(
            "Delete this memo?",
            "It's removed from your server too. This can't be undone.",
            "Delete",
            onDismiss = { deleting = false; onDismiss() },
        ) {
            deleting = false
            onDismiss()
            c.runTask { c.repo.deleteMemo(memo.identifier) }
        }
        return
    }
    val remoteUrl = memo.remoteId?.let { id -> c.sessions.active.value?.remote?.memoUrl(id) }
    val actions = buildList {
        if (memo.archived) {
            add(SheetAction("Restore", Icons.Outlined.Restore) { c.runTask { c.repo.restoreMemo(memo.identifier) } })
        } else {
            add(
                if (memo.pinned) SheetAction("Unpin", Icons.Outlined.PinDrop) { c.runTask { c.repo.updateMemo(memo.identifier, pinned = false) } }
                else SheetAction("Pin to the top", Icons.Outlined.PushPin) { c.runTask { c.repo.updateMemo(memo.identifier, pinned = true) } },
            )
            add(SheetAction("Edit", Icons.Outlined.Edit) { nav.edit(memo.identifier) })
            add(SheetAction("Share", Icons.Outlined.Share) { shareText(context, memo.content) })
            add(SheetAction("Copy text", Icons.Outlined.ContentCopy) { copyText(context, memo.content) })
            if (remoteUrl != null) add(SheetAction("Share link", Icons.Outlined.Link) { shareText(context, remoteUrl) })
            add(SheetAction("Archive", Icons.Outlined.Archive) { c.runTask { c.repo.archiveMemo(memo.identifier) } })
        }
        add(SheetAction("Delete", Icons.Outlined.Delete, destructive = true) { deleting = true })
    }
    ActionSheet(
        title = memo.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "Memo",
        subtitle = relativeTime(memo.date.toEpochMilli()),
        actions = actions,
        onDismiss = { if (!deleting) onDismiss() },
    )
}

fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}

fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Memo", text))
    // Android 13 and newer show their own confirmation.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

/** A memo someone shared on the server, in Explore. Read-only. */
@Composable
fun ExploreCard(memo: RemoteMemo, serverUrl: String?) {
    val nav = LocalNavigator.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MemoHeader(
                time = relativeTime(memo.date.toEpochMilli()),
                pinned = false,
                unsynced = false,
                visibility = memo.visibility,
                author = memo.creator?.name?.takeIf { it.isNotBlank() },
            )
            val actions = remember { MarkdownActions(onTag = { nav.tag(it) }) }
            if (memo.content.isNotBlank()) Markdown(memo.content, serverUrl = serverUrl, actions = actions)
            val asEntities = remember(memo) {
                memo.resources.map { r ->
                    com.vknxworks.memoctrl.data.model.ResourceEntity(
                        identifier = r.remoteId,
                        remoteId = r.remoteId,
                        accountKey = "",
                        date = r.date,
                        filename = r.filename,
                        uri = r.uri,
                        mimeType = r.mimeType,
                    )
                }
            }
            ImageGrid(asEntities.filter { it.isImage })
            AttachmentChips(asEntities.filterNot { it.isImage })
            memo.location?.let { LocationRow(it) }
        }
    }
}
