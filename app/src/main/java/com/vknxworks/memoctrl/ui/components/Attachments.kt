package com.vknxworks.memoctrl.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.vknxworks.memoctrl.AppContainer
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.api.await
import com.vknxworks.memoctrl.data.files.FileStorage
import com.vknxworks.memoctrl.data.model.MemoLocation
import com.vknxworks.memoctrl.data.model.ResourceEntity
import com.vknxworks.memoctrl.ui.lock.AppLock
import com.vknxworks.memoctrl.ui.media.ImageViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.math.ceil

/** The pictures of a memo in a grid of up to three across; tapping one opens the viewer. */
@Composable
fun ImageGrid(images: List<ResourceEntity>, onRemove: ((ResourceEntity) -> Unit)? = null) {
    if (images.isEmpty()) return
    var viewing by remember { mutableIntStateOf(-1) }
    val cols = 3
    val rows = ceil(images.size / cols.toFloat()).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until cols) {
                    val i = r * cols + col
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (i < images.size) {
                            AsyncImage(
                                model = images[i].displayUri,
                                contentDescription = images[i].filename,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { viewing = i },
                            )
                            if (onRemove != null) {
                                IconButton(
                                    onClick = { onRemove(images[i]) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                        .clip(MaterialTheme.shapes.extraLarge)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                                ) {
                                    Icon(Icons.Outlined.Close, "Remove ${images[i].filename}", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (viewing >= 0) ImageViewer(images, viewing, onDismiss = { viewing = -1 })
}

/** Files that aren't pictures: a chip each, tapping opens the file in another app. */
@Composable
fun AttachmentChips(files: List<ResourceEntity>, onRemove: ((ResourceEntity) -> Unit)? = null) {
    if (files.isEmpty()) return
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEach { f ->
            Row(
                Modifier
                    .widthIn(max = 280.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(enabled = opening == null) {
                        opening = f.identifier
                        scope.launch {
                            openAttachment(context, c, f)
                            opening = null
                        }
                    }
                    .padding(start = 12.dp, end = if (onRemove != null) 4.dp else 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (opening == f.identifier) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Attachment, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(f.filename.ifBlank { "Attachment" }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(f) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Close, "Remove ${f.filename}", Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Where a memo was written. Tapping opens it in a map app (or OpenStreetMap in the browser). */
@Composable
fun LocationRow(location: MemoLocation, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    Row(
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = onClick != null || location.hasValidCoordinates) {
                if (onClick != null) onClick() else openLocation(context, location)
            }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocationOn, "Location", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(6.dp))
        Text(location.displayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

fun openLocation(context: Context, location: MemoLocation) {
    val coordinates = String.format(Locale.US, "%f,%f", location.latitude, location.longitude)
    val geo = "geo:$coordinates?q=" + Uri.encode("$coordinates (${location.displayText})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openLink(
            context,
            String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%f&mlon=%f#map=16/%f/%f", location.latitude, location.longitude, location.latitude, location.longitude),
        )
    }
}

/** A file:// address to the file on the phone, when it's still there. */
fun localFileOf(uri: String?): File? {
    val u = uri?.toUri() ?: return null
    if (u.scheme != "file") return null
    return u.path?.let(::File)?.takeIf { it.exists() }
}

/**
 * Gets an attachment onto the phone: its kept copy, or a download from the server (which is then
 * kept, so it opens offline next time). Null when that failed.
 */
suspend fun ensureLocalFile(context: Context, c: AppContainer, resource: ResourceEntity): File? {
    localFileOf(resource.localUri)?.let { return it }
    localFileOf(resource.uri)?.let { return it }
    if (!resource.uri.startsWith("http://") && !resource.uri.startsWith("https://")) return null
    val downloaded = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "open").also { it.mkdirs() }
        val target = File(dir, "${resource.identifier}_${FileStorage.safeName(resource.filename.ifBlank { "attachment" })}")
        c.http.newCall(Request.Builder().url(resource.uri).build()).await().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        target
    } ?: return null
    val kept = runCatching { c.repo.cacheResourceFile(resource.identifier, downloaded) }.getOrNull()
    val keptFile = localFileOf(kept?.localUri)
    if (keptFile != null) {
        downloaded.delete()
        return keptFile
    }
    return downloaded
}

private fun mimeOf(resource: ResourceEntity, file: File): String =
    resource.mimeType?.takeIf { it.isNotBlank() }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: "*/*"

/** Hands a file to the app the user picks, read-only and only that one file. */
fun fileUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + ".files", file)

suspend fun openAttachment(context: Context, c: AppContainer, resource: ResourceEntity) {
    val file = try {
        ensureLocalFile(context, c, resource)
    } catch (e: Exception) {
        null
    }
    if (file == null) {
        Toast.makeText(context, "Couldn't open this attachment", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = fileUri(context, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(resource, file))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, resource.filename.ifBlank { "attachment" }, uri)
    }
    try {
        AppLock.expectReturn()
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Shares an attachment's file with another app. */
suspend fun shareAttachment(context: Context, c: AppContainer, resource: ResourceEntity) {
    val file = runCatching { ensureLocalFile(context, c, resource) }.getOrNull()
    if (file == null) {
        Toast.makeText(context, "Couldn't get this file", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = fileUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(resource, file)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, resource.filename.ifBlank { "attachment" }, uri)
    }
    AppLock.expectReturn()
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
