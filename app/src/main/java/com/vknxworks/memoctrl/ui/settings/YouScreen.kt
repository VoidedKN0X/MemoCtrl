package com.vknxworks.memoctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.vknxworks.memoctrl.ui.components.CtrlButton
import com.vknxworks.memoctrl.ui.components.initialsOf
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlType
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.BuildConfig
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.auth.ServerApi
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.Group
import com.vknxworks.memoctrl.ui.components.GroupRow
import com.vknxworks.memoctrl.ui.components.LocalBottomClearance
import com.vknxworks.memoctrl.ui.components.ScreenTopBar
import com.vknxworks.memoctrl.ui.memos.SyncButton
import com.vknxworks.memoctrl.ui.memos.label
import com.vknxworks.memoctrl.ui.memos.rememberManualSync
import com.vknxworks.memoctrl.ui.nav.AccountsRoute
import com.vknxworks.memoctrl.ui.nav.ArchivedRoute
import com.vknxworks.memoctrl.ui.nav.AttachmentsRoute
import com.vknxworks.memoctrl.ui.nav.LocalNavigator
import com.vknxworks.memoctrl.ui.nav.SettingsRoute
import com.vknxworks.memoctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.pluralStringResource

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/MemoCtrl"
private const val ISSUES_URL = "$REPO_URL/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** The You page, opened from your profile picture: who you're signed in as, sync, archive, settings. */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val session by c.sessions.active.collectAsStateWithLifecycle()
    val s = session ?: return
    val acc = s.account
    val status by s.repo.syncStatus.collectAsStateWithLifecycle()
    var confirmSignOut by remember { mutableStateOf(false) }
    val sync = rememberManualSync()
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.you_title))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp + LocalBottomClearance.current)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initialsOf(acc.title), style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(acc.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (acc.isLocal) stringResource(R.string.local_account_subtitle) else acc.host, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val meta = if (acc.isLocal) "" else listOfNotNull(
                        "@${acc.username}".takeIf { acc.username.isNotBlank() && acc.username != acc.title },
                        acc.serverName,
                        acc.serverVersion?.let { stringResource(R.string.memos_version, it.removePrefix("v")) },
                    ).joinToString("  ·  ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            if (acc.isLocal) {
                Group(stringResource(R.string.group_local), footer = stringResource(R.string.local_footer)) {
                    MoveLocalMemosRow(acc.key)
                }
            } else Group(stringResource(R.string.group_sync)) {
                GroupRow(
                    when {
                        status.syncing -> stringResource(R.string.sync_syncing)
                        status.errorMessage != null -> stringResource(R.string.sync_failed)
                        status.unsyncedCount > 0 -> pluralStringResource(R.plurals.sync_waiting, status.unsyncedCount, status.unsyncedCount)
                        else -> stringResource(R.string.sync_all_done)
                    },
                    status.errorMessage ?: stringResource(R.string.sync_explainer),
                    icon = if (status.errorMessage != null) Icons.Outlined.CloudOff else Icons.Outlined.Sync,
                    iconTint = if (status.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    onClick = { sync.start() },
                    trailing = { SyncButton(sync) },
                )
            }

            Group {
                GroupRow(stringResource(R.string.archive_title), stringResource(R.string.archive_subtitle), icon = Icons.Outlined.Archive, showChevron = true, onClick = { nav.navigate(ArchivedRoute) })
                GroupRow(stringResource(R.string.attachments_title), stringResource(R.string.attachments_subtitle), icon = Icons.Outlined.Attachment, showChevron = true, onClick = { nav.navigate(AttachmentsRoute) })
            }

            if (!acc.isLocal) Group(stringResource(R.string.group_server)) {
                GroupRow(
                    acc.host,
                    listOfNotNull(
                        if (acc.isEncrypted) null else stringResource(R.string.not_encrypted),
                        stringResource(R.string.default_visibility, acc.defaultVisibility.label.lowercase()),
                        if (acc.api == ServerApi.V0) stringResource(R.string.memos_v0_api) else null,
                    ).joinToString("  ·  "),
                    icon = Icons.Outlined.Dns,
                )
            }

            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Outlined.Tune, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
                GroupRow(stringResource(R.string.accounts_title), icon = Icons.Outlined.SwapHoriz, showChevron = true, onClick = { nav.navigate(AccountsRoute) })
                GroupRow(
                    stringResource(if (acc.isLocal) R.string.local_remove else R.string.sign_out),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmSignOut = true },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Outlined.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Outlined.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Outlined.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }
            CtrlButton(
                stringResource(R.string.support_title),
                onClick = { open(SPONSOR_URL) },
                icon = Icons.Outlined.FavoriteBorder,
                modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 16.dp),
            )
            Text(
                stringResource(R.string.support_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME).uppercase(),
                style = CtrlType.Label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.ctrl_footer),
                style = CtrlType.Label.copy(letterSpacing = 0.12.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmSignOut) {
        if (acc.isLocal) ConfirmDialog(
            stringResource(R.string.local_remove_title),
            stringResource(R.string.local_remove_body),
            stringResource(R.string.local_remove),
            onDismiss = { confirmSignOut = false },
        ) {
            confirmSignOut = false
            c.appScope.launch { c.sessions.signOut(acc.key) }
        } else ConfirmDialog(
            stringResource(R.string.sign_out_title, acc.serverName),
            if (status.unsyncedCount > 0) pluralStringResource(R.plurals.sign_out_unsynced, status.unsyncedCount, status.unsyncedCount)
            else stringResource(R.string.sign_out_body),
            stringResource(R.string.sign_out),
            onDismiss = { confirmSignOut = false },
        ) {
            confirmSignOut = false
            c.appScope.launch { c.sessions.signOut(acc.key) }
        }
    }
}
