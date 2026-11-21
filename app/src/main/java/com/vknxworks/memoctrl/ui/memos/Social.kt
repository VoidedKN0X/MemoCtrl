package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.api.MemosRemote
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.RemoteMemo
import com.vknxworks.memoctrl.data.model.RemoteReaction
import com.vknxworks.memoctrl.data.model.RemoteRelation
import com.vknxworks.memoctrl.ui.components.CtrlLabel
import com.vknxworks.memoctrl.ui.components.CtrlSheetShape
import com.vknxworks.memoctrl.ui.components.CtrlTextField
import com.vknxworks.memoctrl.ui.components.Markdown
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.nav.SharedMemoRoute
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlShapes
import com.vknxworks.memoctrl.ui.theme.CtrlType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the server holds around one memo: who reacted, what was said under it, which memos it links to. */
private data class Social(
    val reactions: List<RemoteReaction> = emptyList(),
    val comments: List<RemoteMemo> = emptyList(),
    val relations: List<RemoteRelation> = emptyList(),
)

private fun sameMemo(a: String, b: String): Boolean = a.substringAfterLast('/') == b.substringAfterLast('/')

/**
 * Reactions, linked memos and comments of a memo on the server (Memos 0.27 and newer), under the memo's
 * text. All of it is read from the server when the screen opens and whenever the server says something
 * changed; none of it is kept on the phone, so it needs a connection.
 *
 * [mayLink]: the memo is the signed-in user's own, so they may change which memos it links to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoSocial(memoRemoteId: String, visibility: MemoVisibility, archived: Boolean, mayLink: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    val remote = s.remote
    if (!remote.supportsSocial) return
    val me = s.account.remoteIdentifier
    val serverChanges by c.live.changes.collectAsStateWithLifecycle()
    var reload by remember { mutableIntStateOf(0) }
    var social by remember(memoRemoteId) { mutableStateOf<Social?>(null) }
    var error by remember(memoRemoteId) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var draft by rememberSaveable(memoRemoteId) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(remote, memoRemoteId, serverChanges, reload) {
        try {
            social = coroutineScope {
                val reactions = async { remote.listReactions(memoRemoteId) }
                val comments = async { remote.listComments(memoRemoteId) }
                val relations = async { remote.listRelations(memoRemoteId) }
                Social(reactions.await(), comments.await(), relations.await())
            }
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            error = e.friendlyMessage()
        }
    }

    /** Runs one change on the server, then reads everything again. One at a time. */
    fun change(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
                reload++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.widget.Toast.makeText(context, e.friendlyMessage(), android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun open(name: String) {
        scope.launch {
            val local = c.db.getMemoByRemoteId(name, s.repo.accountKey)?.takeIf { !it.isDeleted }
            if (local != null) nav.memo(local.identifier) else nav.navigate(SharedMemoRoute(name))
        }
    }

    val loaded = social
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (loaded == null) {
            if (error != null) {
                Text(stringResource(R.string.social_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { error = null; reload++ }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.retry)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.social_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {

        // ---------- Reactions ----------
        CtrlLabel(stringResource(R.string.reactions))
        val grouped = remember(loaded.reactions) { loaded.reactions.groupBy { it.type } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grouped.forEach { (type, list) ->
                val mine = list.firstOrNull { it.creator == me }
                Chip(
                    text = "$type ${list.size}",
                    selected = mine != null,
                    description = if (mine != null) stringResource(R.string.reaction_remove, type) else stringResource(R.string.reaction_add, type),
                ) {
                    change { if (mine != null) remote.deleteReaction(mine.name) else remote.addReaction(memoRemoteId, type) }
                }
            }
            Box(
                Modifier.clip(CtrlShapes.Chip).border(1.dp, CtrlColors.ChipBorder, CtrlShapes.Chip)
                    .clickable(role = Role.Button) { picking = true }.padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Icon(Icons.Outlined.AddReaction, stringResource(R.string.reaction_pick), Modifier.size(18.dp), tint = CtrlColors.AccentText)
            }
        }

        // ---------- Linked memos ----------
        val outgoing = loaded.relations.filter { !it.comment && sameMemo(it.memo, memoRemoteId) }
        val incoming = loaded.relations.filter { !it.comment && !sameMemo(it.memo, memoRemoteId) }
        if (outgoing.isNotEmpty() || incoming.isNotEmpty() || mayLink) {
            CtrlLabel(stringResource(R.string.linked_memos), Modifier.padding(top = 6.dp))
            outgoing.forEach { r ->
                LinkRow(
                    outgoing = true,
                    text = r.relatedSnippet.ifBlank { stringResource(R.string.memo) },
                    onClick = { open(r.related) },
                    onRemove = if (!mayLink) null else {
                        { change { remote.setReferences(memoRemoteId, outgoing.filter { it !== r }.map { it.related }) } }
                    },
                )
            }
            incoming.forEach { r ->
                LinkRow(outgoing = false, text = r.memoSnippet.ifBlank { stringResource(R.string.memo) }, onClick = { open(r.memo) }, onRemove = null)
            }
            if (mayLink) {
                TextButton(onClick = { linking = true }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.link_add))
                }
            }
        }

        // ---------- Comments ----------
        CtrlLabel(pluralStringResource(R.plurals.comments_count, loaded.comments.size, loaded.comments.size), Modifier.padding(top = 6.dp))
        loaded.comments.forEach { comment ->
            Column(
                Modifier.fillMaxWidth().clip(CtrlShapes.Control).background(CtrlColors.ChipFill).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row {
                    Text(relativeTime(comment.date.toEpochMilli()).uppercase(), style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1)
                    val author = comment.creator?.name?.takeIf { it.isNotBlank() }
                    if (author != null) {
                        Spacer(Modifier.width(10.dp))
                        Text("@$author", style = CtrlType.Mono, color = CtrlColors.AccentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Markdown(comment.content, serverUrl = remote.host, textStyle = MaterialTheme.typography.bodyMedium)
            }
        }
        if (archived) {
            Text(stringResource(R.string.comment_archived), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CtrlTextField(
                    draft,
                    { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.comment_hint),
                    singleLine = false,
                    enabled = !busy,
                )
                IconButton(
                    onClick = {
                        val text = draft.trim()
                        change {
                            remote.createComment(memoRemoteId, text, visibility)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !busy,
                ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.comment_send)) }
            }
        }
        }
    }

    if (picking) ReactionPicker(remote, onDismiss = { picking = false }) { type ->
        picking = false
        val already = loaded?.reactions?.any { it.type == type && it.creator == me } == true
        if (!already) change { remote.addReaction(memoRemoteId, type) }
    }
    if (linking && loaded != null) {
        val linked = loaded.relations.filter { !it.comment && sameMemo(it.memo, memoRemoteId) }.map { it.related }
        LinkPicker(exclude = linked + memoRemoteId, onDismiss = { linking = false }) { picked ->
            linking = false
            change { remote.setReferences(memoRemoteId, linked + picked) }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, description: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) CtrlColors.AccentText else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CtrlShapes.Chip)
            .background(if (selected) CtrlColors.ChipFill else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (selected) CtrlColors.AccentText else CtrlColors.ChipBorder, CtrlShapes.Chip)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LinkRow(outgoing: Boolean, text: String, onClick: () -> Unit, onRemove: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(CtrlShapes.Control).clickable(onClick = onClick).heightIn(min = 44.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (outgoing) Icons.AutoMirrored.Outlined.ArrowForward else Icons.AutoMirrored.Outlined.ArrowBack,
            stringResource(if (outgoing) R.string.link_to else R.string.link_from),
            Modifier.size(16.dp),
            tint = CtrlColors.AccentText,
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.link_remove), Modifier.size(18.dp), tint = CtrlColors.Muted) }
        }
    }
}

/** The emoji the server offers as reactions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReactionPicker(remote: MemosRemote, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var choices by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(remote) { choices = remote.reactionChoices() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CtrlColors.Surface, shape = CtrlSheetShape, tonalElevation = 0.dp) {
        CtrlLabel(stringResource(R.string.reaction_pick), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val list = choices
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else {
            FlowRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { type ->
                    Box(
                        Modifier.size(52.dp).clip(CtrlShapes.Control).border(1.dp, CtrlColors.ChipBorder, CtrlShapes.Control)
                            .clickable(role = Role.Button) { onPick(type) },
                        contentAlignment = Alignment.Center,
                    ) { Text(type, style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    }
}

/** Picks one of your own memos that is on the server, to link to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkPicker(exclude: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = LocalContext.current.container
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<MemoEntity>?>(null) }
    LaunchedEffect(Unit) {
        all = c.repo.listMemos().filter { m -> m.remoteId != null && exclude.none { sameMemo(it, m.remoteId) } }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CtrlColors.Surface, shape = CtrlSheetShape, tonalElevation = 0.dp) {
        CtrlLabel(stringResource(R.string.link_add), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        CtrlTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), placeholder = stringResource(R.string.link_search_hint))
        val list = all
        val shown = remember(list, query) {
            val q = query.trim()
            list.orEmpty().filter { q.isEmpty() || it.content.contains(q, ignoreCase = true) }.take(50)
        }
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            shown.isEmpty() -> Text(
                stringResource(R.string.link_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(Modifier.navigationBarsPadding().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(shown, key = { it.identifier }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clickable { m.remoteId?.let(onPick) }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            m.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { stringResource(R.string.memo) },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(relativeTime(m.date.toEpochMilli()), style = CtrlType.Mono, color = CtrlColors.Muted)
                    }
                }
            }
        }
    }
}
