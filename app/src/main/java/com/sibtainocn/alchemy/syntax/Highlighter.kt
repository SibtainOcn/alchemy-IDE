package com.sibtainocn.alchemy.syntax

import androidx.compose.ui.text.AnnotatedString
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.theme.AlchemyAccents

/**
 * A single-pass character scanner.
 *
 * Regex highlighting falls apart exactly where it matters - a "#" inside a string, a
 * quote inside a comment, Python's triple-quoted blocks - so this walks the text once,
 * carrying real lexer state. One pass, no backtracking, and comfortably fast enough to
 * run on every keystroke for normal source files.
 */
object Highlighter {

    /**
     * Beyond this, highlighting is skipped and plain text is shown instead.
     *
     * The scan runs on every keystroke. Measured on a desktop JVM: ~1.4 ms at 4 KB,
     * ~4.7 ms at 90 KB, ~7.3 ms at 225 KB, rising roughly linearly.
     *
     * Those numbers argue for a low ceiling until you notice what else happens on that
     * keystroke: Compose re-lays-out the whole document, and at 225 KB that costs far more
     * than the scan does. Cutting the ceiling therefore removes colour from big files
     * without making them meaningfully smoother - the jank is in the layout, not here.
     * So the ceiling stays generous, and the honest fix is incremental highlighting that
     * rescans only the edited region.
     *
     * 250 KB is around 6,000 lines. Above it, files stay fully editable and lose only
     * colour, which is the right way round.
     */
    const val MAX_HIGHLIGHT_CHARS = 250_000

