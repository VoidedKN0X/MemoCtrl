package com.vknxworks.memoctrl.ui.editor

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.memoctrl.AppContainer
import com.vknxworks.memoctrl.PendingAction
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.model.MemoEntity
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.MemoLocationChange
import com.vknxworks.memoctrl.data.model.MemoVisibility
import com.vknxworks.memoctrl.data.model.ResourceEntity
import com.vknxworks.memoctrl.data.settings.Draft
import com.vknxworks.memoctrl.data.sync.SyncingRepository
import com.vknxworks.memoctrl.ui.components.AttachmentChips
import com.vknxworks.memoctrl.ui.components.ChoiceSheet
import com.vknxworks.memoctrl.ui.components.ImageGrid
import com.vknxworks.memoctrl.ui.components.LocationRow
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.lock.AppLock
import com.vknxworks.memoctrl.ui.memos.icon
import com.vknxworks.memoctrl.ui.memos.label
import com.vknxworks.memoctrl.ui.nav.EditorRoute
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.util.UUID

private const val MAX_IMAGES = 100

/** What the editor keeps while the screen rotates or the app is in the background. */
class EditorModel : ViewModel() {
    val resources = mutableStateListOf<ResourceEntity>()

    /** The memo row autosave writes to; null until the first autosave creates it. */
    @Volatile
    var autosaveId: String? = null
    val autosaveMutex = Mutex()
    var loaded = false
    var baseline: MemoEntity? = null
    var cameraUri: Uri? = null

    /** Saves the text as a memo row right away (autosave). Creates the row the first time. */
    suspend fun autosave(repo: SyncingRepository, content: String, visibility: MemoVisibility, location: MemoLocation?): MemoEntity =
        autosaveMutex.withLock {
            withContext(NonCancellable) {
                val id = autosaveId
                if (id == null) {
                    repo.createMemo(content, visibility, resources.toList(), deferPush = true, location = location).also { autosaveId = it.identifier }
                } else {
                    repo.updateMemo(id, content, null, visibility, deferPush = true, location = MemoLocationChange(location))
                }
            }
        }

    suspend fun flushAutosave(repo: SyncingRepository, content: String, visibility: MemoVisibility, location: MemoLocation?) {
        autosave(repo, content, visibility, location)
        withContext(NonCancellable) { autosaveId?.let { repo.flushPendingPush(it) } }
    }

    suspend fun discardEmptyAutosave(repo: SyncingRepository) = autosaveMutex.withLock {
        withContext(NonCancellable) {
            val id = autosaveId ?: return@withContext
            repo.deleteMemo(id)
            autosaveId = null
        }
    }

    /** Copies a picked file into the app and links it to the memo. */
    suspend fun upload(context: Context, repo: SyncingRepository, uri: Uri, memoId: String?) = autosaveMutex.withLock {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString) else null
            }
        }.getOrNull() ?: ("attachment_${UUID.randomUUID().toString().take(8)}" + (ext?.let { ".$it" } ?: ""))
        val resource = withContext(kotlinx.coroutines.Dispatchers.IO) {
            repo.createResource(name, mime?.toMediaTypeOrNull(), uri, memoId ?: autosaveId)
        }
        resources.add(resource)
    }
}

/**
 * Writing and editing memos: Markdown with a formatting bar, tasks, tags, pictures, files, the
 * camera, a location and who can see it. New memos keep a draft until posted; with autosave on,
 * every change is saved and synced as you type.
 */
