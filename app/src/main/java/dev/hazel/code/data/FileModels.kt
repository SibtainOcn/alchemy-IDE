package dev.hazel.code.data

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
    val isHidden: Boolean get() = name.startsWith('.')
}

enum class SortBy(val label: String) {
    MODIFIED("Date modified"),
    NAME("Name"),
    SIZE("Size"),
    TYPE("Type"),
}
