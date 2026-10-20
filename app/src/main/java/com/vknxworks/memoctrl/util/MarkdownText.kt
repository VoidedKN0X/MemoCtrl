package com.vknxworks.memoctrl.util

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private val flavour = GFMFlavourDescriptor()

/** Parses memo text as GitHub-flavoured Markdown, the way Memos itself reads it. */
fun parseMarkdown(text: String): ASTNode = MarkdownParser(flavour).buildMarkdownTreeFromString(text)

// ---------- Tags ----------

private val tagPattern = Regex("#([^\\s#]+)")

/** Places where "#word" is not a tag: code and links. */
private val tagExcludedTypes: Set<IElementType> = setOf(
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_SPAN,
    MarkdownTokenTypes.CODE_LINE,
    MarkdownTokenTypes.CODE_FENCE_CONTENT,
    MarkdownElementTypes.LINK_DEFINITION,
    MarkdownElementTypes.INLINE_LINK,
    MarkdownElementTypes.FULL_REFERENCE_LINK,
    MarkdownElementTypes.SHORT_REFERENCE_LINK,
    MarkdownElementTypes.LINK_DESTINATION,
    MarkdownElementTypes.LINK_TEXT,
    MarkdownElementTypes.LINK_LABEL,
    MarkdownElementTypes.LINK_TITLE,
    MarkdownElementTypes.AUTOLINK,
    MarkdownElementTypes.IMAGE,
    MarkdownTokenTypes.URL,
    MarkdownTokenTypes.AUTOLINK,
    MarkdownTokenTypes.EMAIL_AUTOLINK,
    GFMTokenTypes.GFM_AUTOLINK,
)

fun findTagMatches(text: CharSequence): Sequence<MatchResult> = tagPattern.findAll(text)

fun tagName(match: MatchResult): String = match.groupValues[1]

fun isTagAllowedAt(node: ASTNode): Boolean {
    var current: ASTNode? = node
    while (current != null) {
        if (current.type in tagExcludedTypes) return false
        current = current.parent
    }
    return true
}

fun ASTNode.findNodeAt(position: Int): ASTNode? {
    if (position !in startOffset until endOffset) return null
    for (child in children) child.findNodeAt(position)?.let { return it }
    return this
}

/** The #tags in a memo, as Memos counts them: not inside code or links. */
fun extractTags(text: String): Set<String> {
    if ('#' !in text) return emptySet()
    val tree = parseMarkdown(text)
    val tags = LinkedHashSet<String>()
    findTagMatches(text).forEach { m ->
        val node = tree.findNodeAt(m.range.first)
        if (node != null && isTagAllowedAt(node)) tags += tagName(m)
    }
    return tags
}

/** Whether a memo has [tag] or one of its sub-tags ("work" matches "#work/meetings"). Case-sensitive. */
fun hasTag(text: String, tag: String): Boolean = extractTags(text).any { it == tag || it.startsWith("$tag/") }

fun matchesSearch(content: String, query: String?): Boolean =
    query.isNullOrEmpty() || content.contains(query, ignoreCase = true)

// ---------- Previews ----------

private const val PREVIEW_UNBREAKABLE_COST = 100
private const val PREVIEW_MAX_WIDTH = 500

private enum class AppendKind { NONE, TEXT, UNBREAKABLE }

/**
 * A preview of [markdownText] limited to [maxWidth] display-width units, and whether anything was
 * left out. Full-width (CJK) characters count twice, so previews are about the same number of lines
 * in every language. Code blocks, images, tables and headings are kept whole or left out.
 */
