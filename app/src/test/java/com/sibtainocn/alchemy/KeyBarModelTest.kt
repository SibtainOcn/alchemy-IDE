package com.sibtainocn.alchemy

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.sibtainocn.alchemy.data.Language
import com.sibtainocn.alchemy.ui.editor.BarKey
import com.sibtainocn.alchemy.ui.editor.EditorCommand
import com.sibtainocn.alchemy.ui.editor.KeyBarModel
import com.sibtainocn.alchemy.ui.editor.KeyOutcome
import com.sibtainocn.alchemy.ui.editor.Modifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ctrl latch is the classic thing that breaks quietly - a modifier that stays armed
 * after the key it was meant for - so the rule is pinned here rather than left to be
 * discovered by a stuck keyboard.
 */
class KeyBarModelTest {

    private val none = Modifiers()

    private fun key(id: String): BarKey =
        (KeyBarModel.ctrlKeys + KeyBarModel.defaultKeys(Language.PYTHON)).first { it.id == id }

    private fun press(id: String, mods: Modifiers = none, lang: Language = Language.PYTHON) =
        KeyBarModel.press(key(id), mods, lang)

    private fun applied(id: String, on: TextFieldValue, mods: Modifiers = none): TextFieldValue {
        val (outcome, _) = press(id, mods)
        return (outcome as KeyOutcome.Edit).op(on)
    }

    @Test
    fun `ctrl arms and disarms without editing anything`() {
        val (outcome, armed) = press(KeyBarModel.CTRL)
        assertTrue(outcome is KeyOutcome.None)
        assertTrue(armed.ctrl)
        assertFalse(press(KeyBarModel.CTRL, armed).second.ctrl)
    }

    @Test
    fun `ctrl clears once a shortcut has run`() {
        val armed = Modifiers(ctrl = true)
        val (outcome, after) = press("ctl.save", armed)
        assertEquals(EditorCommand.SAVE, (outcome as KeyOutcome.Command).command)
        assertFalse(after.ctrl)
    }

    @Test
    fun `every ctrl key resolves to a command or an edit, and clears the latch`() {
        val armed = Modifiers(ctrl = true)
        KeyBarModel.ctrlKeys.forEach { k ->
            val (outcome, after) = KeyBarModel.press(k, armed, Language.PYTHON)
            assertTrue("No outcome for ${k.id}", outcome !is KeyOutcome.None)
            assertFalse("Ctrl still armed after ${k.id}", after.ctrl)
        }
    }

    @Test
    fun `ctrl and tab are ordinary reorderable keys, not a pinned group`() {
        val ids = KeyBarModel.defaultKeys(Language.PYTHON).map { it.id }
        assertTrue("ctrl must live in the reorderable set", ids.contains(KeyBarModel.CTRL))
        assertTrue("tab must live in the reorderable set", ids.contains(KeyBarModel.TAB))
    }

    @Test
    fun `tab indents`() {
        val block = TextFieldValue("a\nb", TextRange(0, 3))
        assertEquals("    a\n    b", applied(KeyBarModel.TAB, block).text)
    }

    @Test
    fun `dedent has its own key now that shift-tab is gone`() {
        val indented = TextFieldValue("    a\n    b", TextRange(0, 11))
        assertEquals("a\nb", applied("act.dedent", indented).text)
    }

    @Test
    fun `insert keys insert their text verbatim`() {
        val v = TextFieldValue("", TextRange(0))
        assertEquals("self.", applied("lang.self", v).text)
        assertEquals("()", applied("sym.paren", v).text)
        assertEquals(TextRange(1), applied("sym.paren", v).selection)
    }

