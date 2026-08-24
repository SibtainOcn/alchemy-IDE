package dev.hazel.code

import dev.hazel.code.ui.preview.MdAlign
import dev.hazel.code.ui.preview.MdBlock
import dev.hazel.code.ui.preview.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The renderer broke on a real README, and it broke in the parser: every non-blank line
 * was folded into one paragraph, so a table arrived as a wall of pipes. These pin the
 * rule that fixed it — a block start always interrupts the paragraph before it — and the
 * table shapes that showed the problem.
 */
class MarkdownParserTest {

    private fun parse(src: String) = MarkdownParser.parse(src.trimIndent())

    @Test
    fun `a table is parsed, not folded into a paragraph`() {
        val blocks = parse(
            """
            Here's the proof:

            | Exercise | Concept | Use |
            |----------|---------|-----|
            | 1-5 | String methods | Parsing log files |
            | 6 | f-string | Formatting alerts |

            After the table.
            """
        )
        val table = blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("Exercise", "Concept", "Use"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("1-5", "String methods", "Parsing log files"), table.rows[0])
        // The prose either side must survive as its own paragraphs.
        val paragraphs = blocks.filterIsInstance<MdBlock.Paragraph>().map { it.text }
        assertEquals(listOf("Here's the proof:", "After the table."), paragraphs)
    }

    @Test
    fun `a table directly after a paragraph still interrupts it`() {
        // This is the exact shape that used to collapse: no blank line before the table.
        val blocks = parse(
            """
            Module 02: what you learn
            | A | B |
            |---|---|
            | 1 | 2 |
            """
        )
        assertEquals(1, blocks.filterIsInstance<MdBlock.Table>().size)
        assertEquals("Module 02: what you learn", blocks.filterIsInstance<MdBlock.Paragraph>().single().text)
    }