fun extractPreview(markdownText: String, maxWidth: Int = PREVIEW_MAX_WIDTH): Pair<String, Boolean> {
    if (previewWidth(markdownText) <= maxWidth) return markdownText to false
    val root = parseMarkdown(markdownText)
    val result = StringBuilder()
    var remaining = maxWidth
    var truncated = false
    var last = AppendKind.NONE

    fun appendText(node: ASTNode): Boolean {
        if (remaining <= 0) {
            truncated = true
            return false
        }
        val content = markdownText.substring(node.startOffset, node.endOffset)
        if (content.isEmpty()) return true
        val width = previewWidth(content)
        if (width <= remaining) {
            result.append(content)
            remaining -= width
            last = AppendKind.TEXT
            return true
        }
        result.append(previewPrefix(content, remaining))
        remaining = 0
        truncated = true
        last = AppendKind.TEXT
        return false
    }

    fun appendWhole(node: ASTNode): Boolean {
        if (remaining < PREVIEW_UNBREAKABLE_COST) {
            truncated = true
            return false
        }
        result.append(markdownText.substring(node.startOffset, node.endOffset))
        remaining -= PREVIEW_UNBREAKABLE_COST
        last = AppendKind.UNBREAKABLE
        return true
    }

    lateinit var walkInline: (ASTNode) -> Boolean
    lateinit var walkBlock: (ASTNode) -> Boolean

    walkInline = { node ->
        if (isUnbreakable(node)) {
            appendWhole(node)
        } else if (node.children.isEmpty()) {
            appendText(node)
        } else {
            var ok = true
            for (child in node.children) {
                val r = if (isBreakableBlock(child.type)) walkBlock(child) else walkInline(child)
                if (!r) { ok = false; break }
            }
            ok
        }
    }

    walkBlock = { node ->
        var ok = true
        val paragraph = node.type == MarkdownElementTypes.PARAGRAPH
        for (child in node.children) {
            val r = when {
                paragraph -> walkInline(child)
                isBreakableBlock(child.type) -> walkBlock(child)
                isWhitespace(child) || child.children.isEmpty() -> appendText(child)
                else -> appendWhole(child)
            }
            if (!r) { ok = false; break }
        }
        ok
    }

    for (child in root.children) {
        val r = when {
            isBreakableBlock(child.type) -> walkBlock(child)
            isWhitespace(child) -> appendText(child)
            else -> appendWhole(child)
        }
        if (!r) break
    }

    if (truncated && last == AppendKind.TEXT) {
        val preview = result.toString().trimEnd()
        return (if (preview.endsWith("…")) preview else "$preview…") to true
    }
    return result.toString() to truncated
}

internal fun previewWidth(text: String): Int {
    var width = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        width += if (isFullWidth(cp)) 2 else 1
        i += Character.charCount(cp)
    }
    return width
}

internal fun previewPrefix(text: String, maxWidth: Int): String {
    var width = 0
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val w = if (isFullWidth(cp)) 2 else 1
        if (width + w > maxWidth) break
        width += w
        i += Character.charCount(cp)
    }
    return text.substring(0, i)
}

private val FULL_WIDTH = listOf(
    0x1100..0x115F, 0x2E80..0x303E, 0x3041..0x33FF, 0x3400..0x4DBF, 0x4E00..0x9FFF, 0xA000..0xA4CF,
    0xAC00..0xD7A3, 0xF900..0xFAFF, 0xFE10..0xFE19, 0xFE30..0xFE6F, 0xFF00..0xFF60, 0xFFE0..0xFFE6,
    0x20000..0x3FFFD,
)

private fun isFullWidth(cp: Int): Boolean = FULL_WIDTH.any { cp in it }

private fun isBreakableBlock(type: IElementType): Boolean =
    type == MarkdownElementTypes.MARKDOWN_FILE ||
        type == MarkdownElementTypes.PARAGRAPH ||
        type == MarkdownElementTypes.LIST_ITEM ||
        type == MarkdownElementTypes.BLOCK_QUOTE ||
        type == MarkdownElementTypes.ORDERED_LIST ||
        type == MarkdownElementTypes.UNORDERED_LIST

private fun isUnbreakable(node: ASTNode): Boolean =
    node.type == MarkdownElementTypes.IMAGE ||
        node.type == MarkdownElementTypes.CODE_BLOCK ||
        node.type == MarkdownElementTypes.CODE_FENCE ||
        node.type == GFMElementTypes.TABLE ||
        node.type == MarkdownElementTypes.ATX_1 ||
        node.type == MarkdownElementTypes.ATX_2 ||
        node.type == MarkdownElementTypes.ATX_3 ||
        node.type == MarkdownElementTypes.ATX_4 ||
        node.type == MarkdownElementTypes.ATX_5 ||
        node.type == MarkdownElementTypes.ATX_6 ||
        node.type == MarkdownElementTypes.SETEXT_1 ||
        node.type == MarkdownElementTypes.SETEXT_2 ||
        node.type == MarkdownTokenTypes.HORIZONTAL_RULE ||
        node.type == MarkdownElementTypes.LINK_DEFINITION ||
        node.type.toString().contains("HTML")

private fun isWhitespace(node: ASTNode): Boolean =
    node.type == MarkdownTokenTypes.EOL || node.type == MarkdownTokenTypes.WHITE_SPACE
