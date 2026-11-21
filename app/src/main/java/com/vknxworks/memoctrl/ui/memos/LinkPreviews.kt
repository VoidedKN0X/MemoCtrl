package com.vknxworks.memoctrl.ui.memos

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.data.api.MemosRemote
import com.vknxworks.memoctrl.data.model.LinkPreview
import com.vknxworks.memoctrl.ui.components.openLink
import com.vknxworks.memoctrl.ui.theme.CtrlColors
import com.vknxworks.memoctrl.ui.theme.CtrlShapes
import com.vknxworks.memoctrl.ui.theme.CtrlType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

private val IMAGE_LINK = Regex("""!\[[^\]]*]\([^)]*\)""")
private val CODE = Regex("""```[\s\S]*?```|`[^`\n]*`""")
private val WEB_LINK = Regex("""https?://[^\s<>()\[\]"'`]+""", RegexOption.IGNORE_CASE)

/**
 * The web links written in a memo, in order and without repeats: not pictures, not code, and not links
 * to [serverUrl] itself (other memos and attachments). At most [max].
 */
fun linksIn(content: String, serverUrl: String?, max: Int = 3): List<String> {
    val server = serverUrl?.toHttpUrlOrNull()
    val text = content.replace(CODE, " ").replace(IMAGE_LINK, " ")
    val out = LinkedHashSet<String>()
    for (m in WEB_LINK.findAll(text)) {
        val link = m.value.trimEnd('.', ',', ';', ':', '!', '?', '*', '_', '~')
        val url = link.toHttpUrlOrNull() ?: continue
        if (server != null && url.host.equals(server.host, ignoreCase = true) && url.port == server.port) continue
        out += link
        if (out.size >= max) break
    }
    return out.toList()
}

/** What the server answered for a link since the app started; a missing preview is remembered as well. */
private val seen = ConcurrentHashMap<String, List<LinkPreview>>()

/**
 * Cards with the title and description of the first few links in a memo. The Memos server looks the
 * links up (Memos 0.29 and newer); the phone itself never contacts those websites. A link's picture is
 * on that website, so it only shows when "Load pictures from other websites" is on.
 */
@Composable
fun LinkPreviews(content: String, remote: MemosRemote) {
    val context = LocalContext.current
    val settings by context.container.settings.settings.collectAsStateWithLifecycle()
    if (!settings.linkPreviews || !remote.supportsSocial) return
    val links = remember(content, remote.host) { linksIn(content, remote.host) }
    if (links.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link ->
            val key = remote.host + "\n" + link
            val preview by produceState(seen[key]?.firstOrNull(), key) {
                if (!seen.containsKey(key)) {
                    val found = runCatching { remote.linkPreview(link) }
                    // A failed lookup (offline) isn't remembered; "nothing to show" is.
                    if (found.isSuccess) seen[key] = listOfNotNull(found.getOrNull())
                    value = found.getOrNull()
                }
            }
            val p = preview
            if (p != null) {
            Column(
                Modifier.fillMaxWidth().clip(CtrlShapes.Control).border(1.dp, CtrlColors.Outline, CtrlShapes.Control)
                    .clickable { openLink(context, link) },
            ) {
                val image = p.image.takeIf { settings.externalImages && it.toHttpUrlOrNull() != null }
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                    )
                }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (p.title.isNotEmpty()) {
                        Text(p.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (p.description.isNotEmpty()) {
                        Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Text(link.toHttpUrlOrNull()?.host ?: link, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            }
        }
    }
}