    @Test
    fun `empty cells are kept so columns stay aligned`() {
        val table = parse(
            """
            | A | B | C |
            |---|---|---|
            | 1 |  | 3 |
            """
        ).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("1", "", "3"), table.rows.single())
    }

    @Test
    fun `a ragged row is padded rather than dropped`() {
        val table = parse(
            """
            | A | B | C |
            |---|---|---|
            | 1 | 2 |
            """
        ).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("1", "2", ""), table.rows.single())
    }

    @Test
    fun `alignment colons are read`() {
        val table = parse(
            """
            | L | C | R |
            |:--|:-:|--:|
            | 1 | 2 | 3 |
            """
        ).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf(MdAlign.START, MdAlign.CENTER, MdAlign.END), table.alignments)
    }

    @Test
    fun `prose containing a pipe is not mistaken for a table`() {
        val blocks = parse("Use a | b to pipe, or c | d.\nStill prose.")
        assertTrue(blocks.filterIsInstance<MdBlock.Table>().isEmpty())
        assertEquals(1, blocks.filterIsInstance<MdBlock.Paragraph>().size)
    }

    @Test
    fun `a table needs its delimiter row`() {
        assertFalse(MarkdownParser.isTableStart(listOf("| a | b |", "| 1 | 2 |"), 0))
        assertTrue(MarkdownParser.isTableStart(listOf("| a | b |", "|---|---|"), 0))
    }

    @Test
    fun `escaped pipes stay inside their cell`() {
        assertEquals(listOf("a|b", "c"), MarkdownParser.splitRow("""| a\|b | c |"""))
    }

    @Test
    fun `fenced code keeps its lines and is not reflowed`() {
        val code = parse(
            """
            ```python
            def f():
                return 1
            ```
            """
        ).filterIsInstance<MdBlock.Code>().single()
        assertEquals("python", code.lang)
        assertEquals("def f():\n    return 1", code.body)
    }

    @Test
    fun `markdown syntax inside a fence is left alone`() {
        val code = parse(
            """
            ```
            # not a heading
            | not | a table |
            ```
            """
        ).filterIsInstance<MdBlock.Code>().single()
        assertTrue(code.body.contains("# not a heading"))
        assertEquals(0, parse("```\n# x\n```").filterIsInstance<MdBlock.Heading>().size)
    }

    @Test
    fun `an unterminated fence does not lose the rest of the file`() {
        val blocks = parse("```\nstuck")
        assertEquals("stuck", blocks.filterIsInstance<MdBlock.Code>().single().body)
    }

    @Test
    fun `headings interrupt a paragraph`() {
        val blocks = parse(
            """
            some text
            ## A heading
            more text
            """
        )
        assertEquals(1, blocks.filterIsInstance<MdBlock.Heading>().size)
        assertEquals(2, blocks.filterIsInstance<MdBlock.Paragraph>().size)
    }

    @Test
    fun `heading levels and trailing hashes`() {
        assertEquals(3, (parse("### Third").single() as MdBlock.Heading).level)
        assertEquals("Closed", (parse("## Closed ##").single() as MdBlock.Heading).text)
        // Seven hashes is not a heading, and neither is a hash with no space.
        assertTrue(parse("####### too deep").single() is MdBlock.Paragraph)
        assertTrue(parse("#nospace").single() is MdBlock.Paragraph)
    }

    @Test
    fun `lists keep their nesting depth`() {
        val items = parse(
            """
            - top
              - nested
                - deeper
            """
        ).filterIsInstance<MdBlock.Item>()
        assertEquals(listOf(0, 1, 2), items.map { it.depth })
        assertEquals(listOf("top", "nested", "deeper"), items.map { it.text })
    }

    @Test
    fun `ordered lists keep their numbers`() {
        val items = parse(
            """
            1. Write scripts
            2. Automate tasks
            """
        ).filterIsInstance<MdBlock.Item>()
        assertTrue(items.all { it.ordered })
        assertEquals(listOf("1.", "2."), items.map { it.marker })
    }

    @Test
    fun `task list state is read`() {
        val items = parse(
            """
            - [x] done
            - [ ] todo
            - plain
            """
        ).filterIsInstance<MdBlock.Item>()
        assertEquals(listOf(true, false, null), items.map { it.checked })
        assertEquals("done", items[0].text)
    }

    @Test
    fun `rules are told apart from list dashes and setext underlines`() {
        assertTrue(parse("---").single() is MdBlock.Rule)
        assertTrue(parse("***").single() is MdBlock.Rule)
        assertTrue(parse("- item").single() is MdBlock.Item)
    }

    @Test
    fun `setext underlines become headings`() {
        val blocks = parse(
            """
            Title here
            ==========
            """
        )
        val heading = blocks.filterIsInstance<MdBlock.Heading>().single()
        assertEquals(1, heading.level)
        assertEquals("Title here", heading.text)
    }

    @Test
    fun `blank lines separate paragraphs`() {
        val blocks = parse("one\ntwo\n\nthree")
        val paragraphs = blocks.filterIsInstance<MdBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("one two", paragraphs[0].text)
        assertEquals("three", paragraphs[1].text)
    }

    @Test
    fun `windows line endings parse the same as unix`() {
        val unix = MarkdownParser.parse("# A\n\n- one\n- two")
        val windows = MarkdownParser.parse("# A\r\n\r\n- one\r\n- two")
        assertEquals(unix, windows)
    }

    @Test
    fun `an empty document produces no blocks`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("\n\n   \n").isEmpty())
    }

    @Test
    fun `no text is lost from a mixed document`() {
        // Whatever the parser does with structure, the words must all still be somewhere.
        val src = """
            # Title

            Intro paragraph with **bold**.

            | A | B |
            |---|---|
            | 1 | 2 |

            - bullet one
            - bullet two

            > a quote

            ```py
            x = 1
            ```
        """.trimIndent()

        val words = MarkdownParser.parse(src).joinToString(" ") { block ->
            when (block) {
                is MdBlock.Heading -> block.text
                is MdBlock.Paragraph -> block.text
                is MdBlock.Quote -> block.text
                is MdBlock.Code -> block.body
                is MdBlock.Item -> block.text
                is MdBlock.Table -> (block.headers + block.rows.flatten()).joinToString(" ")
                MdBlock.Rule -> ""
            }
        }
        listOf("Title", "Intro", "bold", "A", "B", "1", "2", "bullet one", "bullet two", "a quote", "x = 1")
            .forEach { assertTrue("Lost \"$it\"", words.contains(it)) }
    }
}
