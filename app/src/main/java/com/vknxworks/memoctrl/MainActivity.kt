package com.vknxworks.memoctrl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.memoctrl.data.settings.ThemeChoice
import com.vknxworks.memoctrl.ui.lock.AppLockGate
import com.vknxworks.memoctrl.ui.nav.AppRoot
import com.vknxworks.memoctrl.ui.theme.MemoCtrlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = container
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // With the app lock on, the recent-apps screen shows a blank card instead of your memos.
            DisposableEffect(settings.appLock) {
                setRecentsScreenshotEnabled(!settings.appLock)
                onDispose { }
            }
            MemoCtrlTheme(settings.theme, settings.dynamicColor) {
                AppLockGate { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Turns a share, shortcut or widget tap into something the main screen opens. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val action = when (intent.action) {
            ACTION_NEW_MEMO -> PendingAction.NewMemo
            ACTION_VIEW_MEMO -> intent.getStringExtra(EXTRA_MEMO_ID)?.let { PendingAction.ViewMemo(it) }
            ACTION_EDIT_MEMO -> intent.getStringExtra(EXTRA_MEMO_ID)?.let { PendingAction.EditMemo(it) }
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() && intent.type == "text/plain" },
                    intent.getStringExtra(Intent.EXTRA_TEXT),
                ).joinToString("\n")
                val image = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.takeIf { intent.type?.startsWith("image/") == true }
                PendingAction.Share(text, listOfNotNull(image))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val images = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                PendingAction.Share(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), images.filterNotNull())
            }
            else -> null
        }
        if (action != null) container.pendingAction.value = action
    }

    companion object {
        const val ACTION_NEW_MEMO = "com.vknxworks.memoctrl.NEW_MEMO"
        const val ACTION_VIEW_MEMO = "com.vknxworks.memoctrl.VIEW_MEMO"
        const val ACTION_EDIT_MEMO = "com.vknxworks.memoctrl.EDIT_MEMO"
        const val EXTRA_MEMO_ID = "memo_id"
    }
}
