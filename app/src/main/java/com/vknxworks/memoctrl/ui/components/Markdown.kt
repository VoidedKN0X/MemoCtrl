package com.vknxworks.memoctrl.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.memoctrl.container
import com.vknxworks.memoctrl.util.findTagMatches
import com.vknxworks.memoctrl.util.parseMarkdown
import com.vknxworks.memoctrl.util.tagName
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import androidx.compose.ui.res.stringResource
import com.vknxworks.memoctrl.R

/** What tapping things inside a memo does. */
class MarkdownActions(
    /** A task checkbox was tapped: its new state and the character range of "[ ]" in the memo text. */
    val onCheckbox: ((checked: Boolean, start: Int, end: Int) -> Unit)? = null,
    val onTag: ((String) -> Unit)? = null,
)

private data class InlineImage(val url: String, val alt: String)

/**
 * Renders memo text: GitHub-flavoured Markdown with clickable #tags, tappable task checkboxes,
 * links, code, quotes, tables and images. [serverUrl] resolves relative image paths, and decides
 * which pictures count as coming from another website.
 */
@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    serverUrl: String? = null,
    actions: MarkdownActions = MarkdownActions(),
    selectable: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val tree = remember(text) { parseMarkdown(text) }
    val content: @Composable () -> Unit = {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Blocks(tree, text, serverUrl, actions, textStyle)
        }
    }
    if (selectable) SelectionContainer { content() } else content()
}

@Composable
private fun Blocks(node: ASTNode, src: String, serverUrl: String?, actions: MarkdownActions, style: TextStyle) {
    for (child in node.children) Block(child, src, serverUrl, actions, style)
}

@Composable
private fun Block(node: ASTNode, src: String, serverUrl: String?, actions: MarkdownActions, style: TextStyle) {
    val colors = MaterialTheme.colorScheme
    val type = node.type
    when {
        type == MarkdownElementTypes.PARAGRAPH -> Paragraph(node, src, serverUrl, actions, style)

        type == MarkdownElementTypes.ATX_1 || type == MarkdownElementTypes.SETEXT_1 ->
            Heading(node, src, actions, MaterialTheme.typography.titleLarge)
        type == MarkdownElementTypes.ATX_2 || type == MarkdownElementTypes.SETEXT_2 ->
            Heading(node, src, actions, MaterialTheme.typography.titleMedium)
        type == MarkdownElementTypes.ATX_3 || type == MarkdownElementTypes.ATX_4 ||
            type == MarkdownElementTypes.ATX_5 || type == MarkdownElementTypes.ATX_6 ->
            Heading(node, src, actions, MaterialTheme.typography.titleSmall)

        type == MarkdownElementTypes.UNORDERED_LIST || type == MarkdownElementTypes.ORDERED_LIST ->
            ListBlock(node, src, serverUrl, actions, style)

        type == MarkdownElementTypes.BLOCK_QUOTE -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(MaterialTheme.shapes.extraSmall).background(colors.primary.copy(alpha = 0.6f)))
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (child in node.children) {
                    if (child.type != MarkdownTokenTypes.BLOCK_QUOTE) {
                        Block(child, src, serverUrl, actions, style.copy(color = colors.onSurfaceVariant))
                    }
                }
            }
        }

        type == MarkdownElementTypes.CODE_FENCE -> CodeBlock(fenceContent(src.substring(node.startOffset, node.endOffset)))
        type == MarkdownElementTypes.CODE_BLOCK -> CodeBlock(
            src.substring(node.startOffset, node.endOffset).lines().joinToString("\n") { it.removePrefix("    ").removePrefix("\t") },
        )

        type == MarkdownTokenTypes.HORIZONTAL_RULE -> HorizontalDivider(Modifier.padding(vertical = 4.dp), color = colors.outlineVariant)

        type == GFMElementTypes.TABLE -> Table(node, src, actions, style)

        type == MarkdownElementTypes.HTML_BLOCK -> Text(src.substring(node.startOffset, node.endOffset), style = style)

        type == MarkdownElementTypes.LINK_DEFINITION -> Unit

        // Whitespace, line ends and anything unknown between blocks.
        node.children.isEmpty() -> Unit
        else -> Blocks(node, src, serverUrl, actions, style)
    }
}

