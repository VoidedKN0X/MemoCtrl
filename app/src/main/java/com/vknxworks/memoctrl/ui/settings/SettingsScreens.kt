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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
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
import com.vknxworks.memoctrl.data.settings.SyncInterval
import com.vknxworks.memoctrl.data.sync.BackgroundSync
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.CloudUpload
import com.vknxworks.memoctrl.ui.components.ChoiceSheet
import androidx.compose.material.icons.outlined.Wifi
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.runTask
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupChoice
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.components.GroupSwitch
import com.vknxworks.memoctrl.ui.components.ImageGrid
import com.vknxworks.memoctrl.ui.components.AttachmentChips
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.MessageView
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.components.ctrlSegmentColors
import com.vknxworks.memoctrl.ui.components.CtrlStatus
import com.vknxworks.memoctrl.ui.components.ctrlSegmentShape
import com.vknxworks.memoctrl.ui.lock.AppLock
import com.vknxworks.memoctrl.ui.lock.PinRequest
import com.vknxworks.memoctrl.data.lock.LockVault
import com.vknxworks.memoctrl.ui.lock.findActivity
import com.vknxworks.memoctrl.ui.memos.MemoCard
import com.vknxworks.memoctrl.ui.nav.AddAccountRoute
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Layers

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) { scope.launch { c.settings.update(t) } }
    val bioAvailable = remember { AppLock.biometricAvailable(context) }
    var certificate by remember { mutableStateOf(ClientCertificate.selectedAlias(context)) }
    var removingCertificate by remember { mutableStateOf(false) }
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val local = session?.account?.isLocal == true
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) update { it.copy(syncFailureNotice = true) }
        else Toast.makeText(context, context.getString(R.string.sync_failure_notice_denied), Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // No Appearance group: Ctrl / Signal is dark only, so there's no theme or Material You colour to pick.
            Group(stringResource(R.string.group_privacy), footer = stringResource(R.string.privacy_footer)) {
                val lockOn = c.vault.enabled
                GroupSwitch(
                    stringResource(R.string.lock_app),
                    stringResource(R.string.lock_app_subtitle),
                    lockOn,
                    icon = Icons.Outlined.Lock,
                ) { on -> AppLock.request = if (on) PinRequest.ENABLE else PinRequest.DISABLE }
                if (lockOn) {
                    GroupRow(
                        stringResource(R.string.lock_change_pin),
                        stringResource(R.string.lock_change_pin_subtitle, LockVault.PIN_MIN),
                        icon = Icons.Outlined.Password,
                        showChevron = true,
                        onClick = { AppLock.request = PinRequest.CHANGE },
                    )
                    GroupSwitch(
                        stringResource(R.string.lock_fingerprint),
                        stringResource(if (bioAvailable || c.vault.biometricEnabled) R.string.lock_fingerprint_settings_subtitle else R.string.lock_fingerprint_unavailable),
                        c.vault.biometricEnabled,
                        icon = Icons.Outlined.Fingerprint,
                        enabled = bioAvailable || c.vault.biometricEnabled,
                    ) { on ->
                        if (!on) {
                            c.vault.disableBiometric()
                        } else {
                            val activity = context.findActivity() ?: return@GroupSwitch
                            val failed = context.getString(R.string.lock_fingerprint_setup_failed)
                            val cipher = c.vault.biometricEnrolCipher() ?: run {
                                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                                return@GroupSwitch
                            }
                            AppLock.biometricPrompt(
                                activity,
                                context.getString(R.string.lock_fingerprint_enable_title),
                                context.getString(R.string.lock_fingerprint_enable_subtitle),
                                cipher,
                            ) { unlocked ->
                                if (!c.vault.finishBiometricEnrol(unlocked)) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    GroupChoice(
                        stringResource(R.string.lock_again),
                        LockTimeout.entries.map { it to stringResource(it.label) },
                        s.lockTimeout,
                        icon = Icons.Outlined.Timer,
                    ) { v -> update { it.copy(lockTimeout = v) } }
                }
                GroupSwitch(
                    stringResource(R.string.hide_screenshots),
                    stringResource(R.string.hide_screenshots_subtitle),
                    s.hideScreenshots,
                    icon = Icons.Outlined.VisibilityOff,
                ) { v -> update { it.copy(hideScreenshots = v) } }
                GroupSwitch(
                    stringResource(R.string.hide_recents),
                    stringResource(R.string.hide_recents_subtitle),
                    s.hideRecents,
                    icon = Icons.Outlined.Layers,
                ) { v -> update { it.copy(hideRecents = v) } }
                GroupSwitch(
                    stringResource(R.string.external_images),
                    stringResource(R.string.external_images_subtitle),
                    s.externalImages,
                    icon = Icons.Outlined.Image,
                ) { v -> update { it.copy(externalImages = v) } }
                if (!local) GroupRow(
                    stringResource(R.string.client_certificate),
                    certificate?.let { stringResource(R.string.client_certificate_using, it) }
                        ?: stringResource(R.string.client_certificate_subtitle),
                    icon = Icons.Outlined.Badge,
                    showChevron = true,
                    onClick = {
                        val activity = context.findActivity() ?: return@GroupRow
                        ClientCertificate.choose(activity) { ok -> if (ok) certificate = ClientCertificate.selectedAlias(context) }
                    },
                )
                if (certificate != null && !local) {
                    GroupRow(
                        stringResource(R.string.client_certificate_stop),
                        icon = Icons.Outlined.Lock,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { removingCertificate = true },
                    )
                }
            }

            if (!local) {
                Group(stringResource(R.string.group_background_sync), footer = stringResource(R.string.background_sync_footer)) {
                    GroupChoice(
                        stringResource(R.string.background_sync),
                        SyncInterval.entries.map { it to stringResource(it.label) },
                        s.syncInterval,
                        icon = Icons.Outlined.Sync,
                    ) { v -> update { it.copy(syncInterval = v) } }
                    if (s.syncInterval != SyncInterval.OFF) {
                        GroupSwitch(
                            stringResource(R.string.sync_wifi_only),
                            stringResource(R.string.sync_wifi_only_subtitle),
                            s.syncWifiOnly,
                            icon = Icons.Outlined.Wifi,
                        ) { v -> update { it.copy(syncWifiOnly = v) } }
                        GroupSwitch(
                            stringResource(R.string.sync_charging),
                            stringResource(R.string.sync_charging_subtitle),
                            s.syncCharging,
                            icon = Icons.Outlined.BatteryChargingFull,
                        ) { v -> update { it.copy(syncCharging = v) } }
                        GroupSwitch(
                            stringResource(R.string.sync_failure_notice),
                            stringResource(R.string.sync_failure_notice_subtitle),
                            s.syncFailureNotice && BackgroundSync.canNotify(context),
                            icon = Icons.Outlined.NotificationsNone,
                        ) { v ->
                            if (v && !BackgroundSync.canNotify(context)) {
                                AppLock.expectReturn()
                                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                update { it.copy(syncFailureNotice = v) }
                            }
                        }
                    }
                }
            }

            Group(stringResource(R.string.group_writing)) {
                GroupChoice(
                    stringResource(R.string.edit_gesture),
                    EditGesture.entries.map { it to stringResource(it.label) },
                    s.editGesture,
                    icon = Icons.Outlined.Edit,
                ) { v -> update { it.copy(editGesture = v) } }
                GroupSwitch(
                    stringResource(R.string.autosave),
                    stringResource(R.string.autosave_subtitle),
                    s.autosave,
                    icon = Icons.Outlined.Save,
                ) { v -> update { it.copy(autosave = v) } }
            }

            Group(stringResource(R.string.group_search)) {
                GroupSwitch(
                    stringResource(R.string.search_archived),
                    stringResource(R.string.search_archived_subtitle),
                    s.searchIncludesArchived,
                    icon = Icons.Outlined.Archive,
                ) { v -> update { it.copy(searchIncludesArchived = v) } }
            }
        }
    }

    if (removingCertificate) {
        ConfirmDialog(
            stringResource(R.string.client_certificate_stop_title),
            stringResource(R.string.client_certificate_stop_body),
            stringResource(R.string.client_certificate_stop_confirm),
            onDismiss = { removingCertificate = false },
        ) {
            removingCertificate = false
            ClientCertificate.clear(context)
            certificate = null
            Toast.makeText(context, context.getString(R.string.client_certificate_removed), Toast.LENGTH_SHORT).show()
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
        ScreenTopBar(stringResource(R.string.accounts_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Group(footer = stringResource(R.string.accounts_footer)) {
                accounts.forEach { a ->
                    val isActive = a.key == active?.account?.key
                    GroupRow(
                        a.title,
                        if (a.isLocal) stringResource(R.string.local_account_subtitle) else listOfNotNull(
                            a.username.takeIf { it.isNotBlank() && it != a.title }?.let { "@$it" },
                            a.host,
                            if (a.isEncrypted) null else stringResource(R.string.not_encrypted),
                        ).joinToString("  ·  "),
                        onClick = if (isActive) null else {
                            { scope.launch { c.sessions.switchTo(a.key) }; Unit }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) CtrlStatus(stringResource(R.string.account_active), active = true)
                                IconButton(onClick = { removing = a.key }) { Icon(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.account_sign_out), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        },
                    )
                }
            }
            Group {
                GroupRow(stringResource(R.string.account_add), icon = Icons.Outlined.Add, showChevron = true, onClick = { nav.navigate(AddAccountRoute) })
                // "Use without a server" as an account of its own, when there isn't one yet.
                if (accounts.none { it.isLocal }) {
                    GroupRow(
                        stringResource(R.string.local_use_without_server),
                        stringResource(R.string.local_add_subtitle),
                        icon = Icons.Outlined.PhoneAndroid,
                        onClick = { scope.launch { c.sessions.useWithoutServer() }; Unit },
                    )
                }
            }
            accounts.firstOrNull { it.isLocal }?.let { local ->
                Group(stringResource(R.string.group_local), footer = stringResource(R.string.local_footer)) {
                    MoveLocalMemosRow(local.key)
                }
            }
        }
    }

    removing?.let { key ->
        val acc = accounts.firstOrNull { it.key == key }
        ConfirmDialog(
            if (acc?.isLocal == true) stringResource(R.string.local_remove_title)
            else stringResource(R.string.sign_out_title, acc?.serverName ?: stringResource(R.string.this_server)),
            stringResource(if (acc?.isLocal == true) R.string.local_remove_body else R.string.sign_out_account_body),
            stringResource(if (acc?.isLocal == true) R.string.local_remove else R.string.sign_out),
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
        ScreenTopBar(stringResource(R.string.archive_title))
        val list = memos
        when {
            list == null -> LoadingView()
            list.isEmpty() -> MessageView(
                Icons.Outlined.Archive,
                stringResource(R.string.archive_empty_title),
                stringResource(R.string.archive_empty_body),
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
        ScreenTopBar(stringResource(R.string.attachments_title))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = ctrlSegmentShape(0, 2), colors = ctrlSegmentColors(), icon = {}) { Text(stringResource(R.string.attachments_pictures)) }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = ctrlSegmentShape(1, 2), colors = ctrlSegmentColors(), icon = {}) { Text(stringResource(R.string.attachments_files)) }
        }
        val list = all
        when {
            list == null -> LoadingView()
            images -> {
                val pics = list.filter { it.isImage }
                if (pics.isEmpty()) MessageView(Icons.Outlined.Image, stringResource(R.string.attachments_no_pictures), stringResource(R.string.attachments_no_pictures_body))
                else Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 24.dp)) {
                    ImageGrid(pics)
                }
            }
            else -> {
                val files = list.filterNot { it.isImage }
                if (files.isEmpty()) MessageView(Icons.Outlined.Attachment, stringResource(R.string.attachments_no_files), stringResource(R.string.attachments_no_files_body))
                else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttachmentChips(files)
                }
            }
        }
    }
}

