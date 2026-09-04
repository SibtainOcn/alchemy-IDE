package com.sibtainocn.alchemy.data

import java.io.File

enum class Language(val label: String) {
    PYTHON("Python"),
    MARKDOWN("Markdown"),
    JSON("JSON"),
    XML("XML"),
    KOTLIN("Kotlin"),
    JAVA("Java"),
    JS("JavaScript"),
    C_LIKE("C"),
    SHELL("Shell"),
    CONFIG("Config"),
    PLAIN("Text");

    companion object {
        fun of(name: String): Language = when (name.substringAfterLast('.', "").lowercase()) {
            // Long names appear as Markdown fence labels ("```python"), which route
            // through here too.
            "py", "pyw", "pyi", "python" -> PYTHON
            "md", "markdown", "mdx" -> MARKDOWN
            "json", "jsonc" -> JSON
            "xml", "html", "htm", "svg" -> XML
            "kt", "kts", "kotlin" -> KOTLIN
            "java" -> JAVA
            "js", "mjs", "ts", "tsx", "jsx", "javascript", "typescript" -> JS
            "c", "h", "cpp", "hpp", "cc", "cs", "rs", "go", "swift" -> C_LIKE
            "sh", "bash", "zsh", "shell", "console" -> SHELL
            "toml", "ini", "cfg", "conf", "properties", "yml", "yaml", "env", "gradle" -> CONFIG
            else -> PLAIN
        }
    }
}

/**
 * What a file is, for the purpose of drawing it.
 *
 * Deliberately not the same question as [Language]. A `.png` has no syntax and a `.zip`
 * has no text, but both need a glyph, and a row of identical grey squares tells you
 * nothing about what is in a folder. Source files keep their extension tag because the
 * extension is the useful part; everything else gets a shape you can recognise across
 * the room.
 */
enum class FileKind {
    /** Anything with syntax. Drawn as the language tag. */
    CODE,
    TEXT,
    PDF,
    ARCHIVE,
    IMAGE,
    VIDEO,
    AUDIO,

    /** Installable packages. */
    APP,

    /** Compiled, packed or otherwise not for reading. */
    BINARY;

    companion object {
        fun of(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "avif" -> IMAGE
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "flv", "wmv", "mpg", "mpeg" -> VIDEO
            "mp3", "wav", "ogg", "opus", "m4a", "flac", "aac", "amr", "mid", "wma" -> AUDIO
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "zst", "jar", "iso" -> ARCHIVE
            "pdf" -> PDF
            "apk", "aab", "apks", "xapk" -> APP
            "exe", "dll", "so", "bin", "dat", "db", "sqlite", "ttf", "otf", "woff",
            "woff2", "class", "o", "a", "pyc", "img" -> BINARY
            "txt", "log", "rtf", "csv", "tsv", "doc", "docx", "odt", "xls", "xlsx",
            "ppt", "pptx", "srt", "vtt", "nfo" -> TEXT
            // Falls back on the syntax question: anything a highlighter recognises is
            // source, and anything left over is a document with a name on it.
            else -> if (Language.of(name) != Language.PLAIN) CODE else TEXT
        }
    }
}

/** One row in the explorer. [childCount] is -1 when the folder could not be read. */
data class Entry(
    val file: File,
    val isDir: Boolean,
    val name: String,
    val sizeBytes: Long,
    val modified: Long,
    val childCount: Int,
) {
    val language: Language get() = Language.of(name)
    val kind: FileKind get() = FileKind.of(name)
    val isHidden: Boolean get() = name.startsWith('.')
}

/** Whether a staged entry should be duplicated at its destination or relocated to it. */
enum class Transfer { COPY, MOVE }

enum class SortBy(val label: String) {
    MODIFIED("Date modified"),
    NAME("Name"),
    SIZE("Size"),
    TYPE("Type"),
}
