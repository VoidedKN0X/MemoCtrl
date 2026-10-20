package com.vknxworks.memoctrl.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.lock.LockVault
import com.vknxworks.memoctrl.data.settings.AppSettings
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.LoadingView
import com.vknxworks.memoctrl.ui.components.PillButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.math.roundToInt

/**
 * App lock state: whether the app is unlocked, and when it locks again. The secrets live in [LockVault].
 * Locking wipes the vault key from memory, so the server login can't be read until the next unlock.
 */
object AppLock {
    var unlocked by mutableStateOf(false)
        private set

    /** Goes up every time the app locks, so the lock screen offers the fingerprint once per lock. */
    var lockCount by mutableIntStateOf(0)
        private set

    /** A PIN screen shown on top of the app: turning the lock on or off, or changing the PIN. */
    var request by mutableStateOf<PinRequest?>(null)

    /** Set by the app: wipes the vault key and closes the session. */
    var onLocked: () -> Unit = {}

    private var authenticating = false
    private var backgroundAt = 0L
    private var expectReturnAt = 0L

    /**
     * The app itself is opening another app (camera, gallery, file picker, share sheet, a file to
     * view). That trip doesn't count as leaving, so you don't have to unlock again when you come back.
     */
    fun expectReturn() {
        expectReturnAt = SystemClock.elapsedRealtime()
    }

    fun onBackground(enabled: Boolean, timeoutMillis: Long) {
        if (authenticating) return
        // Only right after the app opened something itself; leaving later still counts.
        if (expectReturnAt > 0 && SystemClock.elapsedRealtime() - expectReturnAt < 5_000) {
            expectReturnAt = 0L
            backgroundAt = 0L
            return
        }
        backgroundAt = SystemClock.elapsedRealtime()
        // "As soon as you leave" locks right away, so the key is gone while the app is in the background.
        if (enabled && unlocked && timeoutMillis == 0L) lock()
    }

    fun onForeground(enabled: Boolean, timeoutMillis: Long) {
        val since = backgroundAt
        backgroundAt = 0L
        expectReturnAt = 0L
        if (!enabled || !unlocked || since <= 0 || timeoutMillis < 0) return
        if (SystemClock.elapsedRealtime() - since >= timeoutMillis) lock()
    }

    fun lock() {
        unlocked = false
        lockCount++
        request = null
        onLocked()
    }

    fun markUnlocked() {
        backgroundAt = 0L
        unlocked = true
    }

