package dev.hazel.code.ui.editor

import androidx.compose.ui.text.input.TextFieldValue
import dev.hazel.code.data.Language

/**
 * Everything the key bar decides, kept out of the composable.
 *
 * A phone keyboard has no Ctrl, no Tab and no arrows, so the bar supplies those. It does
 * not supply Shift or Caps: the IME already has both, and duplicating them only created a
 * second, competing idea of what "shifted" meant.
 *
 * Ctrl *latches* - one tap arms it for the next key, which is the only workable gesture
 * when you cannot hold two keys at once. Arming it swaps the whole bar for the shortcut
 * set, so a key never means two things at the same time.
 *
 * Every key is reorderable, Ctrl and Tab included. Nothing is pinned.
 */

/** What a key does when it is pressed. */
sealed interface KeyOutcome {
    /** A pure text edit the view model can apply and undo as one step. */
    data class Edit(val op: (TextFieldValue) -> TextFieldValue) : KeyOutcome

    /** Something only the screen can do - save, clipboard, undo history. */
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

    /** Latches; changes what the other keys do. */
    MODIFIER,
}

/**
 * One key. [id] is stable and is what gets persisted in the user's chosen order - labels
 * and behaviour can change between versions without scrambling a saved layout.
 */
data class BarKey(
    val id: String,
    val label: String,
    val kind: KeyKind = KeyKind.INSERT,
    /** Text inserted for [KeyKind.INSERT]; caret lands [caretOffset] into it. */
    val text: String = label,
    val caretOffset: Int = text.length,
)

/** The modifier state. One latch, held as a value so updates are atomic. */
data class Modifiers(val ctrl: Boolean = false) {
    /** Ctrl is spent by the next key that does something. */
    fun afterOrdinaryKey(): Modifiers = Modifiers(ctrl = false)
}

object KeyBarModel {

    const val CTRL = "mod.ctrl"
    const val TAB = "act.tab"

    /**
     * Editing keys, all one tap away and all reorderable.
     *
     * Ctrl and Tab sit here rather than in a pinned group: pinning them cost the width of
     * four keys permanently and stopped them being moved somewhere the thumb prefers.
     */
    private val editingKeys: List<BarKey> = listOf(
        BarKey(CTRL, "ctrl", KeyKind.MODIFIER),
        BarKey(TAB, "tab", KeyKind.ACTION),
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

    /** The full reorderable set for a language. */
    fun defaultKeys(language: Language): List<BarKey> =
        editingKeys + languageKeys(language) + commonSymbols

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

    /** Where a dragged key lands, and how much of the drag is left once it gets there. */
    data class Drop(val index: Int, val residual: Float)

    /**
     * Resolves a drag into a landing slot.
     *
     * [widths] are the laid-out widths of the keys in their current order, [from] is the
     * slot the dragged key holds now, and [dx] is how far it has been pulled from that
     * slot. A key takes the next slot once it has covered half of it, and that slot's
     * width then comes off the drag - so one long pull crosses every key it reaches
     * instead of stopping after the first.
     *
     * [Drop.residual] is what is left over afterwards, and is what keeps the key under
     * the finger rather than snapping it to the slot it has just taken.
     */
    fun dropTarget(widths: List<Float>, from: Int, dx: Float, gap: Float): Drop {
        if (from !in widths.indices) return Drop(from, dx)
        var index = from
        var left = dx
        // One direction only, chosen by the way the finger went. Crossing a slot leaves a
        // remainder of up to half its width pointing the other way, and a slot narrower
        // than that on the far side would otherwise read as a crossing straight back.
        if (dx > 0f) {
            while (true) {
                val step = (widths.getOrNull(index + 1) ?: break) + gap
                if (step <= 0f || left <= step / 2f) break
                left -= step
                index++
            }
        } else {
            while (true) {
                val step = (widths.getOrNull(index - 1) ?: break) + gap
                if (step <= 0f || left >= -step / 2f) break
                left += step
                index--
            }
        }
        return Drop(index, left)
    }

    /**
     * What pressing [key] does, given the current modifiers.
     *
     * Returns the outcome and the modifiers that should follow it - one function, so the
     * "did we remember to clear Ctrl?" question has exactly one answer.
     */
    fun press(
        key: BarKey,
        mods: Modifiers,
        language: Language,
    ): Pair<KeyOutcome, Modifiers> = when (key.id) {
        CTRL -> KeyOutcome.None to Modifiers(ctrl = !mods.ctrl)

        TAB, "act.indent" -> edit(mods) { SmartEdit.indent(it) }
        "act.dedent" -> edit(mods) { SmartEdit.dedent(it) }
        "act.comment" -> edit(mods) { SmartEdit.toggleComment(it, language) }
        "act.dup", "ctl.dup" -> edit(mods) { SmartEdit.duplicateLine(it) }
        "act.delline", "ctl.delline" -> edit(mods) { SmartEdit.deleteLine(it) }

        "act.left" -> edit(mods) { SmartEdit.moveCaret(it, -1, extend = false) }
        "act.right" -> edit(mods) { SmartEdit.moveCaret(it, 1, extend = false) }
        "act.up" -> edit(mods) { SmartEdit.moveCaretLine(it, -1, extend = false) }
        "act.down" -> edit(mods) { SmartEdit.moveCaretLine(it, 1, extend = false) }
        "act.home" -> edit(mods) { SmartEdit.toLineStart(it, extend = false) }
        "act.end" -> edit(mods) { SmartEdit.toLineEnd(it, extend = false) }

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
        val op: (TextFieldValue) -> TextFieldValue = {
            SmartEdit.insert(it, key.text, key.caretOffset.coerceAtMost(key.text.length))
        }
        return KeyOutcome.Edit(op) to mods.afterOrdinaryKey()
    }
}
