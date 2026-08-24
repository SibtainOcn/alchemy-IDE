package dev.hazel.code.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import dev.hazel.code.data.Language

/**
 * Everything the key bar decides, kept out of the composable.
 *
 * A phone keyboard has no Ctrl, no Tab and no arrows, and its Shift only affects letters.
 * The bar supplies those, which means it needs real modifier state — and modifier state
 * expressed as booleans scattered through a composable is exactly the kind of thing that
 * breaks silently. It lives here instead, as data and pure functions, so it can be tested
 * without a device.
 *
 * Ctrl and Shift *latch*: one tap arms them for the next key, which is the only workable
 * gesture when you cannot hold two keys at once. Caps *locks*, because holding it for a
 * word is the whole point.
 */

/** What a key does when it is pressed. */
sealed interface KeyOutcome {
    /** A pure text edit the view model can apply and undo as one step. */
    data class Edit(val op: (TextFieldValue) -> TextFieldValue) : KeyOutcome

    /** Something only the screen can do — save, clipboard, undo history. */
    data class Command(val command: EditorCommand) : KeyOutcome

    /** A modifier changed; the caller has nothing to apply. */
    data object None : KeyOutcome
}

enum class EditorCommand { SAVE, UNDO, REDO, COPY, CUT, PASTE }

enum class KeyKind {
    /** Inserts its own text. */
    INSERT,

    /** Runs an editor operation. */
    ACTION,

    /** Latches or locks; changes what the other keys do. */
    MODIFIER,
}

/**
 * One key. [id] is stable and is what gets persisted in the user's chosen order — labels
 * and behaviour can change between versions without scrambling a saved layout.
 */
data class BarKey(
    val id: String,
    val label: String,
    val kind: KeyKind = KeyKind.INSERT,
    /** Text inserted for [KeyKind.INSERT]; caret lands [caretOffset] into it. */
    val text: String = label,
    val caretOffset: Int = text.length,
    /** Shown in place of [label] while Shift is armed. */
    val shiftedLabel: String? = null,
)

/** The three modifiers, as one immutable value so state updates are atomic. */
data class Modifiers(
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val caps: Boolean = false,
) {
    /** Shift is spent by the next ordinary key; Ctrl and Caps are not. */
    fun afterOrdinaryKey(): Modifiers = copy(shift = false, ctrl = false)
}

object KeyBarModel {

    const val SHIFT = "mod.shift"
    const val CTRL = "mod.ctrl"
    const val CAPS = "mod.caps"
    const val TAB = "act.tab"

    /** The fixed left-hand group: modifiers and Tab, which are never reordered away. */
    val modifierKeys: List<BarKey> = listOf(
        BarKey(CTRL, "ctrl", KeyKind.MODIFIER),
        BarKey(SHIFT, "shift", KeyKind.MODIFIER),
        BarKey(CAPS, "caps", KeyKind.MODIFIER),
        BarKey(TAB, "tab", KeyKind.ACTION, shiftedLabel = "untab"),
    )

    /**
     * Editing keys that stay one tap away.
     *
     * Indent, outdent, comment, duplicate and delete-line are all reachable through Tab,
     * Shift+Tab and the Ctrl set, but they were single taps before the modifiers existed
     * and there is no reason to make them cost two now. They are reorderable, so anyone
     * who prefers the modifier route can drag them out of the way.
     */
    private val editingKeys: List<BarKey> = listOf(
        BarKey("act.indent", "⇥|", KeyKind.ACTION),
        BarKey("act.dedent", "|⇤", KeyKind.ACTION),
        BarKey("act.comment", "#", KeyKind.ACTION),
        BarKey("act.left", "◀", KeyKind.ACTION),
        BarKey("act.right", "▶", KeyKind.ACTION),
        BarKey("act.up", "▲", KeyKind.ACTION),
        BarKey("act.down", "▼", KeyKind.ACTION),
        BarKey("act.home", "⇤", KeyKind.ACTION),
        BarKey("act.end", "⇥", KeyKind.ACTION),
        BarKey("act.dup", "dup", KeyKind.ACTION),
        BarKey("act.delline", "del ln", KeyKind.ACTION),
    )

    private val commonSymbols: List<BarKey> = listOf(
        // Pairs put the caret between the halves, the only useful place for it.
        BarKey("sym.paren", "( )", text = "()", caretOffset = 1),
        BarKey("sym.bracket", "[ ]", text = "[]", caretOffset = 1),
        BarKey("sym.brace", "{ }", text = "{}", caretOffset = 1),
        BarKey("sym.dquote", "\" \"", text = "\"\"", caretOffset = 1),
        BarKey("sym.squote", "' '", text = "''", caretOffset = 1),
        BarKey("sym.eq", "="), BarKey("sym.under", "_"), BarKey("sym.dot", "."),
        BarKey("sym.comma", ","), BarKey("sym.star", "*"), BarKey("sym.minus", "-"),
        BarKey("sym.plus", "+"), BarKey("sym.lt", "<"), BarKey("sym.gt", ">"),
        BarKey("sym.slash", "/"), BarKey("sym.pct", "%"), BarKey("sym.amp", "&"),
        BarKey("sym.pipe", "|"), BarKey("sym.bang", "!"),
    )

    /** The shortcut set the bar switches to while Ctrl is armed. */
    val ctrlKeys: List<BarKey> = listOf(
        BarKey("ctl.save", "save", KeyKind.ACTION),
        BarKey("ctl.undo", "undo", KeyKind.ACTION),
        BarKey("ctl.redo", "redo", KeyKind.ACTION),
        BarKey("ctl.all", "all", KeyKind.ACTION),
        BarKey("ctl.copy", "copy", KeyKind.ACTION),
        BarKey("ctl.cut", "cut", KeyKind.ACTION),
        BarKey("ctl.paste", "paste", KeyKind.ACTION),
        BarKey("ctl.dup", "dup", KeyKind.ACTION),
        BarKey("ctl.delline", "del ln", KeyKind.ACTION),
    )

