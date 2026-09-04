package com.sibtainocn.alchemy.ui.preview

/**
 * A block parser for the Markdown people actually write in READMEs.
 *
 * The previous version merged every run of non-blank lines into one paragraph, which is
 * correct CommonMark for prose and catastrophic for a table: the rows, the `|---|` rule
 * and the surrounding text all collapsed into a single wall of pipes. Block starts are
 * now recognised *before* lazy continuation, so a table, list, heading or fence always
 * interrupts the paragraph it follows.
 *
 * Deliberately not a CommonMark implementation. It covers headings, fenced code, tables,
 * nested and task lists, quotes, rules and the inline run - and anything it does not
 * recognise survives as visible text instead of vanishing.
 */

enum class MdAlign { START, CENTER, END }

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val lang: String, val body: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val alignments: List<MdAlign>,
    ) : MdBlock

    data class Item(
        val depth: Int,
        val marker: String,
        val ordered: Boolean,
        val text: String,
        /** null when this is not a task item. */
        val checked: Boolean? = null,
    ) : MdBlock

    data object Rule : MdBlock
}

object MarkdownParser {

    private val ORDERED = Regex("""^(\d{1,9})[.)]\s+(.*)$""")
    private val UNORDERED = Regex("""^([-*+])\s+(.*)$""")
    private val TASK = Regex("""^\[([ xX])]\s*(.*)$""")
    private val SETEXT_H1 = Regex("""^=+\s*$""")
    private val SETEXT_H2 = Regex("""^-{2,}\s*$""")

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val out = mutableListOf<MdBlock>()
        val paragraph = StringBuilder()
        var i = 0

        fun flush() {
            if (paragraph.isNotBlank()) out += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }

        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            val trimmed = line.trim()

            // --- Fenced code, first: everything inside it is literal ---
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                flush()
                val fence = trimmed.take(3)
                val lang = trimmed.drop(3).trim().substringBefore(' ')
                val body = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(fence)) {
                    body.appendLine(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, body.toString().trimEnd('\n'))
                i++ // closing fence, or past the end for an unterminated block
                continue
            }

            // --- Table: header row plus a delimiter row of dashes ---
            if (isTableStart(lines, i)) {
                flush()
                val table = parseTable(lines, i)
                if (table != null) {
                    out += table.block
                    i = table.nextIndex
                    continue
                }
            }

            // --- Blank line ends a paragraph ---
            if (trimmed.isEmpty()) {
                flush()
                i++
                continue
            }

            // --- ATX heading ---
            val hashes = trimmed.takeWhile { it == '#' }.length
            if (hashes in 1..6 && trimmed.length > hashes && trimmed[hashes] == ' ') {
                flush()
                out += MdBlock.Heading(hashes, trimmed.drop(hashes).trim().trimEnd('#').trim())
                i++
                continue
            }

            // --- Setext heading: underlined text, but only when it follows a paragraph ---
            if (paragraph.isNotBlank() && SETEXT_H1.matches(trimmed)) {
                out += MdBlock.Heading(1, paragraph.toString().trim())
                paragraph.setLength(0)
                i++
                continue
            }
            if (paragraph.isNotBlank() && SETEXT_H2.matches(trimmed) && !trimmed.startsWith("---")) {
                out += MdBlock.Heading(2, paragraph.toString().trim())
                paragraph.setLength(0)
                i++
                continue
            }

            // --- Thematic break ---
            if (isRule(trimmed)) {
                flush()
                out += MdBlock.Rule
                i++
                continue
            }

            // --- Blockquote ---
            if (trimmed.startsWith(">")) {
                flush()
                out += MdBlock.Quote(trimmed.removePrefix(">").trim())
                i++
                continue
            }

            // --- List items, ordered and unordered, with task-list support ---
            val indent = line.takeWhile { it == ' ' || it == '\t' }
                .sumOf { if (it == '\t') 4 else 1 }
            val depth = indent / 2

            UNORDERED.matchEntire(trimmed)?.let { m ->
                flush()
                val body = m.groupValues[2]
                val task = TASK.matchEntire(body)
                out += if (task != null) {
                    MdBlock.Item(
                        depth = depth,
                        marker = "•",
                        ordered = false,
                        text = task.groupValues[2],
                        checked = !task.groupValues[1].isBlank(),
                    )
                } else {
                    MdBlock.Item(depth, "•", ordered = false, text = body)
                }
                i++
                return@let
            }?.also { continue }

            ORDERED.matchEntire(trimmed)?.let { m ->
                flush()
                out += MdBlock.Item(depth, m.groupValues[1] + ".", ordered = true, text = m.groupValues[2])
                i++
                return@let
            }?.also { continue }

            // --- Otherwise it is paragraph text, joined to whatever came before ---
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(trimmed)
            i++
        }
        flush()
        return out
    }

    /**
     * A table needs a header row and a delimiter row directly under it. Requiring the
     * dashes is what stops a line of prose containing a pipe from being eaten as a table.
     */
    fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) return false
        val header = lines[index].trim()
        val delimiter = lines[index + 1].trim()
        if (!header.contains('|')) return false
        if (!delimiter.contains('-')) return false
        // Every delimiter cell must be dashes, with optional alignment colons.
        val cells = splitRow(delimiter)
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val c = cell.trim()
            c.isNotEmpty() && c.all { it == '-' || it == ':' } && c.contains('-')
        }
    }

    private data class TableResult(val block: MdBlock.Table, val nextIndex: Int)

    private fun parseTable(lines: List<String>, start: Int): TableResult? {
        val headers = splitRow(lines[start]).map { it.trim() }
        if (headers.isEmpty()) return null

        val alignments = splitRow(lines[start + 1]).map { cell ->
            val c = cell.trim()
            when {
                c.startsWith(":") && c.endsWith(":") -> MdAlign.CENTER
                c.endsWith(":") -> MdAlign.END
                else -> MdAlign.START
            }
        }

        var i = start + 2
        val rows = mutableListOf<List<String>>()
        while (i < lines.size) {
            val line = lines[i].trim()
            if (!line.contains('|') || line.isEmpty()) break
            val cells = splitRow(line).map { it.trim() }
            // Pad or trim to the header width rather than dropping the row: a ragged row
            // is still information, and discarding it silently loses data.
            rows += List(headers.size) { cells.getOrElse(it) { "" } }
            i++
        }
        if (rows.isEmpty()) return null

        return TableResult(
            MdBlock.Table(
                headers = headers,
                rows = rows,
                alignments = List(headers.size) { alignments.getOrElse(it) { MdAlign.START } },
            ),
            i,
        )
    }

    /**
     * Splits a table row on unescaped pipes, dropping only the leading and trailing
     * delimiters. Empty cells in the middle are kept - they are columns, not noise.
     */
    fun splitRow(line: String): List<String> {
        val body = line.trim().removePrefix("|").removeSuffix("|")
        if (body.isEmpty()) return emptyList()
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length && body[i + 1] == '|' -> {
                    current.append('|'); i += 2; continue
                }
                c == '|' -> {
                    cells += current.toString(); current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells.map { it.trim() }
    }

    private fun isRule(trimmed: String): Boolean {
        if (trimmed.length < 3) return false
        val stripped = trimmed.filterNot { it == ' ' }
        return stripped.length >= 3 &&
            (stripped.all { it == '-' } || stripped.all { it == '*' } || stripped.all { it == '_' })
    }
}
