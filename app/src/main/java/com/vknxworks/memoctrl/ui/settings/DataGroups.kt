package com.vknxworks.memoctrl.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.BuildConfig
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.files.MemoZip
import com.vknxworks.memoctrl.data.settings.BackupFormatException
import com.vknxworks.memoctrl.data.settings.EditGesture
import com.vknxworks.memoctrl.data.settings.SettingsBackup
import com.vknxworks.memoctrl.data.settings.SettingsBackups
import com.vknxworks.memoctrl.data.settings.SyncInterval
import com.vknxworks.memoctrl.ui.components.CtrlAlertDialog
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime

private const val MAX_SETTINGS_BYTES = 256 * 1024

/**
 * The two "to a file" groups of Settings: your memos as a ZIP file (export and import), and the app's
 * settings as a small JSON file (export and import, without any login).
 */
@Composable
fun DataGroups() {
    MemoZipGroup()
    SettingsFileGroup()
}

@Composable
private fun MemoZipGroup() {
    val context = LocalContext.current
    val c = context.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    // The jobs live in the app's scope (see MemoZipTasks): leaving Settings or the app lock doesn't stop them.
    val tasks = c.zipTasks
    val progress by tasks.progress.collectAsStateWithLifecycle()
    val pending by tasks.plan.collectAsStateWithLifecycle()
    val message by tasks.message.collectAsStateWithLifecycle()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        tasks.export(
            uri = uri,
            repo = s.repo,
            http = if (s.account.isLocal) null else c.http,
            server = if (s.account.isLocal) null else s.account.host,
            username = s.account.username,
            displayName = s.account.displayName,
        )
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        tasks.read(uri, s.repo)
    }

    Group(stringResource(R.string.group_memo_files), footer = stringResource(R.string.memo_files_footer)) {
        GroupRow(
            stringResource(R.string.zip_export),
            stringResource(R.string.zip_export_subtitle),
            icon = Icons.Outlined.FolderZip,
            onClick = { AppLock.expectReturn(); save.launch(MemoZip.fileName(s.account.username)) },
        )
        GroupRow(
            stringResource(R.string.zip_import),
            stringResource(R.string.zip_import_subtitle),
            icon = Icons.Outlined.Unarchive,
            onClick = { AppLock.expectReturn(); open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        )
    }

    progress?.let { pr ->
        CtrlAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(if (pr.export) R.string.zip_exporting else R.string.zip_importing)) },
            text = { Text(if (pr.total > 0) stringResource(R.string.zip_progress, pr.done, pr.total) else stringResource(R.string.zip_reading)) },
            confirmButton = { TextButton(onClick = { tasks.cancel() }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // A ZIP read for another account (you switched accounts since) is not offered for this one.
    LaunchedEffect(pending, s.repo.accountKey) {
        if (pending != null && pending?.accountKey != s.repo.accountKey) tasks.discardPlan()
    }

    pending?.takeIf { it.accountKey == s.repo.accountKey && progress == null }?.plan?.let { p ->
        val discard = { tasks.discardPlan() }
        CtrlAlertDialog(
            onDismissRequest = discard,
            title = { Text(stringResource(R.string.zip_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (p.memos.isEmpty()) stringResource(R.string.zip_import_nothing)
                        else pluralStringResource(R.plurals.zip_import_body, p.memos.size, p.memos.size, p.attachments, s.account.title),
                    )
                    if (p.existing > 0) Text(pluralStringResource(R.plurals.zip_import_existing, p.existing, p.existing))
                    if (p.comments > 0) Text(pluralStringResource(R.plurals.zip_import_comments, p.comments, p.comments))
                    if (!p.memosFormat) Text(stringResource(R.string.zip_import_plain))
                }
            },
            confirmButton = {
                if (p.memos.isNotEmpty()) TextButton(onClick = { tasks.import(s.repo) }) { Text(stringResource(R.string.zip_import_confirm)) }
                else TextButton(onClick = discard) { Text(stringResource(R.string.close)) }
            },
            dismissButton = if (p.memos.isNotEmpty()) { { TextButton(onClick = discard) { Text(stringResource(R.string.cancel)) } } } else null,
        )
    }

    message?.let { text ->
        CtrlAlertDialog(
            onDismissRequest = { tasks.dismissMessage() },
            title = { Text(stringResource(R.string.group_memo_files)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { tasks.dismissMessage() }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SettingsFileGroup() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<SettingsBackup?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val app = context.applicationContext
        // In the app's scope: leaving Settings right after picking the file doesn't cut it short.
        c.appScope.launch {
            val ok = runCatching {
                val text = SettingsBackups.export(c.settings.settings.value, BuildConfig.VERSION_NAME, OffsetDateTime.now().toString())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: throw java.io.IOException()
                }
            }.isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(app, if (ok) R.string.settings_file_saved else R.string.settings_file_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // A settings file is a few hundred bytes; anything far bigger isn't one.
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_SETTINGS_BYTES) throw BackupFormatException(newer = false)
                        }
                        out.toString(Charsets.UTF_8.name())
                    } ?: throw BackupFormatException(newer = false)
                }
                SettingsBackups.parse(text)
            }
            result.onSuccess { pending = it }.onFailure { e ->
                val msg = if (e is BackupFormatException && e.newer) R.string.settings_file_too_new else R.string.settings_file_not_valid
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Group(stringResource(R.string.group_settings_file), footer = stringResource(R.string.settings_file_footer)) {
        GroupRow(
            stringResource(R.string.settings_file_export),
            stringResource(R.string.settings_file_export_subtitle),
            icon = Icons.Outlined.FileDownload,
            onClick = { AppLock.expectReturn(); save.launch("memoctrl-settings-${LocalDate.now()}.json") },
        )
        GroupRow(
            stringResource(R.string.settings_file_import),
            stringResource(R.string.settings_file_import_subtitle),
            icon = Icons.Outlined.FileUpload,
            onClick = { AppLock.expectReturn(); open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
        )
    }

    pending?.let { b ->
        val current by c.settings.settings.collectAsStateWithLifecycle()
        val changes = remember(b, current) { SettingsBackups.changes(current, b.settings) }
        val on = stringResource(R.string.setting_on)
        val off = stringResource(R.string.setting_off)
        CtrlAlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_file_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_file_from, b.createdAt?.take(10) ?: "?"))
                    if (changes.isEmpty()) Text(stringResource(R.string.settings_file_no_changes))
                    else {
                        Text(stringResource(R.string.settings_file_will_change))
                        changes.forEach { (key, old, new) ->
                            Text(stringResource(R.string.settings_file_change, stringResource(settingLabel(key)), settingValue(old, on, off), settingValue(new, on, off)))
                        }
                    }
                    Text(stringResource(R.string.settings_file_untouched))
                }
            },
            confirmButton = {
                if (changes.isEmpty()) TextButton(onClick = { pending = null }) { Text(stringResource(R.string.close)) }
                else TextButton(onClick = {
                    pending = null
                    val app = context.applicationContext
                    // In the app's scope, so leaving Settings straight away doesn't stop it halfway.
                    c.appScope.launch {
                        val ok = runCatching { c.settings.update { SettingsBackups.apply(it, b.settings) } }.isSuccess
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, if (ok) R.string.settings_file_imported else R.string.settings_file_import_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.settings_file_import_confirm)) }
            },
            dismissButton = if (changes.isEmpty()) null else { { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } } },
        )
    }
}

@Composable
private fun settingValue(raw: String, on: String, off: String): String = when (raw) {
    "true" -> on
    "false" -> off
    else -> EditGesture.entries.firstOrNull { it.name == raw }?.let { stringResource(it.label) }
        ?: SyncInterval.entries.firstOrNull { it.name == raw }?.let { stringResource(it.label) }
        ?: raw
}

private fun settingLabel(key: String): Int = when (key) {
    "editGesture" -> R.string.edit_gesture
    "autosave" -> R.string.autosave
    "searchIncludesArchived" -> R.string.search_archived
    "externalImages" -> R.string.external_images
    "hideScreenshots" -> R.string.hide_screenshots
    "hideRecents" -> R.string.hide_recents
    "syncInterval" -> R.string.background_sync
    "syncWifiOnly" -> R.string.sync_wifi_only
    "syncCharging" -> R.string.sync_charging
    "liveUpdates" -> R.string.live_updates
    "linkPreviews" -> R.string.link_previews
    else -> R.string.map_tiles
}