@Composable
fun EditorScreen(memoId: String?, fromShare: Boolean, initialText: String?, model: EditorModel = viewModel()) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val s = session ?: return
    val repo = s.repo
    val accountKey = s.account.key
    val autosave = settings.autosave
    val focus = remember { FocusRequester() }

    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var visibility by rememberSaveable { mutableStateOf(s.account.defaultVisibility) }
    var location by remember { mutableStateOf<MemoLocation?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var restored by rememberSaveable { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var showVisibility by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    val isEdit = memoId != null
    val usesDraft = !isEdit && !fromShare && initialText == null

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    /** Where unsaved changes to an existing memo are kept when the app lock closes the editor. */
    val editDraftKey = memoId?.let { "$accountKey|edit:$it" }

    /** An existing memo with nothing changed: autosave must not write it back (it may be older than the phone's copy). */
    fun untouchedExisting(): Boolean {
        val base = model.baseline ?: return false
        return !dirty && text.text == base.content && visibility == base.visibility &&
            location == base.location && model.resources.size == base.resources.size
    }

    fun hasChanges(): Boolean {
        val base = model.baseline
        return text.text != (base?.content ?: "") ||
            location != base?.location ||
            visibility != (base?.visibility ?: s.account.defaultVisibility) ||
            model.resources.size != (base?.resources?.size ?: 0)
    }

    fun close() {
        exiting = true
        nav.close(EditorRoute::class)
    }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                when {
                    autosave && untouchedExisting() -> Unit
                    autosave -> {
                        model.flushAutosave(repo, text.text, visibility, location)
                        if (usesDraft) c.settings.clearDraft(accountKey)
                    }
                    model.baseline != null -> repo.updateMemo(
                        model.baseline!!.identifier,
                        content = text.text,
                        resources = model.resources.toList(),
                        visibility = visibility,
                        location = MemoLocationChange(location),
                    )
                    else -> {
                        repo.createMemo(text.text, visibility, model.resources.toList(), location = location)
                        if (usesDraft) c.settings.clearDraft(accountKey)
                    }
                }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast(e.friendlyMessage())
            } finally {
                busy = false
            }
        }
    }

    fun exit() {
        if (autosave) {
            scope.launch {
                val row = model.autosaveId
                // Only a row this editor created may be thrown away, never the memo being edited.
                val ownsRow = row == null || row != memoId
                if (ownsRow && text.text.isEmpty() && model.resources.isEmpty()) model.discardEmptyAutosave(repo)
                else if (!untouchedExisting()) model.flushAutosave(repo, text.text, visibility, location)
                if (usesDraft) c.settings.clearDraft(accountKey)
                close()
            }
            return
        }
        val empty = text.text.isBlank() && model.resources.isEmpty() && location == null
        when {
            isEdit && hasChanges() -> showExitDialog = true
            isEdit -> close()
            !empty && usesDraft -> showExitDialog = true
            !empty -> showExitDialog = true
            else -> {
                if (usesDraft) scope.launch { c.settings.clearDraft(accountKey) }
                close()
            }
        }
    }

    /** Leaves without saving: files picked in this editor are deleted again. */
    fun discard() {
        val kept = model.baseline?.resources.orEmpty().mapTo(HashSet()) { it.identifier }
        model.resources.filter { it.identifier !in kept }.forEach { r ->
            c.appScope.launch { runCatching { repo.deleteResource(r.identifier) } }
        }
        if (usesDraft) c.appScope.launch { c.settings.clearDraft(accountKey) }
        close()
    }

    fun keepDraft() {
        val d = Draft(text.text, location)
        c.appScope.launch { c.settings.saveDraft(accountKey, d) }
        close()
    }

    // The app lock closes the editor; keep what was typed so it's there after unlocking.
    // (Autosave already wrote it.)
    val latestText by rememberUpdatedState(text)
    val latestLocation by rememberUpdatedState(location)
    DisposableEffect(Unit) {
        val hook = {
            if (!autosave && !exiting && hasChanges()) {
                val d = Draft(latestText.text, latestLocation)
                c.appScope.launch { c.settings.saveDraft(editDraftKey ?: accountKey, d) }
            }
        }
        AppLock.saveBeforeLock = hook
        onDispose { if (AppLock.saveBeforeLock === hook) AppLock.saveBeforeLock = null }
    }

    // ---------- Pickers ----------

    fun uploadAll(uris: List<Uri>) {
        scope.launch {
            uris.take(MAX_IMAGES).forEach { uri ->
                try {
                    model.upload(context, repo, uri, memoId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    toast("Couldn't add the file: ${e.friendlyMessage()}")
                }
            }
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) uploadAll(uris)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uploadAll(listOf(uri))
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = model.cameraUri
        if (ok && uri != null) uploadAll(listOf(uri))
    }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            model.cameraUri = uri
            AppLock.expectReturn()
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            toast("No camera app found")
        } catch (e: Exception) {
            toast(e.friendlyMessage())
        }
    }

    // ---------- Loading ----------

    LaunchedEffect(Unit) {
        tags = runCatching { repo.listTags() }.getOrDefault(emptyList())
        if (!model.loaded) {
            model.loaded = true
            model.autosaveId = memoId
            val row = memoId?.let { repo.getMemo(it) }
            when {
                row != null -> {
                    model.baseline = row
                    model.resources.clear()
                    model.resources.addAll(row.resources)
                    location = row.location
                    if (!restored) {
                        text = TextFieldValue(row.content, TextRange(row.content.length))
                        visibility = row.visibility
                        // Changes kept when the app lock closed this editor last time.
                        if (editDraftKey != null) {
                            val kept = c.settings.draft(editDraftKey)
                            if (kept.content.isNotEmpty()) {
                                text = TextFieldValue(kept.content, TextRange(kept.content.length))
                                location = kept.location
                                c.settings.clearDraft(editDraftKey)
                                toast("Restored your unsaved changes")
                            }
                        }
                    }
                }
                fromShare -> {
                    val share = c.pendingAction.value as? PendingAction.Share
                    c.pendingAction.value = null
                    if (share != null && !restored) {
                        text = TextFieldValue(share.text, TextRange(share.text.length))
                        uploadAll(share.images)
                    }
                }
                initialText != null && !restored -> text = TextFieldValue(initialText, TextRange(initialText.length))
                usesDraft && !restored -> {
                    val draft = c.settings.draft(accountKey)
                    text = TextFieldValue(draft.content, TextRange(draft.content.length))
                    location = draft.location
                }
            }
        }
        restored = true
        initialized = true
        delay(250)
        runCatching { focus.requestFocus() }
    }

    // Autosave: every change is written (the newest one waits for the previous write).
    LaunchedEffect(initialized, autosave, text.text, visibility, location, model.resources.size) {
        if (!initialized || !autosave || exiting) return@LaunchedEffect
        if (model.autosaveId == null && text.text.isEmpty() && model.resources.isEmpty()) return@LaunchedEffect
        if (untouchedExisting()) return@LaunchedEffect
        dirty = true
        runCatching { model.autosave(repo, text.text, visibility, location) }
    }

    // Leaving the app mid-edit: push autosaved changes now, or keep the draft.
    DisposableEffect(lifecycleOwner, autosave) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !exiting && initialized) {
                if (autosave && dirty) {
                    c.appScope.launch { runCatching { model.flushAutosave(repo, text.text, visibility, location) } }
                } else if (!autosave && usesDraft) {
                    val d = Draft(text.text, location)
                    c.appScope.launch { c.settings.saveDraft(accountKey, d) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Closing a new memo without posting keeps it as a draft for next time.
    DisposableEffect(Unit) {
        onDispose {
            if (!autosave && usesDraft && initialized && !exiting) {
                val d = Draft(text.text, location)
                c.appScope.launch { c.settings.saveDraft(accountKey, d) }
            }
        }
    }

    BackHandler { exit() }

    val canSubmit = initialized && !busy && (text.text.isNotBlank() || model.resources.isNotEmpty())

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (isEdit) "Edit memo" else "New memo", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = ::exit) { Icon(Icons.Filled.Close, "Close") } },
            actions = {
                Button(
                    onClick = ::submit,
                    enabled = canSubmit,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Text(if (isEdit) "Save" else "Post", modifier = Modifier.padding(start = 8.dp))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            TextField(
                value = text,
                onValueChange = { updated ->
                    // Enter on a list item continues the list.
                    if (updated.text.length == text.text.length + 1 && updated.selection.collapsed &&
                        updated.selection.start > 0 && updated.text[updated.selection.start - 1] == '\n' &&
                        text.selection.collapsed
                    ) {
                        continueList(text)?.let {
                            text = it
                            return@TextField
                        }
                    }
                    text = updated
                },
                placeholder = { Text("Any thoughts…") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).focusRequester(focus),
            )
            val images = model.resources.filter { it.isImage }
            val files = model.resources.filterNot { it.isImage }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                ImageGrid(images, onRemove = { r -> removeResource(c, model, r, autosave) })
                AttachmentChips(files, onRemove = { r -> removeResource(c, model, r, autosave) })
                location?.let { LocationRow(it, onClick = { showLocation = true }) }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Formatting
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FormatText("H1") { text = applyFormat(text, MarkdownFormat.H1) }
            FormatText("H2") { text = applyFormat(text, MarkdownFormat.H2) }
            FormatText("H3") { text = applyFormat(text, MarkdownFormat.H3) }
            ToolButton(Icons.Outlined.FormatBold, "Bold") { text = applyFormat(text, MarkdownFormat.BOLD) }
            ToolButton(Icons.Outlined.FormatItalic, "Italic") { text = applyFormat(text, MarkdownFormat.ITALIC) }
            ToolButton(Icons.Outlined.FormatStrikethrough, "Strikethrough") { text = applyFormat(text, MarkdownFormat.STRIKETHROUGH) }
            ToolButton(Icons.Outlined.Code, "Code") { text = applyFormat(text, MarkdownFormat.CODE) }
            ToolButton(Icons.Outlined.FormatQuote, "Quote") { text = applyFormat(text, MarkdownFormat.QUOTE) }
            ToolButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list") { text = applyFormat(text, MarkdownFormat.BULLET) }
            ToolButton(Icons.Outlined.FormatListNumbered, "Numbered list") { text = applyFormat(text, MarkdownFormat.NUMBERED) }
        }
        // Content
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ToolButton(Icons.Outlined.Tag, "Tag") {
                        if (tags.isEmpty()) text = replaceSelection(text, "#") else showTags = true
                    }
                    DropdownMenu(expanded = showTags, onDismissRequest = { showTags = false }) {
                        DropdownMenuItem(text = { Text("New tag") }, onClick = {
                            showTags = false
                            text = replaceSelection(text, "#")
                        })
                        tags.forEach { t ->
                            DropdownMenuItem(text = { Text("#$t") }, onClick = {
                                showTags = false
                                text = replaceSelection(text, "#$t ")
                            })
                        }
                    }
                }
                ToolButton(Icons.Outlined.CheckBox, "Task") { text = toggleTask(text) }
                ToolButton(Icons.Outlined.Image, "Add pictures") {
                    AppLock.expectReturn()
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ToolButton(Icons.Outlined.PhotoCamera, "Take a photo") { launchCamera() }
                ToolButton(Icons.Outlined.Attachment, "Attach a file") {
                    AppLock.expectReturn()
                    pickFile.launch(arrayOf("*/*"))
                }
                if (s.remote.supportsLocation) ToolButton(Icons.Outlined.LocationOn, "Location") { showLocation = true }
            }
            TextButton(onClick = { showVisibility = true }) {
                Icon(visibility.icon, null, Modifier.size(18.dp))
                Text(visibility.label, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    if (showVisibility) {
        val options = buildList {
            add(MemoVisibility.PRIVATE)
            add(MemoVisibility.PROTECTED)
            add(MemoVisibility.PUBLIC)
            if (visibility == MemoVisibility.SPACE || model.baseline?.visibility == MemoVisibility.SPACE) add(MemoVisibility.SPACE)
        }.map { it to "${it.label} · ${it.descriptionShort()}" }
        ChoiceSheet("Who can see this memo", options, visibility, onDismiss = { showVisibility = false }) {
            visibility = it
            showVisibility = false
        }
    }

    if (showLocation) {
        LocationDialog(location, onConfirm = {
            location = it
            showLocation = false
        }, onDismiss = { showLocation = false })
    }

    if (showExitDialog) {
        if (isEdit) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Save your changes?") },
                text = { Text("You changed this memo. Save the changes before closing?") },
                confirmButton = { TextButton(onClick = { showExitDialog = false; submit() }) { Text("Save") } },
                dismissButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        discard()
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                },
            )
        } else {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text(if (usesDraft) "Keep this as a draft?" else "Post this memo?") },
                text = {
                    Text(
                        if (usesDraft) "It opens again next time you write a new memo. Pictures and files aren't kept in drafts."
                        else "You haven't posted it yet.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        if (usesDraft) keepDraft() else submit()
                    }) { Text(if (usesDraft) "Keep draft" else "Post") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        discard()
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                },
            )
        }
    }
}

private fun MemoVisibility.descriptionShort(): String = when (this) {
    MemoVisibility.PRIVATE -> "only you"
    MemoVisibility.PROTECTED -> "anyone signed in to your server"
    MemoVisibility.PUBLIC -> "anyone with the link"
    MemoVisibility.SPACE -> "members of your space"
}

private fun removeResource(c: AppContainer, model: EditorModel, r: ResourceEntity, autosave: Boolean) {
    model.resources.removeAll { it.identifier == r.identifier }
    // A saved memo keeps its files until it's saved without them (or right away with autosave).
    if (r.memoId == null || autosave) c.appScope.launch { runCatching { c.repo.deleteResource(r.identifier) } }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, label, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun FormatText(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(width = 48.dp, height = 40.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