private fun fenceContent(raw: String): String {
    val lines = raw.lines().toMutableList()
    if (lines.isNotEmpty()) lines.removeAt(0)
    if (lines.isNotEmpty()) {
        val last = lines.last().trim()
        if (last.startsWith("```") || last.startsWith("~~~")) lines.removeAt(lines.lastIndex)
    }
    return lines.joinToString("\n")
}

@Composable
private fun CodeBlock(code: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        Text(code, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun Heading(node: ASTNode, src: String, actions: MarkdownActions, style: TextStyle) {
    val content = node.children.firstOrNull {
        it.type == MarkdownTokenTypes.ATX_CONTENT || it.type == MarkdownTokenTypes.SETEXT_CONTENT
    }
    val builder = rememberInline(src, actions)
    val text = remember(src, node.startOffset) {
        if (content == null) AnnotatedString("") else builder.build(content, trim = true).text
    }
    Text(text, style = style.copy(color = MaterialTheme.colorScheme.onSurface))
}

@Composable
private fun Paragraph(node: ASTNode, src: String, serverUrl: String?, actions: MarkdownActions, style: TextStyle) {
    val builder = rememberInline(src, actions)
    val built = remember(src, node.startOffset, node.endOffset) { builder.build(node, trim = true) }
    if (built.text.isNotEmpty()) Text(built.text, style = style.copy(color = style.color.takeOrElse(MaterialTheme.colorScheme.onSurface)))
    built.images.forEach { MarkdownImage(it, serverUrl) }
}

private fun Color.takeOrElse(other: Color) = if (this == Color.Unspecified) other else this

@Composable
private fun ListBlock(node: ASTNode, src: String, serverUrl: String?, actions: MarkdownActions, style: TextStyle) {
    val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var index = 0
        for (item in node.children) {
            if (item.type != MarkdownElementTypes.LIST_ITEM) continue
            index++
            ListItem(item, src, serverUrl, actions, style, ordered, index)
        }
    }
}

@Composable
private fun ListItem(item: ASTNode, src: String, serverUrl: String?, actions: MarkdownActions, style: TextStyle, ordered: Boolean, index: Int) {
    val checkbox = findCheckbox(item)
    val marker = item.children.firstOrNull { it.type == MarkdownTokenTypes.LIST_NUMBER }
        ?.let { src.substring(it.startOffset, it.endOffset).trim() }
        ?: if (ordered) "$index." else "•"
    Row(verticalAlignment = Alignment.Top) {
        if (checkbox != null) {
            val raw = src.substring(checkbox.startOffset, checkbox.endOffset)
            val checked = raw.contains('x') || raw.contains('X')
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = actions.onCheckbox?.let { cb -> { _: Boolean -> cb(!checked, checkbox.startOffset, checkbox.endOffset) } },
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp).size(20.dp),
                )
            }
        } else {
            Text(
                marker,
                style = style.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.widthIn(min = 18.dp).padding(end = 8.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (child in item.children) {
                when (child.type) {
                    MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER, GFMTokenTypes.CHECK_BOX -> Unit
                    else -> {
                        val done = checkbox != null &&
                            (src.substring(checkbox.startOffset, checkbox.endOffset).let { it.contains('x') || it.contains('X') })
                        Block(
                            child, src, serverUrl, actions,
                            if (done) style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough) else style,
                        )
                    }
                }
            }
        }
    }
}

/** The "[ ]" of a task item: a direct child, or the first thing in its paragraph. */
private fun findCheckbox(item: ASTNode): ASTNode? {
    item.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }?.let { return it }
    val para = item.children.firstOrNull { it.type == MarkdownElementTypes.PARAGRAPH } ?: return null
    return para.children.firstOrNull { it.type == GFMTokenTypes.CHECK_BOX }
}

