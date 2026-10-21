package com.vknxworks.memoctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.memoctrl.data.api.AccessTokenInvalidException
import com.vknxworks.memoctrl.data.api.ApiException
import java.io.IOException
import java.net.UnknownHostException
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R
import com.vknxworks.memoctrl.str

/** A message for people, not developers, for anything that went wrong talking to the server. */
fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> message ?: str(R.string.error_server)
    is AccessTokenInvalidException -> message ?: str(R.string.error_token_invalid)
    is UnknownHostException -> str(R.string.error_unknown_host)
    is java.net.ConnectException -> str(R.string.error_refused)
    is java.net.SocketTimeoutException -> str(R.string.error_timeout)
    is javax.net.ssl.SSLException -> str(R.string.error_tls, message ?: str(R.string.error_tls_certificate))
    is IOException -> message ?: str(R.string.error_network)
    else -> message ?: str(R.string.error_generic)
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val retry = stringResource(R.string.retry)
    MessageView(Icons.Outlined.ErrorOutline, stringResource(R.string.error_load_title), message, modifier, actionLabel = onRetry?.let { retry }, onAction = onRetry)
}

@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                FilledTonalButton(onClick = onAction, shape = CircleShape) { Text(actionLabel) }
            }
        }
    }
}