/**
 * "Move memos to a server": pick one of your server accounts, and every memo kept only on this phone
 * (with its pictures and files) becomes a new memo there, uploaded by the next sync. Without a server
 * account yet, it offers to add one first.
 */
@Composable
fun MoveLocalMemosRow(localKey: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val accounts by c.sessions.accounts.collectAsStateWithLifecycle()
    val servers = accounts.filterNot { it.isLocal }
    var picking by remember { mutableStateOf(false) }
    var noServer by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    val changes by c.db.changes.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(localKey, changes) {
        count = runCatching { c.db.getAllMemosForSync(localKey).count { !it.isDeleted } }.getOrNull()
    }

    GroupRow(
        stringResource(R.string.local_move),
        count?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.local_move_subtitle, it, it) },
        icon = Icons.Outlined.CloudUpload,
        showChevron = true,
        onClick = { if (servers.isEmpty()) noServer = true else picking = true },
    )

    if (picking) {
        ChoiceSheet(
            stringResource(R.string.local_move_pick),
            servers.map { it.key to listOf(it.title, it.username.takeIf { u -> u.isNotBlank() && u != it.title }?.let { u -> "@$u" }, it.host).filterNotNull().distinct().joinToString("  ·  ") },
            null,
            onDismiss = { picking = false },
        ) { key ->
            picking = false
            target = key
        }
    }
    target?.let { key ->
        val acc = servers.firstOrNull { it.key == key }
        val n = count ?: 0
        ConfirmDialog(
            stringResource(R.string.local_move_title, acc?.title ?: stringResource(R.string.this_server)),
            androidx.compose.ui.res.pluralStringResource(R.plurals.local_move_body, n, n, acc?.serverName ?: ""),
            stringResource(R.string.local_move_confirm),
            onDismiss = { target = null },
        ) {
            target = null
            c.runTask {
                val moved = c.sessions.moveLocalMemos(localKey, key)
                Toast.makeText(context, context.resources.getQuantityString(R.plurals.local_moved, moved, moved), Toast.LENGTH_LONG).show()
                c.syncInBackground()
            }
        }
    }
    if (noServer) {
        CtrlAlertDialog(
            onDismissRequest = { noServer = false },
            title = { Text(stringResource(R.string.local_move_no_server_title)) },
            text = { Text(stringResource(R.string.local_move_no_server_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    noServer = false
                    nav.navigate(AddAccountRoute)
                }) { Text(stringResource(R.string.account_add)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { noServer = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