@Composable
private fun Table(node: ASTNode, src: String, actions: MarkdownActions, style: TextStyle) {
    val builder = rememberInline(src, actions)
    val rows = remember(src, node.startOffset) {
        node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }.map { row ->
            (row.type == GFMElementTypes.HEADER) to row.children.filter { it.type == GFMTokenTypes.CELL }.map { builder.build(it, trim = true).text }
        }
    }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, outline, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
            rows.forEachIndexed { i, (header, cells) ->
                if (i > 0) HorizontalDivider(color = outline)
                Row(Modifier.background(if (header) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)) {
                    cells.forEach { cell ->
                        Text(
                            cell,
                            style = style.copy(fontWeight = if (header) FontWeight.SemiBold else style.fontWeight),
                            modifier = Modifier.widthIn(min = 80.dp, max = 260.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---------- Images ----------

@Composable
private fun MarkdownImage(image: InlineImage, serverUrl: String?) {
    val context = LocalContext.current
    val settings by context.container.settings.settings.collectAsStateWithLifecycle()
    val resolved = remember(image.url, serverUrl) { resolveUrl(image.url, serverUrl) }
    val external = remember(resolved, serverUrl) { isExternal(resolved, serverUrl) }
    if (external && !settings.externalImages) {
        // Not loaded: fetching it would tell another website you're reading this memo.
        val host = resolved.toHttpUrlOrNull()?.host ?: resolved
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable { openLink(context, resolved) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Image, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(R.string.markdown_image_link, image.alt.ifBlank { stringResource(R.string.markdown_image) }, host),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        return
    }
    AsyncImage(
        model = resolved,
        contentDescription = image.alt.ifBlank { null },
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).clip(MaterialTheme.shapes.small),
    )
}

private fun resolveUrl(link: String, serverUrl: String?): String {
    if (Uri.parse(link).scheme != null || serverUrl.isNullOrBlank()) return link
    val base = serverUrl.toHttpUrlOrNull() ?: return link
    return base.resolve(link)?.toString() ?: link
}

private fun isExternal(url: String, serverUrl: String?): Boolean {
    val u = url.toHttpUrlOrNull() ?: return false
    val s = serverUrl?.toHttpUrlOrNull() ?: return true
    return !u.host.equals(s.host, ignoreCase = true)
}

/** Opens a web or mail link in another app; a toast when nothing can open it. */
fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme !in setOf("http", "https", "mailto", "geo", "tel")) {
        Toast.makeText(context, context.getString(R.string.link_unsupported), Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.link_no_app), Toast.LENGTH_SHORT).show()
    }
}

// ---------- Inline text ----------

private class Built(val text: AnnotatedString, val images: List<InlineImage>)

@Composable
private fun rememberInline(src: String, actions: MarkdownActions): InlineBuilder {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    return remember(src, actions, colors.primary) {
        InlineBuilder(
            src = src,
            linkStyle = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
            tagStyle = TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)),
            codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest),
            onTag = actions.onTag,
            openUrl = { openLink(context, it) },
        )
    }
}

