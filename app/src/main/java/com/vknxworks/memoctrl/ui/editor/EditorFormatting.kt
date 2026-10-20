package com.vknxworks.memoctrl.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Line starts that make a list item, longest first so "- [ ] " wins over "- ". */
val LIST_PREFIXES = listOf("- [ ] ", "- [x] ", "- [X] ", "* ", "- ")

enum class MarkdownFormat(val label: String, val prefix: String, val suffix: String = "", val linePrefix: Boolean = false) {
    H1("H1", "# ", linePrefix = true),
    H2("H2", "## ", linePrefix = true),
    H3("H3", "### ", linePrefix = true),
    BOLD("Bold", "**", "**"),
    ITALIC("Italic", "*", "*"),
    STRIKETHROUGH("Strikethrough", "~~", "~~"),
    CODE("Code", "`", "`"),
    QUOTE("Quote", "> ", linePrefix = true),
    BULLET("Bulleted list", "- ", linePrefix = true),
    NUMBERED("Numbered list", "1. ", linePrefix = true),
}

private data class Line(val before: String, val line: String, val after: String)

private fun currentLine(text: TextFieldValue): Line {
    val head = text.text.substring(0, text.selection.min)
    val lastBreak = head.lastIndexOf('\n')
    val nextBreak = text.text.indexOf('\n', lastBreak + 1)
    val line = text.text.substring(lastBreak + 1, if (nextBreak == -1) text.text.length else nextBreak)
    return Line(
        before = head.take(lastBreak + 1),
        line = line,
        after = if (nextBreak == -1) "" else text.text.substring(nextBreak),
    )
}

/** Makes the current line a task "- [ ] ", ticks a task, or turns a plain list item into a task. */
fun toggleTask(text: TextFieldValue): TextFieldValue {
    val (before, line, after) = currentLine(text)
    for (prefix in LIST_PREFIXES) {
        if (!line.startsWith(prefix)) continue
        if (prefix == "- [ ] ") return text.copy(before + "- [x] " + line.substring(prefix.length) + after)
        val offset = "- [ ] ".length - prefix.length
        return text.copy(
            before + "- [ ] " + line.substring(prefix.length) + after,
            TextRange(text.selection.start + offset, text.selection.end + offset),
        )
    }
    return text.copy(
        "$before- [ ] $line$after",
        TextRange(text.selection.start + 6, text.selection.end + 6),
    )
}

/**
 * Enter on a list item starts the next item with the same marker (an empty task for tasks).
 * Null when the line isn't a list item, or the item is empty (then Enter just ends the list).
 */
fun continueList(text: TextFieldValue): TextFieldValue? {
    val (before, line, _) = currentLine(text)
    val cursorInLine = text.selection.min - before.length
    for (prefix in LIST_PREFIXES) {
        if (!line.startsWith(prefix)) continue
        if (line.length <= prefix.length || cursorInLine <= prefix.length) return null
        val next = if (prefix.startsWith("- [")) "- [ ] " else prefix
        val newText = text.text.substring(0, text.selection.min) + "\n" + next + text.text.substring(text.selection.max)
        val pos = text.selection.min + 1 + next.length
        return text.copy(newText, TextRange(pos))
    }
    val numbered = Regex("^(\\d+)\\. ").find(line)
    if (numbered != null) {
        val marker = numbered.value
        if (line.length <= marker.length || cursorInLine <= marker.length) return null
        val next = "${numbered.groupValues[1].toInt() + 1}. "
        val newText = text.text.substring(0, text.selection.min) + "\n" + next + text.text.substring(text.selection.max)
        return text.copy(newText, TextRange(text.selection.min + 1 + next.length))
    }
    return null
}

/** Wraps the selection (or puts markers at the cursor), or toggles a line prefix like "# ". */
fun applyFormat(text: TextFieldValue, format: MarkdownFormat): TextFieldValue {
    if (format.linePrefix) {
        val (before, line, after) = currentLine(text)
        val candidates = (MarkdownFormat.entries.filter { it.linePrefix }.map { it.prefix } + LIST_PREFIXES)
            .distinct()
            .sortedByDescending { it.length }
        val existing = candidates.firstOrNull { line.startsWith(it) }
        val rest = if (existing != null) line.substring(existing.length) else line
        val prefix = if (existing == format.prefix) "" else format.prefix
        val newText = before + prefix + rest + after
        val offset = prefix.length - (existing?.length ?: 0)
        return text.copy(
            newText,
            TextRange(
                (text.selection.start + offset).coerceIn(0, newText.length),
                (text.selection.end + offset).coerceIn(0, newText.length),
            ),
        )
    }
    if (text.selection.min != text.selection.max) {
        val selected = text.text.substring(text.selection.min, text.selection.max)
        val newText = text.text.substring(0, text.selection.min) + format.prefix + selected + format.suffix + text.text.substring(text.selection.max)
        val pos = text.selection.max + format.prefix.length + format.suffix.length
        return text.copy(newText, TextRange(pos))
    }
    val newText = text.text.substring(0, text.selection.min) + format.prefix + format.suffix + text.text.substring(text.selection.min)
    return text.copy(newText, TextRange(text.selection.min + format.prefix.length))
}

/** Replaces the selection with [replacement] and puts the cursor after it. */
fun replaceSelection(text: TextFieldValue, replacement: String): TextFieldValue {
    val start = text.selection.min
    val updated = text.text.replaceRange(start, text.selection.max, replacement)
    return text.copy(updated, TextRange(start + replacement.length))
}
