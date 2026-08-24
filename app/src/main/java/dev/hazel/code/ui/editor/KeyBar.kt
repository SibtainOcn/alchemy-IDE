package dev.hazel.code.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hazel.code.data.Language
import dev.hazel.code.ui.common.Ico
import dev.hazel.code.ui.common.Motion
import dev.hazel.code.ui.theme.CodeFont
import dev.hazel.code.ui.theme.Hairline
import dev.hazel.code.ui.theme.InkRaised
import dev.hazel.code.ui.theme.TextHigh
import dev.hazel.code.ui.theme.TextMid

/**
 * The strip above the keyboard.
 *
 * A phone keyboard buries every character that matters in code behind two taps, so these
 * are the characters and the block operations, in reach, ordered by how often they get
 * used rather than by ASCII. The set shifts with the language: Python gets its colon and
 * `self.`, brace languages get their braces and semicolon.
 */
@Composable
fun KeyBar(
    language: Language,
    onOp: ((TextFieldValue) -> TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val symbols = remember(language) { symbolsFor(language) }

    Row(
        modifier
            .fillMaxWidth()
            .background(InkRaised)
            .horizontalScroll(scroll)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyIcon(Ico.Indent, "Indent") { onOp(SmartEdit::indent) }
        KeyIcon(Ico.Dedent, "Outdent") { onOp(SmartEdit::dedent) }
        KeyIcon(Ico.Hash, "Toggle comment") { onOp { SmartEdit.toggleComment(it, language) } }

        Divider()

        symbols.forEach { key ->
            KeyCap(key.label) { onOp { v -> SmartEdit.insert(v, key.text, key.caret) } }
        }

        Divider()

        KeyIcon(Ico.Copy, "Duplicate line") { onOp(SmartEdit::duplicateLine) }
        KeyIcon(Ico.Trash, "Delete line") { onOp(SmartEdit::deleteLine) }
    }
}

private data class Key(val label: String, val text: String = label, val caret: Int = text.length)

private fun symbolsFor(language: Language): List<Key> {
    val common = listOf(
        // Pairs land with the caret between the halves, which is the only useful place
        // for it.
        Key("( )", "()", 1),
        Key("[ ]", "[]", 1),
        Key("{ }", "{}", 1),
        Key("\" \"", "\"\"", 1),
        Key("' '", "''", 1),
        Key("="), Key("_"), Key("."), Key(","), Key("*"), Key("-"), Key("+"),
        Key("<"), Key(">"), Key("/"), Key("%"), Key("&"), Key("|"), Key("!"),
    )
    return when (language) {
        Language.PYTHON -> listOf(
            Key(":"),
            Key("self.", "self."),
            Key("→", "    "),
        ) + common
        Language.KOTLIN, Language.JAVA, Language.JS, Language.C_LIKE -> listOf(
            Key(";"), Key("→", "    "),
        ) + common
        Language.MARKDOWN -> listOf(
            Key("#"), Key("- "), Key("` `", "``", 1), Key("**", "****", 2),
            Key("[ ]", "[]()", 1),
        ) + common
        else -> listOf(Key("→", "    ")) + common
    }
}

@Composable
private fun KeyCap(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.snappy(), label = "key")

    Box(
        Modifier
            .scale(scale)
            .height(38.dp)
            .defaultMinSize(minWidth = 40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = CodeFont,
            fontSize = 14.sp,
            color = if (pressed) MaterialTheme.colorScheme.primary else TextHigh,
        )
    }
}

@Composable
private fun KeyIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, Motion.snappy(), label = "keyicon")

    Box(
        Modifier
            .scale(scale)
            .size(width = 42.dp, height = 38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (pressed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(18.dp), tint = if (pressed) MaterialTheme.colorScheme.primary else TextMid)
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(22.dp)
            .background(Hairline)
    )
}
