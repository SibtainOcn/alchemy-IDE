package dev.hazel.code

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dev.hazel.code.data.Language
import dev.hazel.code.ui.editor.BarKey
import dev.hazel.code.ui.editor.EditorCommand
import dev.hazel.code.ui.editor.KeyBarModel
import dev.hazel.code.ui.editor.KeyOutcome
import dev.hazel.code.ui.editor.Modifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Modifier state is the classic thing that breaks quietly — a Shift that never clears, a
 * Ctrl that stays armed after a command — so the latch rules are pinned here rather than
 * left to be discovered by a stuck keyboard.
 */
class KeyBarModelTest {

    private val none = Modifiers()

    private fun key(id: String): BarKey =
        (KeyBarModel.modifierKeys + KeyBarModel.ctrlKeys + KeyBarModel.defaultKeys(Language.PYTHON))
            .first { it.id == id }

    private fun press(id: String, mods: Modifiers = none, lang: Language = Language.PYTHON) =
        KeyBarModel.press(key(id), mods, lang)

    private fun applied(id: String, on: TextFieldValue, mods: Modifiers = none): TextFieldValue {
        val (outcome, _) = press(id, mods)
        return (outcome as KeyOutcome.Edit).op(on)
    }

    @Test
    fun `shift arms and disarms without editing anything`() {
        val (outcome, armed) = press(KeyBarModel.SHIFT)
        assertTrue(outcome is KeyOutcome.None)
        assertTrue(armed.shift)
        assertFalse(KeyBarModel.press(key(KeyBarModel.SHIFT), armed, Language.PYTHON).second.shift)
    }

    @Test
    fun `shift is spent by the next ordinary key`() {
        val armed = none.copy(shift = true)
        val (_, after) = press("sym.eq", armed)
        assertFalse("Shift must not stay armed after a key", after.shift)
    }

    @Test
    fun `caps survives the next key, because that is the point of a lock`() {
        val locked = none.copy(caps = true)
        val (_, after) = press("sym.eq", locked)
        assertTrue(after.caps)
    }

    @Test
    fun `ctrl clears once a shortcut has run`() {
        val armed = none.copy(ctrl = true)
        val (outcome, after) = press("ctl.save", armed)
        assertEquals(EditorCommand.SAVE, (outcome as KeyOutcome.Command).command)
        assertFalse(after.ctrl)
    }

    @Test
    fun `tab indents, and shift-tab outdents`() {
        val block = TextFieldValue("a\nb", TextRange(0, 3))
        assertEquals("    a\n    b", applied(KeyBarModel.TAB, block).text)
        val indented = TextFieldValue("    a\n    b", TextRange(0, 11))
        assertEquals("a\nb", applied(KeyBarModel.TAB, indented, none.copy(shift = true)).text)
    }

    @Test
    fun `caps uppercases inserted text`() {
        val v = TextFieldValue("", TextRange(0))
        assertEquals("self.", applied("lang.self", v).text)
        assertEquals("SELF.", applied("lang.self", v, none.copy(caps = true)).text)
    }

    @Test
    fun `arrows move the caret and shift-arrows extend the selection`() {
        val v = TextFieldValue("abcdef", TextRange(3))
        assertEquals(TextRange(4), applied("act.right", v).selection)
        assertEquals(TextRange(2), applied("act.left", v).selection)
        assertEquals(TextRange(3, 4), applied("act.right", v, none.copy(shift = true)).selection)
    }

    @Test
    fun `vertical movement keeps the column`() {
        val v = TextFieldValue("hello\nworld", TextRange(3))
        assertEquals(TextRange(9), applied("act.down", v).selection)
        val back = TextFieldValue("hello\nworld", TextRange(9))
        assertEquals(TextRange(3), applied("act.up", back).selection)
    }

    @Test
    fun `vertical movement clamps onto a shorter line`() {
        val v = TextFieldValue("longer line\nab", TextRange(9))
        assertEquals("Should land at the end of the short line", TextRange(14), applied("act.down", v).selection)
    }

    @Test
    fun `home toggles between the first word and column zero`() {
        val v = TextFieldValue("    indented", TextRange(8))
        val atWord = applied("act.home", v)
        assertEquals(TextRange(4), atWord.selection)
        assertEquals(TextRange(0), applied("act.home", atWord).selection)
    }

    @Test
    fun `every default key produces an outcome`() {
        // Guards against adding a key id to the set and forgetting to wire it up: an
        // unhandled id silently falls through to "insert its own label".
        val v = TextFieldValue("x = 1\n", TextRange(0))
        Language.entries.forEach { lang ->
            KeyBarModel.defaultKeys(lang).forEach { k ->
                val (outcome, _) = KeyBarModel.press(k, none, lang)
                assertNotNull("No outcome for ${k.id}", outcome)
                if (outcome is KeyOutcome.Edit) assertNotNull(outcome.op(v))
            }
        }
    }

    // ---- Reordering ----

    @Test
    fun `reorder moves one key and shifts the rest`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        val moved = KeyBarModel.reorder(keys, 0, 2)
        assertEquals(keys[1].id, moved[0].id)
        assertEquals(keys[2].id, moved[1].id)
        assertEquals(keys[0].id, moved[2].id)
        assertEquals(keys.size, moved.size)
    }

    @Test
    fun `reorder ignores out-of-range indices instead of throwing`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        assertEquals(keys, KeyBarModel.reorder(keys, -1, 2))
        assertEquals(keys, KeyBarModel.reorder(keys, 0, keys.size))
        assertEquals(keys, KeyBarModel.reorder(keys, 1, 1))
    }

    @Test
    fun `a saved order is applied`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        val reversed = keys.map { it.id }.reversed()
        assertEquals(reversed, KeyBarModel.applyOrder(keys, reversed).map { it.id })
    }

    @Test
    fun `a saved order survives the key set changing`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        // An order saved by an older version: two ids that no longer exist, and missing
        // every key added since.
        val stale = listOf("sym.eq", "gone.forever", "sym.dot")
        val result = KeyBarModel.applyOrder(keys, stale)

        assertEquals("sym.eq", result[0].id)
        assertEquals("sym.dot", result[1].id)
        assertEquals("Dropped keys must not survive", 0, result.count { it.id == "gone.forever" })
        assertEquals("Every current key must still be present", keys.size, result.size)
        assertEquals(keys.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `an empty order leaves the defaults alone`() {
        val keys = KeyBarModel.defaultKeys(Language.MARKDOWN)
        assertEquals(keys, KeyBarModel.applyOrder(keys, emptyList()))
    }

    @Test
    fun `labels follow the modifiers`() {
        val tab = key(KeyBarModel.TAB)
        assertEquals("tab", KeyBarModel.labelFor(tab, none))
        assertEquals("untab", KeyBarModel.labelFor(tab, none.copy(shift = true)))
        val self = key("lang.self")
        assertEquals("SELF.", KeyBarModel.labelFor(self, none.copy(caps = true)))
    }

    @Test
    fun `key ids are unique within a language`() {
        Language.entries.forEach { lang ->
            val ids = (KeyBarModel.defaultKeys(lang) + KeyBarModel.modifierKeys).map { it.id }
            assertEquals("Duplicate key id in $lang", ids.size, ids.toSet().size)
        }
    }
}
