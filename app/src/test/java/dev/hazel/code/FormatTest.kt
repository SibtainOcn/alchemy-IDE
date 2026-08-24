package dev.hazel.code

import dev.hazel.code.data.Entry
import dev.hazel.code.data.Language
import dev.hazel.code.ui.common.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FormatTest {

    private fun entry(
        name: String,
        isDir: Boolean = false,
        size: Long = 0,
        children: Int = 0,
    ) = Entry(
        file = File("/storage/emulated/0/$name"),
        isDir = isDir,
        name = name,
        sizeBytes = size,
        modified = 0L,
        childCount = children,
    )

    @Test
    fun `sizes step through the units`() {
        assertEquals("512 B", Fmt.size(512))
        assertEquals("1.0 KB", Fmt.size(1024))
        assertEquals("1.5 KB", Fmt.size(1536))
        assertEquals("1.0 MB", Fmt.size(1024L * 1024))
        assertEquals("1.00 GB", Fmt.size(1024L * 1024 * 1024))
    }

    @Test
    fun `folder subtitles count children and singularise`() {
        assertEquals("Empty", Fmt.subtitle(entry("src", isDir = true, children = 0)))
        assertEquals("1 item", Fmt.subtitle(entry("src", isDir = true, children = 1)))
        assertEquals("19 items", Fmt.subtitle(entry("src", isDir = true, children = 19)))
    }

    @Test
    fun `an unreadable folder reads as locked, not empty`() {
        assertEquals("Locked", Fmt.subtitle(entry("android", isDir = true, children = -1)))
    }

    @Test
    fun `file subtitles show the size`() {
        assertEquals("970 B", Fmt.subtitle(entry("concept_1.py", size = 970)))
    }

    @Test
    fun `a missing timestamp does not render as 1970`() {
        assertEquals("--", Fmt.date(0L))
    }

    @Test
    fun `extensions map to languages, including fence labels`() {
        assertEquals(Language.PYTHON, Language.of("concept_1.py"))
        assertEquals(Language.PYTHON, Language.of("x.python"))
        assertEquals(Language.MARKDOWN, Language.of("Readme_Overwiew.md"))
        assertEquals(Language.KOTLIN, Language.of("build.gradle.kts"))
        assertEquals(Language.CONFIG, Language.of("app.yml"))
        assertEquals(Language.PLAIN, Language.of("LOGS-ENTRY.txt"))
        assertEquals(Language.PLAIN, Language.of("Makefile"))
    }
}