    /** Only class 3 ("strong") biometrics: camera-only face unlock can be fooled by a photo. */
    fun biometricAvailable(context: Context): Boolean = runCatching {
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    /**
     * Shows the fingerprint prompt for [cipher], a Keystore key that only works after a strong match.
     * [onSuccess] gets the unlocked cipher; [onError] gets a message, or null when cancelled.
     */
    fun biometricPrompt(
        activity: Activity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onError: (String?) -> Unit = {},
        onSuccess: (Cipher) -> Unit,
    ) {
        if (authenticating) return
        authenticating = true
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Use PIN", activity.mainExecutor) { _, _ ->
                    authenticating = false
                    onError(null)
                }
                .build()
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticating = false
                        val c = result.cryptoObject?.cipher
                        if (c != null) onSuccess(c) else onError("Fingerprint didn't unlock the key")
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authenticating = false
                        val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                        onError(if (cancelled) null else errString.toString())
                    }
                },
            )
        } catch (e: Exception) {
            authenticating = false
            onError(e.message ?: "Couldn't show the fingerprint prompt")
        }
    }

    /**
     * The phone's own screen lock. Only used once, by people updating from the old app lock, to prove
     * it's them before they choose a PIN.
     */
    fun legacyAuthenticate(activity: Activity, onResult: (String?) -> Unit) {
        if (authenticating) return
        authenticating = true
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle("Unlock MemoCtrl")
                .setSubtitle("Confirm it's you to choose an app PIN")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build()
            prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    markUnlocked()
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

/** What a PIN screen on top of the app is for. */
enum class PinRequest { ENABLE, CHANGE, DISABLE }

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
    val vault = c.vault
    when {
        // Updated from the old app lock (the phone's screen lock): confirm once, then choose a PIN.
        settings.appLock && !vault.enabled -> {
            if (!AppLock.unlocked) LegacyLockedScreen()
            else PinSetupScreen(
                title = "Choose an app PIN",
                intro = "MemoCtrl's app lock now has its own PIN, separate from your phone's. " +
                    "Your server login is encrypted with it while the app is locked.",
                verifyCurrent = false,
                cancellable = false,
                onDone = { pin -> c.enableLock(pin) },
                onCancel = {},
            )
        }
        vault.enabled && !AppLock.unlocked -> PinLockScreen()
        else -> Box(Modifier.fillMaxSize()) {
            content()
            when (AppLock.request) {
                PinRequest.ENABLE -> PinSetupScreen(
                    title = "Choose an app PIN",
                    intro = "At least ${LockVault.PIN_MIN} digits, different from your phone's PIN. " +
                        "If you forget it, the only way back in is to sign out and sign in again.",
                    verifyCurrent = false,
                    cancellable = true,
                    onDone = { pin -> c.enableLock(pin); AppLock.request = null },
                    onCancel = { AppLock.request = null },
                )
                PinRequest.CHANGE -> PinSetupScreen(
                    title = "Change app PIN",
                    intro = "At least ${LockVault.PIN_MIN} digits, different from your phone's PIN.",
                    verifyCurrent = true,
                    cancellable = true,
                    onDone = { pin -> withContext(Dispatchers.Default) { vault.changePin(pin) }; AppLock.request = null },
                    onCancel = { AppLock.request = null },
                )
                PinRequest.DISABLE -> PinSetupScreen(
                    title = "Turn off app lock",
                    intro = "Enter your app PIN to turn the lock off.",
                    verifyCurrent = true,
                    verifyOnly = true,
                    cancellable = true,
                    onDone = { c.disableLock(); AppLock.request = null },
                    onCancel = { AppLock.request = null },
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun LegacyLockedScreen() {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    fun unlock() {
        val activity = context.findActivity() ?: return
        AppLock.legacyAuthenticate(activity) { error = it }
    }
    LaunchedEffect(AppLock.lockCount) { unlock() }
    LockFrame(
        title = "MemoCtrl is locked",
        message = "Unlock with your fingerprint, face or screen lock one last time, then choose an app PIN.",
        error = error,
    ) {
        PillButton("Unlock", onClick = ::unlock, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LockFrame(title: String, message: String, error: String?, actions: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity()
    BackHandler { activity?.moveTaskToBack(true) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LockBadge()
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                actions()
            }
        }
    }
}

@Composable
private fun LockBadge() {
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
}

/** The lock screen: PIN pad, fingerprint, and "Forgot PIN?". */
@Composable
private fun PinLockScreen() {
    val context = LocalContext.current
    val c = context.container
    val vault = c.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }
    var forgetting by remember { mutableStateOf(false) }
    val bioAvailable = remember(AppLock.lockCount) {
        vault.biometricEnabled && AppLock.biometricAvailable(context) && !vault.pinRequired()
    }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    fun askFingerprint() {
        val a = activity ?: return
        val cipher = vault.biometricUnlockCipher() ?: run {
            message = if (!vault.biometricEnabled) "A fingerprint was added or removed. Unlock with your PIN, then turn fingerprint unlock on again." else null
            return
        }
        AppLock.biometricPrompt(a, "Unlock MemoCtrl", "Confirm it's you to see your memos", cipher, onError = { message = it }) { unlocked ->
            if (vault.unlockWithBiometric(unlocked)) c.onUnlocked() else message = "Couldn't unlock with your fingerprint. Use your PIN."
        }
    }
    LaunchedEffect(AppLock.lockCount) { if (bioAvailable) askFingerprint() }

    fun submit() {
        if (checking || lockout > 0 || pin.length < LockVault.PIN_MIN) return
        val entered = pin
        checking = true
        scope.launch {
            // Slow on purpose (it makes guessing slow too), so off the main thread.
            val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
            checking = false
            pin = ""
            when (result) {
                LockVault.PinResult.Ok -> c.onUnlocked()
                LockVault.PinResult.Wrong, LockVault.PinResult.LockedOut -> {
                    lockout = vault.lockoutRemaining()
                    message = "Wrong PIN"
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45))
                }
            }
        }
    }

    val activityForBack = activity
    BackHandler { activityForBack?.moveTaskToBack(true) }
    PinPadScreen(
        title = "MemoCtrl is locked",
        message = when {
            lockout > 0 -> "Too many tries. Try again in ${(lockout + 999) / 1000} s"
            message != null -> message!!
            vault.biometricEnabled && vault.pinRequired() -> "Enter your PIN. It's needed after a restart and every 72 hours."
            else -> "Enter your PIN"
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = checking,
        enabled = lockout == 0L && !checking,
        onDigit = { d ->
            if (pin.length < LockVault.PIN_MAX) {
                message = null
                pin += d
                if (pin.length == vault.pinLength) submit()
            }
        },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::submit,
        showConfirm = pin.length >= LockVault.PIN_MIN && pin.length != vault.pinLength,
        bottom = {
            if (bioAvailable) {
                TextButton(onClick = ::askFingerprint) {
                    Icon(Icons.Outlined.Fingerprint, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Use fingerprint")
                }
            }
            TextButton(onClick = { forgetting = true }) { Text("Forgot PIN?") }
        },
    )
    if (forgetting) {
        ConfirmDialog(
            title = "Forgot your PIN?",
            body = "The PIN can't be recovered, because it encrypts your server login. MemoCtrl will sign out of every " +
                "account and delete the memos, pictures and drafts kept on this phone. Everything on your Memos server stays. " +
                "Changes that haven't synced yet are lost.",
            confirm = "Sign out and reset",
            onDismiss = { forgetting = false },
        ) {
            forgetting = false
            scope.launch { c.forgetEverything() }
        }
    }
}

/**
 * Choosing a PIN (twice), changing it (current, then new twice) or confirming it to turn the lock off.
 * [onDone] runs off the main thread's critical path inside a coroutine.
 */
@Composable
private fun PinSetupScreen(
    title: String,
    intro: String,
    verifyCurrent: Boolean,
    cancellable: Boolean,
    onDone: suspend (String) -> Unit,
    onCancel: () -> Unit,
    verifyOnly: Boolean = false,
) {
    val context = LocalContext.current
    val vault = context.container.vault
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val shake = remember { Animatable(0f) }
    // 0 = current PIN, 1 = new PIN, 2 = repeat new PIN
    var step by remember { mutableIntStateOf(if (verifyCurrent) 0 else 1) }
    var pin by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockout by remember { mutableLongStateOf(vault.lockoutRemaining()) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(500)
            lockout = vault.lockoutRemaining()
        }
    }

    BackHandler { if (cancellable) onCancel() else activity?.moveTaskToBack(true) }

    fun wrong(text: String) {
        message = text
        pin = ""
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(45)) }
    }

    fun finish(newPin: String) {
        busy = true
        scope.launch {
            runCatching { onDone(newPin) }.onFailure { busy = false; wrong("Couldn't save the PIN. Try again.") }
        }
    }

    fun next() {
        if (busy || lockout > 0) return
        when (step) {
            0 -> {
                if (pin.length < LockVault.PIN_MIN) return
                val entered = pin
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { vault.unlockWithPin(entered) }
                    busy = false
                    if (result == LockVault.PinResult.Ok) {
                        pin = ""
                        message = null
                        if (verifyOnly) finish(entered) else step = 1
                    } else {
                        lockout = vault.lockoutRemaining()
                        wrong("Wrong PIN")
                    }
                }
            }
            1 -> {
                if (pin.length < LockVault.PIN_MIN) return
                if (pin.toSet().size == 1 || "0123456789012".contains(pin) || "9876543210987".contains(pin)) {
                    wrong("Too easy to guess. Try a less obvious PIN.")
                    return
                }
                first = pin
                pin = ""
                message = null
                step = 2
            }
            else -> {
                if (pin != first) {
                    first = ""
                    step = 1
                    wrong("The PINs didn't match. Start again.")
                    return
                }
                finish(pin)
            }
        }
    }

    PinPadScreen(
        title = title,
        message = when {
            lockout > 0 -> "Too many tries. Try again in ${(lockout + 999) / 1000} s"
            message != null -> message!!
            step == 0 -> "Enter your current PIN"
            step == 1 -> intro
            else -> "Enter the same PIN again"
        },
        isError = lockout > 0 || message != null,
        pin = pin,
        shake = shake.value,
        checking = busy,
        enabled = !busy && lockout == 0L,
        onDigit = { d -> if (pin.length < LockVault.PIN_MAX) { message = null; pin += d } },
        onBackspace = { pin = pin.dropLast(1) },
        onConfirm = ::next,
        showConfirm = pin.length >= LockVault.PIN_MIN,
        confirmLabel = if (step == 2 || (step == 0 && verifyOnly)) "Done" else "Next",
        bottom = { if (cancellable) TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun PinPadScreen(
    title: String,
    message: String,
    isError: Boolean,
    pin: String,
    shake: Float,
    checking: Boolean,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    showConfirm: Boolean,
    confirmLabel: String = "OK",
    bottom: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockBadge()
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) cs.error else cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp),
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.height(20.dp).offset { IntOffset(shake.roundToInt(), 0) }, contentAlignment = Alignment.Center) {
                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxOf(pin.length, LockVault.PIN_MIN)) { i ->
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < pin.length) cs.primary else cs.surfaceContainerHighest))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { d ->
                            DigitKey(d.toString(), enabled) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigit(d)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackspace, enabled = enabled && pin.isNotEmpty(), modifier = Modifier.size(72.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
                    }
                    DigitKey("0", enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDigit('0')
                    }
                    TextButton(onClick = onConfirm, enabled = enabled && showConfirm, modifier = Modifier.size(72.dp)) { Text(confirmLabel) }
                }
            }
            Spacer(Modifier.height(16.dp))
            bottom()
        }
    }
}

@Composable
private fun DigitKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(72.dp),
    ) { Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium) }
}
