package com.vknxworks.memoctrl.ui.auth

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.auth.Credentials
import com.vknxworks.memoctrl.data.auth.HttpFallbackNeeded
import com.vknxworks.memoctrl.data.auth.NewerServerException
import com.vknxworks.memoctrl.data.net.ClientCertificate
import com.vknxworks.memoctrl.ui.components.ConfirmDialog
import com.vknxworks.memoctrl.ui.components.PillButton
import com.vknxworks.memoctrl.ui.components.friendlyMessage
import com.vknxworks.memoctrl.ui.lock.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** Whether [address] points at the home network (Android 17 asks the user before apps can reach it). */
private suspend fun isLocalNetwork(address: String): Boolean = withContext(Dispatchers.IO) {
    val raw = address.trim()
    val host = (if (raw.contains("://")) raw else "https://$raw").toHttpUrlOrNull()?.host ?: return@withContext false
    if (host.trimEnd('.').endsWith(".local", ignoreCase = true)) return@withContext true
    runCatching {
        InetAddress.getAllByName(host).any { a ->
            val b = a.address
            !a.isLoopbackAddress && (a.isSiteLocalAddress || a.isLinkLocalAddress || (b.size == 16 && (b[0].toInt() and 0xfe) == 0xfc))
        }
    }.getOrDefault(false)
}

@Composable
fun SignInScreen(onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    // The token can be shown in clear text here: keep this screen out of the recent-apps preview.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.setRecentsScreenshotEnabled(false)
        onDispose { activity?.setRecentsScreenshotEnabled(!c.settings.settings.value.appLock) }
    }
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    // Password sign-in by default; an access token for SSO accounts or if you'd rather make one yourself.
    var usePassword by rememberSaveable { mutableStateOf(true) }
    var username by rememberSaveable { mutableStateOf("") }
    // Kept in memory only for this screen; never saved, never written to the phone.
    var password by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offerHttp by remember { mutableStateOf<String?>(null) }
    var newer by remember { mutableStateOf<NewerServerException?>(null) }
    var certificate by remember { mutableStateOf(ClientCertificate.selectedAlias(context)) }
    var pendingTarget by remember { mutableStateOf<String?>(null) }
    val canSubmit = address.isNotBlank() && !busy &&
        (if (usePassword) username.isNotBlank() && password.isNotEmpty() else token.isNotBlank())

    fun attempt(target: String, allowNewer: Boolean = false) {
        busy = true
        error = null
        scope.launch {
            try {
                val credentials = if (usePassword) Credentials.Password(username, password) else Credentials.Token(token)
                c.sessions.signIn(target, credentials, label, allowNewer)
                password = ""
                token = ""
                onDone()
            } catch (e: HttpFallbackNeeded) {
                offerHttp = e.httpUrl
            } catch (e: NewerServerException) {
                pendingTarget = target
                newer = e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.friendlyMessage()
            } finally {
                busy = false
            }
        }
    }

    val localPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingTarget
        pendingTarget = null
        if (granted && target != null) attempt(target)
        else if (!granted) error = "MemoCtrl needs access to devices on your network to reach a server at home. Allow it in the app's settings and try again."
    }

    fun submit() {
        if (!canSubmit) return
        val target = address
        scope.launch {
            val needsPermission = Build.VERSION.SDK_INT >= 37 &&
                ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED &&
                isLocalNetwork(target)
            if (needsPermission) {
                pendingTarget = target
                localPermission.launch(ACCESS_LOCAL_NETWORK)
            } else {
                attempt(target)
            }
        }
    }

    offerHttp?.let { httpUrl ->
        ConfirmDialog(
            "Use an unencrypted connection?",
            "MemoCtrl couldn't connect to this server over HTTPS. It can try plain HTTP instead, " +
                "but then your password or access token and your memos are sent unencrypted and anyone on the network can read them. " +
                "Only do this on a network you trust.",
            "Use HTTP",
            onDismiss = {
                offerHttp = null
                error = "Couldn't connect securely (HTTPS)"
            },
        ) {
            offerHttp = null
            address = httpUrl
            attempt(httpUrl)
        }
    }

    newer?.let { e ->
        ConfirmDialog(
            "Newer Memos version",
            e.message ?: "This server runs a newer Memos version.",
            "Sign in anyway",
            onDismiss = { newer = null; pendingTarget = null },
        ) {
            newer = null
            val target = pendingTarget ?: address
            pendingTarget = null
            attempt(target, allowNewer = true)
        }
    }

    BackHandler(enabled = onCancel != null) { onCancel?.invoke() }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.height(48.dp)) {
                if (onCancel != null) IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
            Text(if (onCancel == null) "MemoCtrl" else "Add a server", style = MaterialTheme.typography.displaySmall)
            Text(
                "Sign in to your Memos server with the address you'd open in a browser.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Field(
                address, { address = it },
                label = "Server address",
                placeholder = "memos.example.com",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                enabled = !busy,
            )
            if (address.trim().startsWith("http://", ignoreCase = true)) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.LockOpen, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Not encrypted (HTTP). Your password, access token and memos are sent in the clear. Fine on your home network, risky anywhere else.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = usePassword, onClick = { usePassword = true }, shape = SegmentedButtonDefaults.itemShape(0, 2), enabled = !busy) {
                    Text("Password")
                }
                SegmentedButton(selected = !usePassword, onClick = { usePassword = false }, shape = SegmentedButtonDefaults.itemShape(1, 2), enabled = !busy) {
                    Text("Access token")
                }
            }
            if (usePassword) {
                Field(
                    username, { username = it },
                    label = "Username",
                    keyboard = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    enabled = !busy,
                )
                Field(
                    password, { password = it },
                    label = "Password",
                    visual = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    enabled = !busy,
                    trailing = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showToken) "Hide password" else "Show password")
                        }
                    },
                )
                Text(
                    "Your password is only used to sign in. MemoCtrl then gets its own access token from your server and never keeps the password. You'll find the token under Settings > My account > Access tokens in Memos, and it's revoked when you sign out here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Field(
                    token, { token = it },
                    label = "Access token",
                    visual = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    enabled = !busy,
                    trailing = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showToken) "Hide token" else "Show token")
                        }
                    },
                )
                Text(
                    "For accounts that sign in with SSO, or if you'd rather make the token yourself: Memos > Settings > My account > Access tokens. Pick one that never expires.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Field(
                label, { label = it },
                label = "Account name (optional)",
                placeholder = "Home",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                actions = KeyboardActions(onDone = { submit() }),
                enabled = !busy,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = !busy) {
                        val activity = context.findActivity() ?: return@clickable
                        ClientCertificate.choose(activity) { ok -> if (ok) certificate = ClientCertificate.selectedAlias(context) }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Badge, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Client certificate", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        certificate?.let { "Using \"$it\"" } ?: "Only if your server's proxy asks for one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (busy) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            } else {
                PillButton("Sign in", onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth())
            }
            Text(
                "The access token is stored encrypted on this phone and is only sent to your server. MemoCtrl works with Memos 0.21 and 0.27 to 0.31.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = keyboard,
        keyboardActions = actions,
        visualTransformation = visual,
        trailingIcon = trailing,
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
