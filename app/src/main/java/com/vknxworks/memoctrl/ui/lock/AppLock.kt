package com.vknxworks.memoctrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.settings.AppSettings
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.PillButton

/**
 * App lock: asks for a fingerprint, face or the screen lock when the app opens, and again after it
 * spent a minute or more in the background.
 */
object AppLock {
    private const val TIMEOUT_MS = 60_000L
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    var unlocked by mutableStateOf(false)
        private set

    /** Goes up every time the app locks, so the gate asks once per lock and not on every recomposition. */
    var lockCount by mutableIntStateOf(0)
        private set

    private var authenticating = false
    private var backgroundAt = 0L

    fun onBackground() {
        if (!authenticating) backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(enabled: Boolean) {
        val since = backgroundAt
        backgroundAt = 0L
        if (enabled && unlocked && since > 0 && SystemClock.elapsedRealtime() - since >= TIMEOUT_MS) lock()
    }

    fun lock() {
        unlocked = false
        lockCount++
    }

    fun canAuthenticate(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /** Shows the system unlock prompt. [onResult] gets null on success, or why it didn't work. */
    fun authenticate(activity: Activity, title: String = "Unlock MemoCtrl", onResult: (String?) -> Unit) {
        if (authenticating) return
        authenticating = true
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle("Confirm it's you to see your memos")
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    backgroundAt = 0L
                    unlocked = true
                    onResult(null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    onResult(errString.toString())
                }
            })
        } catch (e: Exception) {
            authenticating = false
            onResult(e.message ?: "Couldn't show the unlock prompt")
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Shows [content] only while the app is unlocked (or the lock is off). */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val stored by c.settings.stored.collectAsState(initial = null)
    val settings: AppSettings = stored ?: run {
        LoadingView()
        return
    }
    if (!settings.appLock || AppLock.unlocked) {
        content()
        return
    }
    LockedScreen()
}

@Composable
private fun LockedScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val available = remember { AppLock.canAuthenticate(context) }
    fun unlock() {
        val activity = context.findActivity() ?: return
        AppLock.authenticate(activity) { error = it }
    }
    LaunchedEffect(AppLock.lockCount) { if (available) unlock() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("MemoCtrl is locked", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                if (available) "Unlock with your fingerprint, face or screen lock." else
                    "This phone has no fingerprint, face or screen lock set up anymore. Set one up to unlock MemoCtrl.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            if (available) {
                PillButton("Unlock", onClick = ::unlock, modifier = Modifier.fillMaxWidth())
            } else {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                }) { Text("Open security settings") }
            }
        }
    }
}