    @Test
    fun `arrows move the caret`() {
        val v = TextFieldValue("abcdef", TextRange(3))
        assertEquals(TextRange(4), applied("act.right", v).selection)
        assertEquals(TextRange(2), applied("act.left", v).selection)
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
    fun `ctrl can be dragged anywhere, including last`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        val moved = KeyBarModel.reorder(keys, keys.indexOfFirst { it.id == KeyBarModel.CTRL }, keys.lastIndex)
        assertEquals(KeyBarModel.CTRL, moved.last().id)
        assertEquals(keys.size, moved.size)
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
        // An order saved by an older version: ids that no longer exist (Shift and Caps
        // were removed), and missing every key added since.
        val stale = listOf("sym.eq", "mod.shift", "mod.caps", "sym.dot")
        val result = KeyBarModel.applyOrder(keys, stale)

        assertEquals("sym.eq", result[0].id)
        assertEquals("sym.dot", result[1].id)
        assertEquals("Removed keys must not come back", 0, result.count { it.id.startsWith("mod.shift") })
        assertEquals("Every current key must still be present", keys.size, result.size)
        assertEquals(keys.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `an empty order leaves the defaults alone`() {
        val keys = KeyBarModel.defaultKeys(Language.MARKDOWN)
        assertEquals(keys, KeyBarModel.applyOrder(keys, emptyList()))
    }

    @Test
    fun `key ids are unique within a language`() {
        Language.entries.forEach { lang ->
            val ids = KeyBarModel.defaultKeys(lang).map { it.id }
            assertEquals("Duplicate key id in $lang", ids.size, ids.toSet().size)
        }
    }

    // ---- Dragging a key ----

    /** Even slots: a 40 px key and a 6 px gap, so every crossing costs 46 px. */
    private val evenWidths = List(10) { 40f }
    private val gap = 6f

    @Test
    fun `a short pull does not move a key at all`() {
        val drop = KeyBarModel.dropTarget(evenWidths, from = 3, dx = 20f, gap = gap)
        assertEquals(3, drop.index)
        assertEquals(20f, drop.residual, 0.01f)
    }

    @Test
    fun `one long pull crosses every key it reaches, not just the first`() {
        // This is the bug the drag had: a swap per gesture rather than a drop anywhere.
        val drop = KeyBarModel.dropTarget(evenWidths, from = 0, dx = 46f * 5, gap = gap)
        assertEquals(5, drop.index)
    }

    @Test
    fun `a pull backwards crosses just as far`() {
        val drop = KeyBarModel.dropTarget(evenWidths, from = 8, dx = -46f * 4, gap = gap)
        assertEquals(4, drop.index)
    }

    @Test
    fun `the leftover keeps the key under the finger rather than under its slot`() {
        // Half a slot past the third crossing: the key has taken slot 3 and is still
        // holding a slot's half-width of travel, which is where the finger is.
        val drop = KeyBarModel.dropTarget(evenWidths, from = 0, dx = 46f * 3 + 20f, gap = gap)
        assertEquals(3, drop.index)
        assertEquals(20f, drop.residual, 0.01f)
    }

    @Test
    fun `a key cannot be dragged off either end`() {
        assertEquals(0, KeyBarModel.dropTarget(evenWidths, 0, -5000f, gap).index)
        assertEquals(
            evenWidths.lastIndex,
            KeyBarModel.dropTarget(evenWidths, evenWidths.lastIndex, 5000f, gap).index,
        )
    }

    @Test
    fun `uneven keys are crossed by their own widths`() {
        // The bar is a mix of 'ctrl' and '#'. A drag that clears a wide key must not be
        // measured against a narrow one, or the key lands short of the finger.
        val widths = listOf(30f, 120f, 30f, 30f)
        assertEquals(0, KeyBarModel.dropTarget(widths, 0, 40f, gap).index)
        assertEquals(1, KeyBarModel.dropTarget(widths, 0, 80f, gap).index)
        assertEquals(2, KeyBarModel.dropTarget(widths, 0, 150f, gap).index)
    }

    @Test
    fun `an unmeasured row leaves the order alone`() {
        // Widths arrive a frame after the first layout; until then nothing should move.
        assertEquals(2, KeyBarModel.dropTarget(List(5) { 0f }, 2, 400f, gap = 0f).index)
    }

    @Test
    fun `reorder moves a key without dropping or duplicating any other`() {
        val keys = KeyBarModel.defaultKeys(Language.PYTHON)
        val moved = KeyBarModel.reorder(keys, 0, keys.lastIndex)
        assertEquals(keys.size, moved.size)
        assertEquals(keys.toSet(), moved.toSet())
        assertEquals(keys[0], moved.last())
    }
}