    private fun languageKeys(language: Language): List<BarKey> = when (language) {
        Language.PYTHON -> listOf(
            BarKey("lang.colon", ":"),
            BarKey("lang.self", "self.", text = "self."),
        )
        Language.KOTLIN, Language.JAVA, Language.JS, Language.C_LIKE ->
            listOf(BarKey("lang.semi", ";"), BarKey("lang.arrow", "->", text = " -> "))
        Language.MARKDOWN -> listOf(
            BarKey("lang.hash", "#"),
            BarKey("lang.dash", "- ", text = "- "),
            BarKey("lang.tick", "` `", text = "``", caretOffset = 1),
            BarKey("lang.bold", "**", text = "****", caretOffset = 2),
            BarKey("lang.link", "[ ]", text = "[]()", caretOffset = 1),
        )
        else -> emptyList()
    }

    /** The default reorderable set for a language: language keys, editing keys, symbols. */
    fun defaultKeys(language: Language): List<BarKey> =
        languageKeys(language) + editingKeys + commonSymbols

    /**
     * Applies a saved order to the current key set.
     *
     * Saved ids that no longer exist are dropped and keys the save predates are appended,
     * so a layout survives an app update that changes the key set.
     */
    fun applyOrder(keys: List<BarKey>, order: List<String>): List<BarKey> {
        if (order.isEmpty()) return keys
        val byId = keys.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val missing = keys.filterNot { key -> order.contains(key.id) }
        return ordered + missing
    }

    /** Moves one key, shifting the rest along. Out-of-range indices leave the list alone. */
    fun reorder(keys: List<BarKey>, from: Int, to: Int): List<BarKey> {
        if (from !in keys.indices || to !in keys.indices || from == to) return keys
        return keys.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * What pressing [key] does, given the current modifiers.
     *
     * Returns the outcome and the modifiers that should follow it — one function so the
     * "did we remember to clear Shift?" question has exactly one answer.
     */
    fun press(
        key: BarKey,
        mods: Modifiers,
        language: Language,
    ): Pair<KeyOutcome, Modifiers> = when (key.id) {
        SHIFT -> KeyOutcome.None to mods.copy(shift = !mods.shift)
        CTRL -> KeyOutcome.None to mods.copy(ctrl = !mods.ctrl)
        CAPS -> KeyOutcome.None to mods.copy(caps = !mods.caps)

        TAB -> edit(mods) { if (mods.shift) SmartEdit.dedent(it) else SmartEdit.indent(it) }

        "act.comment", "lang.hash" ->
            if (key.id == "act.comment") edit(mods) { SmartEdit.toggleComment(it, language) }
            else insert(key, mods)

        "act.indent" -> edit(mods) { SmartEdit.indent(it) }
        "act.dedent" -> edit(mods) { SmartEdit.dedent(it) }
        "act.dup", "ctl.dup" -> edit(mods) { SmartEdit.duplicateLine(it) }
        "act.delline", "ctl.delline" -> edit(mods) { SmartEdit.deleteLine(it) }

        "act.left" -> edit(mods) { SmartEdit.moveCaret(it, -1, mods.shift) }
        "act.right" -> edit(mods) { SmartEdit.moveCaret(it, 1, mods.shift) }
        "act.up" -> edit(mods) { SmartEdit.moveCaretLine(it, -1, mods.shift) }
        "act.down" -> edit(mods) { SmartEdit.moveCaretLine(it, 1, mods.shift) }
        "act.home" -> edit(mods) { SmartEdit.toLineStart(it, mods.shift) }
        "act.end" -> edit(mods) { SmartEdit.toLineEnd(it, mods.shift) }

        "ctl.save" -> command(EditorCommand.SAVE, mods)
        "ctl.undo" -> command(EditorCommand.UNDO, mods)
        "ctl.redo" -> command(EditorCommand.REDO, mods)
        "ctl.copy" -> command(EditorCommand.COPY, mods)
        "ctl.cut" -> command(EditorCommand.CUT, mods)
        "ctl.paste" -> command(EditorCommand.PASTE, mods)
        "ctl.all" -> edit(mods) { SmartEdit.selectAll(it) }

        else -> insert(key, mods)
    }

    private fun edit(mods: Modifiers, op: (TextFieldValue) -> TextFieldValue) =
        KeyOutcome.Edit(op) as KeyOutcome to mods.afterOrdinaryKey()

    private fun command(command: EditorCommand, mods: Modifiers) =
        KeyOutcome.Command(command) as KeyOutcome to mods.afterOrdinaryKey()

    private fun insert(key: BarKey, mods: Modifiers): Pair<KeyOutcome, Modifiers> {
        // Caps and Shift both uppercase inserted text; Caps stays on afterwards.
        val upper = mods.caps || mods.shift
        val body = if (upper) key.text.uppercase() else key.text
        val op: (TextFieldValue) -> TextFieldValue = {
            SmartEdit.insert(it, body, key.caretOffset.coerceAtMost(body.length))
        }
        return KeyOutcome.Edit(op) to mods.afterOrdinaryKey()
    }

    /** The label a key shows right now. */
    fun labelFor(key: BarKey, mods: Modifiers): String = when {
        mods.shift && key.shiftedLabel != null -> key.shiftedLabel
        (mods.caps || mods.shift) && key.kind == KeyKind.INSERT -> key.label.uppercase()
        else -> key.label
    }
}