/** Turns a paragraph's (or heading's, or cell's) inline Markdown into styled, clickable text. */
private class InlineBuilder(
    private val src: String,
    private val linkStyle: TextLinkStyles,
    private val tagStyle: TextLinkStyles,
    private val codeStyle: SpanStyle,
    private val onTag: ((String) -> Unit)?,
    private val openUrl: (String) -> Unit,
) {
    fun build(node: ASTNode, trim: Boolean): Built {
        val images = ArrayList<InlineImage>()
        val text = buildAnnotatedString {
            val pending = StringBuilder()
            fun flush() {
                if (pending.isEmpty()) return
                appendWithTags(this, unescape(pending.toString()))
                pending.clear()
            }
            fun walk(n: ASTNode) {
                val t = n.type
                when {
                    t == MarkdownElementTypes.EMPH -> { flush(); withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { n.children.forEach(::walk); flush() } }
                    t == MarkdownElementTypes.STRONG -> { flush(); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { n.children.forEach(::walk); flush() } }
                    t == GFMElementTypes.STRIKETHROUGH -> { flush(); withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { n.children.forEach(::walk); flush() } }
                    t == MarkdownTokenTypes.EMPH || t == GFMTokenTypes.TILDE -> Unit
                    t == MarkdownElementTypes.CODE_SPAN -> {
                        flush()
                        val raw = src.substring(n.startOffset, n.endOffset).trim('`')
                        withStyle(codeStyle) { append(raw) }
                    }
                    t == MarkdownElementTypes.INLINE_LINK -> {
                        flush()
                        val dest = n.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                            ?.let { src.substring(it.startOffset, it.endOffset).trim('<', '>') }
                        val label = n.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                        if (dest == null) {
                            label?.let { innerOfBrackets(it).forEach(::walk) }
                            flush()
                        } else {
                            withLink(link(dest)) {
                                if (label != null) innerOfBrackets(label).forEach(::walk) else append(dest)
                                flush()
                            }
                        }
                    }
                    t == MarkdownElementTypes.FULL_REFERENCE_LINK || t == MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                        flush()
                        val label = n.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT || it.type == MarkdownElementTypes.LINK_LABEL }
                        if (label != null) innerOfBrackets(label).forEach(::walk) else append(src.substring(n.startOffset, n.endOffset))
                        flush()
                    }
                    t == MarkdownElementTypes.AUTOLINK -> {
                        flush()
                        val url = src.substring(n.startOffset, n.endOffset).trim('<', '>')
                        withLink(link(url)) { append(url) }
                    }
                    t == GFMTokenTypes.GFM_AUTOLINK || t == MarkdownTokenTypes.AUTOLINK -> {
                        flush()
                        val url = src.substring(n.startOffset, n.endOffset)
                        val target = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
                        withLink(link(target)) { append(url) }
                    }
                    t == MarkdownTokenTypes.EMAIL_AUTOLINK -> {
                        flush()
                        val mail = src.substring(n.startOffset, n.endOffset).trim('<', '>')
                        withLink(link("mailto:$mail")) { append(mail) }
                    }
                    t == MarkdownElementTypes.IMAGE -> {
                        flush()
                        val inner = n.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
                        val dest = inner?.children?.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                            ?.let { src.substring(it.startOffset, it.endOffset).trim('<', '>') }
                        val alt = inner?.children?.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                            ?.let { src.substring(it.startOffset, it.endOffset).trim('[', ']') }
                            .orEmpty()
                        if (dest != null) images += InlineImage(dest, alt) else append(src.substring(n.startOffset, n.endOffset))
                    }
                    t == GFMTokenTypes.CHECK_BOX || t == MarkdownTokenTypes.BLOCK_QUOTE -> Unit
                    t == MarkdownTokenTypes.HARD_LINE_BREAK -> { flush(); append('\n') }
                    // Memos keeps single line breaks inside a paragraph, unlike plain Markdown.
                    t == MarkdownTokenTypes.EOL -> pending.append('\n')
                    n.children.isEmpty() -> pending.append(src, n.startOffset, n.endOffset)
                    else -> n.children.forEach(::walk)
                }
            }
            if (node.children.isEmpty()) pending.append(src, node.startOffset, node.endOffset) else node.children.forEach(::walk)
            flush()
        }
        val trimmed = if (trim) text.trimmed() else text
        return Built(trimmed, images)
    }

    /** The children between "[" and "]" of a link's text. */
    private fun innerOfBrackets(label: ASTNode): List<ASTNode> =
        label.children.filterNot { it.type == MarkdownTokenTypes.LBRACKET || it.type == MarkdownTokenTypes.RBRACKET }

    private fun link(url: String): LinkAnnotation = LinkAnnotation.Clickable(
        tag = "url",
        styles = linkStyle,
        linkInteractionListener = LinkInteractionListener { openUrl(url) },
    )

    private fun appendWithTags(b: AnnotatedString.Builder, text: String) {
        var cursor = 0
        for (m in findTagMatches(text)) {
            if (m.range.first > cursor) b.append(text.substring(cursor, m.range.first))
            val tag = tagName(m)
            val listener = onTag
            if (listener != null) {
                b.withLink(LinkAnnotation.Clickable("tag", tagStyle, LinkInteractionListener { listener(tag) })) { append(m.value) }
            } else {
                b.withStyle(tagStyle.style ?: SpanStyle()) { append(m.value) }
            }
            cursor = m.range.last + 1
        }
        if (cursor < text.length) b.append(text.substring(cursor))
    }
}

private val ESCAPABLE = Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~])""")

private fun unescape(s: String): String = s.replace(ESCAPABLE, "$1")

private fun AnnotatedString.trimmed(): AnnotatedString {
    var start = 0
    var end = length
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start == 0 && end == length) this else subSequence(start, end)
}
