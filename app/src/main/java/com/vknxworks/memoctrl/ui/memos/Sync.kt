package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.AppContainer
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.auth.SyncCheck
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.memoctrl.R

sealed interface SyncAlert {
    data class Blocked(val message: String) : SyncAlert
    data class Confirm(val version: String, val message: String) : SyncAlert
    data class Failed(val message: String) : SyncAlert
}

/** A sync the user asked for (pull to refresh, the sync button). Unlike background syncs, it explains problems. */
class ManualSync(private val c: AppContainer, private val scope: CoroutineScope) {
    var alert by mutableStateOf<SyncAlert?>(null)
    var running by mutableStateOf(false)
        private set

    fun start(allowVersion: String? = null, onDone: () -> Unit = {}) {
        if (running) return
        val session = c.sessions.active.value ?: return
        running = true
        scope.launch {
            try {
                when (val check = c.sessions.checkSync(automatic = false, allowVersion = allowVersion)) {
                    SyncCheck.Allowed -> {
                        session.repo.sync()
                        if (allowVersion != null) c.sessions.acceptVersion(allowVersion)
                        Widgets.updateAll(c.context)
                    }
                    is SyncCheck.Blocked -> alert = SyncAlert.Blocked(check.message ?: c.context.getString(R.string.sync_unsupported))
                    is SyncCheck.NeedsConfirmation -> alert = SyncAlert.Confirm(check.version, check.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                alert = SyncAlert.Failed(e.friendlyMessage())
            } finally {
                running = false
                onDone()
            }
        }
    }
}

@Composable
fun rememberManualSync(): ManualSync {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val sync = remember { ManualSync(c, scope) }
    SyncDialogs(sync)
    return sync
}

@Composable
private fun SyncDialogs(sync: ManualSync) {
    when (val a = sync.alert) {
        null -> Unit
        is SyncAlert.Blocked -> AlertDialog(
            onDismissRequest = { sync.alert = null },
            title = { Text(stringResource(R.string.sync_unsupported_title)) },
            text = { Text(a.message) },
            confirmButton = { TextButton(onClick = { sync.alert = null }) { Text(stringResource(R.string.close)) } },
        )
        is SyncAlert.Confirm -> AlertDialog(
            onDismissRequest = { sync.alert = null },
            title = { Text(stringResource(R.string.sync_newer_title)) },
            text = { Text(a.message) },
            confirmButton = {
                TextButton(onClick = {
                    sync.alert = null
                    sync.start(allowVersion = a.version)
                }) { Text(stringResource(R.string.sync_anyway)) }
            },
            dismissButton = { TextButton(onClick = { sync.alert = null }) { Text(stringResource(R.string.cancel)) } },
        )
        is SyncAlert.Failed -> AlertDialog(
            onDismissRequest = { sync.alert = null },
            title = { Text(stringResource(R.string.sync_failed_title)) },
            text = { Text(stringResource(R.string.sync_failed_body, a.message)) },
            confirmButton = { TextButton(onClick = { sync.alert = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** Sync state at a glance: spinning while syncing, a count of changes waiting, red when the last sync failed. */
@Composable
fun SyncButton(sync: ManualSync) {
    val c = LocalContext.current.container
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val repo = session?.repo ?: return
    val status by repo.syncStatus.collectAsStateWithLifecycle()
    IconButton(onClick = { sync.start() }, enabled = !status.syncing && !sync.running) {
        when {
            status.syncing || sync.running -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            status.errorMessage != null -> Icon(Icons.Outlined.CloudOff, stringResource(R.string.sync_failed_now), tint = MaterialTheme.colorScheme.error)
            status.unsyncedCount > 0 -> BadgedBox(badge = { Badge { Text(status.unsyncedCount.coerceAtMost(99).toString()) } }) {
                Icon(Icons.Outlined.Sync, pluralStringResource(R.plurals.sync_pending_now, status.unsyncedCount, status.unsyncedCount))
            }
            else -> Icon(Icons.Outlined.Sync, stringResource(R.string.sync_now), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