    private val PY_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
        "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
        "global", "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass",
        "raise", "return", "try", "while", "with", "yield", "match", "case",
    )

    private val PY_BUILTINS = setOf(
        "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr", "classmethod",
        "dict", "dir", "divmod", "enumerate", "eval", "filter", "float", "format",
        "frozenset", "getattr", "hasattr", "hash", "hex", "id", "input", "int",
        "isinstance", "issubclass", "iter", "len", "list", "map", "max", "min", "next",
        "object", "oct", "open", "ord", "pow", "print", "property", "range", "repr",
        "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod", "str",
        "sum", "super", "tuple", "type", "vars", "zip", "Exception", "ValueError",
        "TypeError", "KeyError", "IndexError", "RuntimeError", "self", "cls",
    )

    private val KT_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam",
        "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner",
        "internal", "lateinit", "noinline", "open", "operator", "out", "override",
        "private", "protected", "public", "reified", "sealed", "suspend", "tailrec",
        "vararg", "it",
    )

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
    )

    private val JS_KEYWORDS = setOf(
        "async", "await", "break", "case", "catch", "class", "const", "continue",
        "debugger", "default", "delete", "do", "else", "export", "extends", "finally",
        "for", "function", "if", "import", "in", "instanceof", "let", "new", "of",
        "return", "static", "super", "switch", "this", "throw", "try", "typeof", "var",
        "void", "while", "with", "yield", "true", "false", "null", "undefined",
        "interface", "type", "enum", "implements", "declare", "readonly", "as",
    )

    private val C_KEYWORDS = setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
        "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
        "register", "return", "short", "signed", "sizeof", "static", "struct", "switch",
        "typedef", "union", "unsigned", "void", "volatile", "while", "bool", "true",
        "false", "nullptr", "class", "public", "private", "protected", "namespace",
        "template", "typename", "using", "new", "delete", "this", "let", "mut", "fn",
        "impl", "pub", "match", "func", "package", "import", "var", "defer", "go",
    )

    private val SH_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
        "do", "done", "function", "in", "select", "time", "return", "export", "local",
        "readonly", "declare", "source", "alias", "unset", "echo", "cd", "set",
    )

    /**
     * How a language writes a hole in a string literal.
     *
     * Every language here spells the same idea differently, and the differences are the
     * whole point: getting them wrong colours a literal brace in a JSON string as though
     * it were an expression. So each style says exactly which literals carry holes, and a
     * literal that does not carry them is painted as one unbroken string.
     */
    private enum class Holes {
        NONE,

        /** `{expr}`, and only in a literal that announced itself: Python's `f`, C#'s `$`. */
        PREFIXED_BRACE,

        /** `$name` and `${expr}` in a double-quoted literal: Kotlin and the shell. */
        DOLLAR,

        /** `${expr}`, backticks only, which is what a JavaScript template is. */
        BACKTICK_DOLLAR,
    }

    private data class Spec(
        val keywords: Set<String>,
        val builtins: Set<String> = emptySet(),
        val lineComment: String?,
        val blockComment: Pair<String, String>? = null,
        val tripleQuotes: Boolean = false,
        val decoratorChar: Char? = null,
        val holes: Holes = Holes.NONE,
    )

    private fun specFor(lang: Language): Spec = when (lang) {
        Language.PYTHON -> Spec(
            PY_KEYWORDS, PY_BUILTINS, "#",
            tripleQuotes = true, decoratorChar = '@', holes = Holes.PREFIXED_BRACE,
        )
        Language.KOTLIN -> Spec(
            KT_KEYWORDS, emptySet(), "//", "/*" to "*/",
            tripleQuotes = true, decoratorChar = '@', holes = Holes.DOLLAR,
        )
        Language.JAVA -> Spec(JAVA_KEYWORDS, emptySet(), "//", "/*" to "*/", decoratorChar = '@')
        Language.JS -> Spec(
            JS_KEYWORDS, emptySet(), "//", "/*" to "*/",
            tripleQuotes = false, holes = Holes.BACKTICK_DOLLAR,
        )
        // The `$"..."` of C#. Plain C and Rust are left alone deliberately: a brace in a C
        // string is a brace, and whether one in a Rust string is a placeholder depends on
        // which macro is being called, which a scanner at this level cannot know.
        Language.C_LIKE -> Spec(C_KEYWORDS, emptySet(), "//", "/*" to "*/", holes = Holes.PREFIXED_BRACE)
        Language.SHELL -> Spec(SH_KEYWORDS, emptySet(), "#", holes = Holes.DOLLAR)
        Language.CONFIG -> Spec(emptySet(), emptySet(), "#")
        else -> Spec(emptySet(), emptySet(), null)
    }

    /**
     * C and C++ carry the author's own token overrides from their VS Code settings -
     * white numerics, orange string bodies, red library calls, cyan braces - layered on
     * top of Monokai. Every other language uses Monokai unmodified.
     */
    /**
     * Scans [text] and reports every run to [sink]. Nothing here knows what a colour is.
     *
     * Text past [MAX_HIGHLIGHT_CHARS] is left alone: at that size the scan costs more
     * than the colour is worth, and the caller draws it plain.
     */
    fun scan(text: String, lang: Language, sink: TokenSink) {
        if (text.length > MAX_HIGHLIGHT_CHARS) return
        when (lang) {
            Language.JSON -> scanJson(text, sink)
            Language.XML -> scanXml(text, sink)
            Language.MARKDOWN -> scanMarkdown(text, sink)
            Language.PLAIN -> Unit
            else -> scanCode(text, specFor(lang), sink)
        }
    }

    /** [scan] rendered as Compose spans, for the Markdown preview's code blocks. */
    fun highlight(text: String, lang: Language, accents: AlchemyAccents): AnnotatedString {
        if (text.length > MAX_HIGHLIGHT_CHARS) return AnnotatedString(text)
        val sink = AnnotatedStringSink(text, accents, lang)
        scan(text, lang, sink)
        return sink.build()
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    /** Skips whitespace forward and reports the next non-space character. */
    private fun peekNonSpace(text: String, from: Int): Char {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return if (i < text.length) text[i] else ' '
    }

    /** Walks back over whitespace to read the word immediately before [before]. */
    private fun wordBefore(text: String, before: Int): String {
        var i = before - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
        val end = i + 1
        while (i >= 0 && isIdentPart(text[i])) i--
        return if (end > i + 1) text.substring(i + 1, end) else ""
    }

    private fun scanCode(text: String, spec: Spec, b: TokenSink) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]

            // Line comment
            if (spec.lineComment != null && text.startsWith(spec.lineComment, i)) {
                var end = text.indexOf('\n', i)
                if (end < 0) end = n
                b.token(i, end, TokenKind.COMMENT, italic = true)
                i = end
                continue
            }

            // Block comment
            val block = spec.blockComment
            if (block != null && text.startsWith(block.first, i)) {
                var end = text.indexOf(block.second, i + block.first.length)
                end = if (end < 0) n else end + block.second.length
                b.token(i, end, TokenKind.COMMENT, italic = true)
                i = end
                continue
            }

            // Triple-quoted string (Python docstrings, Kotlin raw strings)
            if (spec.tripleQuotes && (c == '"' || c == '\'') &&
                i + 2 < n && text[i + 1] == c && text[i + 2] == c
            ) {
                i = scanStringLiteral(text, i, text.substring(i, i + 3), spec, b)
                continue
            }

            // Single- or double-quoted string, escape-aware.
            if (c == '"' || c == '\'' || c == '`') {
                i = scanStringLiteral(text, i, c.toString(), spec, b)
                continue
            }

            // Decorator / annotation
            if (spec.decoratorChar != null && c == spec.decoratorChar &&
                i + 1 < n && isIdentStart(text[i + 1])
            ) {
                var j = i + 1
                while (j < n && (isIdentPart(text[j]) || text[j] == '.')) j++
                b.token(i, j, TokenKind.DECORATOR)
                i = j
                continue
            }

            // Number: decimal, hex, float, exponent, underscores
            if (c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit() &&
                    (i == 0 || !isIdentPart(text[i - 1])))
            ) {
                if (i > 0 && isIdentPart(text[i - 1])) {
                    // Part of an identifier such as utf8 - not a literal.
                    var j = i
                    while (j < n && isIdentPart(text[j])) j++
                    i = j
                    continue
                }
                var j = i
                while (j < n && (text[j].isLetterOrDigit() || text[j] == '.' || text[j] == '_')) {
                    if (text[j] == '.' && j + 1 < n && !text[j + 1].isDigit()) break
                    j++
                }
                b.token(i, j, TokenKind.NUMBER)
                i = j
                continue
            }

            // Identifier
            if (isIdentStart(c)) {
                var j = i
                while (j < n && isIdentPart(text[j])) j++
                val word = text.substring(i, j)
                val prev = wordBefore(text, i)
                val isDefName = prev == "def" || prev == "fun" || prev == "class" || prev == "function"
                // Monokai puts types and builtins in cyan italic, self/cls in orange italic,
                // and everything it defines or calls in green.
                val kind: TokenKind? = when {
                    word in spec.keywords -> TokenKind.KEYWORD
                    word == "self" || word == "cls" || word == "this" -> TokenKind.SELF_REF
                    isDefName -> TokenKind.FUNCTION
                    // A call wins over the builtin list: Monokai paints print(...) the
                    // same green as any other invocation, and reserves cyan for names
                    // used as types.
                    peekNonSpace(text, j) == '(' -> TokenKind.FUNCTION
                    word in spec.builtins -> TokenKind.BUILTIN
                    // Leading capital reads as a type in every language here.
                    word.first().isUpperCase() -> TokenKind.BUILTIN
                    // Not one of the kinds worth naming: left as ordinary text rather
                    // than reported, so the sink has nothing to draw over.
                    else -> null
                }
                if (kind != null) {
                    b.token(i, j, kind, italic = kind == TokenKind.BUILTIN || kind == TokenKind.SELF_REF)
                }
                i = j
                continue
            }

            // Operators and punctuation
            if (c in "+-*/%=<>!&|^~") {
                var j = i
                while (j < n && text[j] in "+-*/%=<>!&|^~") j++
                b.token(i, j, TokenKind.OPERATOR)
                i = j
                continue
            }
            if (c in "()[]{},;:.") {
                b.token(i, i + 1, if (c == '{' || c == '}') TokenKind.BRACE else TokenKind.PUNCTUATION)
                i++
                continue
            }

            i++
        }
    }

    /** The letters that may sit in front of a quote and still belong to the literal. */
    private const val PREFIX_CHARS = "fFrRbBuU$"

    /**
     * Where the literal that closes at [quoteAt] actually begins.
     *
     * Two characters at most, because that is as long as a real prefix gets - `rf`, `bR`,
     * `f` - and only when what precedes them is not itself part of a word: the `r` in
     * `var"x"` opens nothing.
     */
    private fun prefixStart(text: String, quoteAt: Int): Int {
        var start = quoteAt
        while (start > 0 && quoteAt - start < 2 && text[start - 1] in PREFIX_CHARS) start--
        if (start == quoteAt) return quoteAt
        return if (start == 0 || !isIdentPart(text[start - 1])) start else quoteAt
    }

    /** Which marks open a hole in this particular literal. */
    private data class HoleKinds(val brace: Boolean = false, val dollar: Boolean = false)

    private fun holesOf(style: Holes, prefix: String, fence: String): HoleKinds = when (style) {
        Holes.NONE -> HoleKinds()
        // Only when the literal announced itself: `f"{x}"` has a hole, `"{x}"` is text
        // that happens to contain braces, and colouring the second would be a lie.
        Holes.PREFIXED_BRACE -> HoleKinds(brace = prefix.any { it == 'f' || it == 'F' || it == '$' })
        // A single-quoted literal is a character in Kotlin and is literal text in the
        // shell, so neither carries a hole.
        Holes.DOLLAR -> HoleKinds(dollar = fence.startsWith("\""))
        Holes.BACKTICK_DOLLAR -> HoleKinds(dollar = fence == "`")
    }

    /**
     * One string literal, from its prefix through to its closing quote.
     *
     * All of it is [TokenKind.STRING] except the holes the language allows in it: the
     * marks that open and close one are [TokenKind.INTERPOLATION] and what sits between
     * them is [TokenKind.TEXT]. An f-string's braces do not hold string content, they hold
     * an expression, and reading `f"{total:.2f}"` as one uniform run of colour hides the
     * only part of it that is code.
     *
     * Returns the index just past the literal, which is always past [quoteAt] so the
     * caller's walk cannot stall.
     */
    private fun scanStringLiteral(
        text: String,
        quoteAt: Int,
        fence: String,
        spec: Spec,
        b: TokenSink,
    ): Int {
        val n = text.length
        val start = prefixStart(text, quoteAt)
        val holes = holesOf(spec.holes, text.substring(start, quoteAt), fence)
        // A triple-quoted block is taken at face value, the way it was before holes
        // existed: an escape inside a Kotlin raw string is not an escape.
        val escapes = fence.length == 1
        val oneLine = fence == "\"" || fence == "'"

        var runStart = start
        var i = quoteAt + fence.length
        while (i < n) {
            val c = text[i]
            if (escapes && c == '\\') { i = (i + 2).coerceAtMost(n); continue }
            if (text.startsWith(fence, i)) { i += fence.length; break }
            // An unterminated quote should not swallow the rest of the file.
            if (oneLine && c == '\n') break

            if (holes.brace) {
                // `{{` and `}}` are how a formatted string writes a brace it means
                // literally, so neither opens anything.
                if (text.startsWith("{{", i) || text.startsWith("}}", i)) { i += 2; continue }
                if (c == '{') {
                    b.token(runStart, i, TokenKind.STRING)
                    i = paintHole(text, i, marks = 1, b = b)
                    runStart = i
                    continue
                }
            }

            if (holes.dollar && c == '$' && i + 1 < n) {
                val next = text[i + 1]
                if (next == '{') {
                    b.token(runStart, i, TokenKind.STRING)
                    i = paintHole(text, i, marks = 2, b = b)
                    runStart = i
                    continue
                }
                // The short form: `$name`, with no braces to close.
                if (isIdentStart(next)) {
                    b.token(runStart, i, TokenKind.STRING)
                    var j = i + 2
                    while (j < n && isIdentPart(text[j])) j++
                    b.token(i, i + 1, TokenKind.INTERPOLATION)
                    b.token(i + 1, j, TokenKind.TEXT)
                    i = j
                    runStart = i
                    continue
                }
            }
            i++
        }
        b.token(runStart, i.coerceAtMost(n), TokenKind.STRING)
        return i.coerceAtLeast(quoteAt + 1)
    }

    /**
     * The hole opening at [at], and everything inside it.
     *
     * [marks] is how many characters open it - one for `{`, two for `${`. Braces are
     * counted so a Python format spec such as `{x:{width}}` ends where it should, and a
     * quoted string inside the expression is stepped over so that a `}` within it cannot
     * close the hole early. An unclosed hole ends at the line break rather than running on.
     */
    private fun paintHole(text: String, at: Int, marks: Int, b: TokenSink): Int {
        val n = text.length
        val open = (at + marks).coerceAtMost(n)
        var i = open
        var depth = 1
        while (i < n) {
            val c = text[i]
            if (c == '\n') break
            if (c == '"' || c == '\'') {
                i++
                while (i < n && text[i] != c && text[i] != '\n') {
                    if (text[i] == '\\') i++
                    i++
                }
                i = (i + 1).coerceAtMost(n)
                continue
            }
            if (c == '{') { depth++; i++; continue }
            if (c == '}') { depth--; if (depth == 0) break; i++; continue }
            i++
        }
        val closed = depth == 0 && i < n && text[i] == '}'
        val bodyEnd = i.coerceAtMost(n)
        b.token(at, open, TokenKind.INTERPOLATION)
        b.token(open, bodyEnd, TokenKind.TEXT)
        if (!closed) return bodyEnd
        b.token(bodyEnd, bodyEnd + 1, TokenKind.INTERPOLATION)
        return bodyEnd + 1
    }

    private fun scanJson(text: String, b: TokenSink) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '"') {
                var j = i + 1
                while (j < n) {
                    if (text[j] == '\\') { j += 2; continue }
                    if (text[j] == '"') { j++; break }
                    j++
                }
                // A string followed by a colon is a key, everything else is a value.
                val isKey = peekNonSpace(text, j) == ':'
                b.token(i, j.coerceAtMost(n), if (isKey) TokenKind.FUNCTION else TokenKind.STRING, bold = isKey)
                i = j.coerceAtLeast(i + 1)
                continue
            }
            if (c.isDigit() || (c == '-' && i + 1 < n && text[i + 1].isDigit())) {
                var j = i + 1
                while (j < n && (text[j].isDigit() || text[j] in ".eE+-")) j++
                b.token(i, j, TokenKind.NUMBER)
                i = j
                continue
            }
            if (isIdentStart(c)) {
                var j = i
                while (j < n && isIdentPart(text[j])) j++
                if (text.substring(i, j) in setOf("true", "false", "null")) b.token(i, j, TokenKind.KEYWORD)
                i = j
                continue
            }
            if (c in "{}[],:") b.token(i, i + 1, TokenKind.PUNCTUATION)
            i++
        }
    }

    private fun scanXml(text: String, b: TokenSink) {
        var i = 0
        val n = text.length
        while (i < n) {
            if (text.startsWith("<!--", i)) {
                var end = text.indexOf("-->", i)
                end = if (end < 0) n else end + 3
                b.token(i, end, TokenKind.COMMENT, italic = true)
                i = end
                continue
            }
            if (text[i] == '<') {
                var j = i + 1
                if (j < n && (text[j] == '/' || text[j] == '?' || text[j] == '!')) j++
                val nameStart = j
                while (j < n && (isIdentPart(text[j]) || text[j] == ':' || text[j] == '-')) j++
                b.token(i, nameStart, TokenKind.PUNCTUATION)
                b.token(nameStart, j, TokenKind.KEYWORD, bold = true)

                // Attributes up to the closing angle bracket.
                while (j < n && text[j] != '>') {
                    when {
                        text[j] == '"' || text[j] == '\'' -> {
                            val q = text[j]
                            var k = j + 1
                            while (k < n && text[k] != q) k++
                            b.token(j, (k + 1).coerceAtMost(n), TokenKind.STRING)
                            j = (k + 1).coerceAtMost(n)
                        }
                        isIdentStart(text[j]) -> {
                            var k = j
                            while (k < n && (isIdentPart(text[k]) || text[k] == ':' || text[k] == '-')) k++
                            b.token(j, k, TokenKind.BUILTIN)
                            j = k
                        }
                        else -> j++
                    }
                }
                b.token(j, (j + 1).coerceAtMost(n), TokenKind.PUNCTUATION)
                i = (j + 1).coerceAtMost(n)
                continue
            }
            i++
        }
    }

    /** Light touch: enough structure to read raw Markdown, no more. */
    private fun scanMarkdown(text: String, b: TokenSink) {
        var lineStart = 0
        var inFence = false
        while (lineStart < text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            when {
                trimmed.startsWith("```") -> {
                    b.token(lineStart, lineEnd, TokenKind.DECORATOR)
                    inFence = !inFence
                }
                inFence -> b.token(lineStart, lineEnd, TokenKind.CODE_SPAN)
                trimmed.startsWith("#") -> b.token(lineStart, lineEnd, TokenKind.FUNCTION, bold = true)
                trimmed.startsWith(">") -> b.token(lineStart, lineEnd, TokenKind.COMMENT, italic = true)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                    b.token(lineStart + indent, lineStart + indent + 1, TokenKind.KEYWORD, bold = true)
                trimmed.startsWith("---") || trimmed.startsWith("===") ->
                    b.token(lineStart, lineEnd, TokenKind.PUNCTUATION)
            }

            if (!inFence && !trimmed.startsWith("#")) {
                markInline(text, lineStart, lineEnd, b)
            }
            lineStart = lineEnd + 1
        }
    }

    private fun markInline(
        text: String,
        from: Int,
        to: Int,
        b: TokenSink,
    ) {
        var i = from
        while (i < to) {
            when {
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end in (i + 1) until to) {
                        // Raw text rather than a quoted string, and coloured apart from
                        // one: the string colour belongs to strings.
                        b.token(i, end + 1, TokenKind.CODE_SPAN)
                        i = end + 1
                        continue
                    }
                }
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end in (i + 2) until to) {
                        // Bold is weight, not colour. Tinting it cyan made ordinary
                        // emphasised prose look like a symbol.
                        b.token(i, end + 2, TokenKind.TEXT, bold = true)
                        i = end + 2
                        continue
                    }
                }
                text[i] == '[' -> {
                    val end = text.indexOf(')', i)
                    if (end in i until to && text.indexOf("](", i).let { it in i until end }) {
                        b.token(i, end + 1, TokenKind.NUMBER)
                        i = end + 1
                        continue
                    }
                }
            }
            i++
        }
    }
}
