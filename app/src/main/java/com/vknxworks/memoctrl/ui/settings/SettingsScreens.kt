package com.vknxworks.memoctrl.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.net.ClientCertificate
import com.vknxworks.memoctrl.data.settings.AppSettings
import com.vknxworks.memoctrl.data.settings.EditGesture
import com.vknxworks.memoctrl.data.settings.LockTimeout
import com.vknxworks.memoctrl.data.settings.ThemeChoice
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupChoice
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.components.GroupSwitch
import com.vknxworks.memoctrl.ui.components.ImageGrid
import com.vknxworks.memoctrl.ui.components.AttachmentChips
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.lock.AppLock
import com.vknxworks.memoctrl.ui.lock.findActivity
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.AddAccountRoute
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val lockAvailable = remember { AppLock.canAuthenticate(context) }
    var certificate by remember { mutableStateOf(ClientCertificate.selectedAlias(context)) }
    var removingCertificate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group("Writing") {
                GroupChoice(
                    "Open a memo in the editor with",
                    EditGesture.entries.map { it to it.label },
                    s.editGesture,
                    icon = Icons.Outlined.Edit,
                ) { v -> update { it.copy(editGesture = v) } }
                GroupSwitch(
                    "Autosave",
                    "Save every change as you type and sync it in the background, instead of when you tap Post",
                    s.autosave,
                    icon = Icons.Outlined.Save,
                ) { v -> update { it.copy(autosave = v) } }
            }

            Group("Search") {
                GroupSwitch(
                    "Include archived memos",
                    "Search results also show archived memos that match",
                    s.searchIncludesArchived,
                    icon = Icons.Outlined.Archive,
                ) { v -> update { it.copy(searchIncludesArchived = v) } }
            }

            Group("Appearance") {
                GroupChoice("Theme", ThemeChoice.entries.map { it to it.label }, s.theme) { v -> update { it.copy(theme = v) } }
                GroupSwitch("Use wallpaper colours", "Replaces the default colours with ones from your wallpaper", s.dynamicColor) { v ->
                    update { it.copy(dynamicColor = v) }
                }
            }

            Group(
                "Privacy and security",
                footer = "MemoCtrl has no analytics or tracking. It only talks to your own Memos server.",
            ) {
                GroupSwitch(
                    "App lock",
                    if (lockAvailable || s.appLock) "Ask for your fingerprint, face or screen lock when opening MemoCtrl. The recent-apps screen and widgets hide your memos."
                    else "Set up a fingerprint, face or screen lock on this phone first",
                    s.appLock,
                    icon = Icons.Outlined.Lock,
                    enabled = lockAvailable || s.appLock,
                ) { on ->
                    if (!on) {
                        update { it.copy(appLock = false) }
                    } else {
                        val activity = context.findActivity() ?: return@GroupSwitch
                        // Confirm it works before turning it on, so nobody locks themselves out.
                        AppLock.authenticate(activity, "Turn on app lock") { error ->
                            if (error == null) update { it.copy(appLock = true) }
                        }
                    }
                }
                if (s.appLock) {
                    GroupChoice(
                        "Lock again",
                        LockTimeout.entries.map { it to it.label },
                        s.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { v -> update { it.copy(lockTimeout = v) } }
                }
                GroupSwitch(
                    "Load pictures from other websites",
                    "Pictures in memos that link to other sites. Off: they show as a link, so those sites can't see when you read your memos",
                    s.externalImages,
                    icon = Icons.Outlined.Image,
                ) { v -> update { it.copy(externalImages = v) } }
                GroupRow(
                    "Client certificate",
                    certificate?.let { "Using \"$it\". Tap to pick another" }
                        ?: "For servers behind a proxy that asks for one (mutual TLS). The certificate stays in Android's certificate store",
                    icon = Icons.Outlined.Badge,
                    showChevron = true,
                    onClick = {
                        val activity = context.findActivity() ?: return@GroupRow
                        ClientCertificate.choose(activity) { ok -> if (ok) certificate = ClientCertificate.selectedAlias(context) }
                    },
                )
                if (certificate != null) {
                    GroupRow(
                        "Stop using the certificate",
                        icon = Icons.Outlined.Lock,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { removingCertificate = true },
                    )
                }
            }
        }
    }

    if (removingCertificate) {
        ConfirmDialog(
            "Stop using the client certificate?",
            "MemoCtrl won't send it anymore. It stays installed on your phone.",
            "Stop using",
            onDismiss = { removingCertificate = false },
        ) {
            removingCertificate = false
            ClientCertificate.clear(context)
            certificate = null
            Toast.makeText(context, "Client certificate removed", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AccountsScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val accounts by c.sessions.accounts.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Servers and accounts")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Group(footer = "Tap an account to switch to it. Each account keeps its own memos on this phone.") {
                accounts.forEach { a ->
                    val isActive = a.key == active?.account?.key
                    GroupRow(
                        a.title,
                        listOfNotNull(
                            a.username.takeIf { it.isNotBlank() && it != a.title }?.let { "@$it" },
                            a.host,
                            if (a.isEncrypted) null else "Not encrypted (HTTP)",
                        ).joinToString("  ·  "),
                        onClick = if (isActive) null else {
                            { scope.launch { c.sessions.switchTo(a.key) }; Unit }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) Icon(Icons.Filled.CheckCircle, "Active", tint = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { removing = a.key }) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out of this account") }
                            }
                        },
                    )
                }
            }
            Group {
                GroupRow("Add a server or account", icon = Icons.Filled.Add, showChevron = true, onClick = { nav.navigate(AddAccountRoute) })
            }
        }
    }

    removing?.let { key ->
        val acc = accounts.firstOrNull { it.key == key }
        ConfirmDialog(
            "Sign out of ${acc?.serverName ?: "this server"}?",
            "Memos kept on this phone for this account are removed, including changes that haven't synced. They stay on your server.",
            "Sign out",
            onDismiss = { removing = null },
        ) {
            removing = null
            c.appScope.launch { c.sessions.signOut(key) }
        }
    }
}

/** Archived memos: restore them to the timeline, or delete them for good. */
@Composable
fun ArchivedScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val memos by remember(repo) { repo.observeArchived() }.collectAsStateWithLifecycle(initialValue = null)
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Archive")
        val list = memos
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(
                Icons.Outlined.Archive,
                "Nothing archived",
                "Archive a memo from its menu to take it off your timeline without deleting it.",
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                items(list, key = { it.identifier }) { m -> MemoCard(m, onOpen = { nav.memo(m.identifier) }) }
            }
        }
    }
}

/** Every picture and file in your memos, newest first. */
@Composable
fun AttachmentsScreen() {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val changes by c.db.changes.collectAsStateWithLifecycle()
    var all by remember { mutableStateOf<List<com.vknxworks.memoctrl.data.model.ResourceEntity>?>(null) }
    androidx.compose.runtime.LaunchedEffect(repo, changes) { all = runCatching { repo.listResources() }.getOrDefault(emptyList()) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val images = tab == 0

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Attachments")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Pictures") }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Files") }
        }
        val list = all
        when {
            list == null -> LoadingView()
            images -> {
                val pics = list.filter { it.isImage }
                if (pics.isEmpty()) MessageView(Icons.Outlined.Image, "No pictures yet", "Pictures you add to memos show up here.")
                else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 24.dp)) {
                    ImageGrid(pics)
                }
            }
            else -> {
                val files = list.filterNot { it.isImage }
                if (files.isEmpty()) MessageView(Icons.Outlined.Attachment, "No files yet", "Files you attach to memos show up here.")
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentChips(files)
                }
            }
        }
    }
}
