package com.vknxworks.memoctrl.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
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
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Text(acc.title.take(1).uppercase(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(14.dp))
                Text(acc.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    listOfNotNull(
                        "@${acc.username}".takeIf { acc.username.isNotBlank() && acc.username != acc.title },
                        acc.serverName,
                        acc.serverVersion?.let { stringResource(R.string.memos_version, it.removePrefix("v")) },
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Group(stringResource(R.string.group_sync)) {
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

            Group(stringResource(R.string.group_server)) {
                GroupRow(
                    acc.host,
                    listOfNotNull(
                        if (acc.isEncrypted) null else stringResource(R.string.not_encrypted),
                        stringResource(R.string.default_visibility, acc.defaultVisibility.label.lowercase()),
                        if (acc.api == ServerApi.V0) stringResource(R.string.memos_v0_api) else null,
                    ).joinToString("  ·  "),
                    icon = Icons.Filled.Dns,
                )
            }

            Group {
                GroupRow(stringResource(R.string.settings), icon = Icons.Filled.Settings, showChevron = true, onClick = { nav.navigate(SettingsRoute) })
                GroupRow(stringResource(R.string.accounts_title), icon = Icons.Filled.SwapHoriz, showChevron = true, onClick = { nav.navigate(AccountsRoute) })
                GroupRow(
                    stringResource(R.string.sign_out),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { confirmSignOut = true },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(stringResource(R.string.support_title), stringResource(R.string.support_subtitle), icon = Icons.Filled.Favorite, showChevron = true, onClick = { open(SPONSOR_URL) })
                GroupRow(stringResource(R.string.bugs_title), stringResource(R.string.bugs_subtitle), icon = Icons.Filled.BugReport, showChevron = true, onClick = { open(ISSUES_URL) })
                GroupRow(stringResource(R.string.source_title), stringResource(R.string.source_subtitle), icon = Icons.Filled.Code, showChevron = true, onClick = { open(REPO_URL) })
                GroupRow(stringResource(R.string.licence_title), stringResource(R.string.licence_subtitle), icon = Icons.Filled.Gavel, showChevron = true, onClick = { open(LICENCE_URL) })
            }
            Text(
                stringResource(R.string.about_footer, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
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
