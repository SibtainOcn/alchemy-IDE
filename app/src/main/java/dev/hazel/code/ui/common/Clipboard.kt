package dev.hazel.code.ui.common

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/**
 * Copy-to-clipboard as a plain callback.
 *
 * The current Compose clipboard API is suspending, which does not fit a click handler, so
 * the scope is captured once here and every call site stays a one-liner.
 */
/**
 * Reads the clipboard and hands the text to a callback.
 *
 * Same shape as the copy helper and for the same reason: the read is suspending, and a
 * key press is not.
 */
@Composable
fun rememberPasteFromClipboard(): ((String) -> Unit) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        fun(onText: (String) -> Unit) {
            scope.launch {
                // Plain text only: coercing other item types needs a Context and would
                // paste a URI string into source code, which is never what was meant.
                // The read crosses a process boundary, so it can fail for reasons that
                // have nothing to do with us.
                val text = runCatching { clipboard.getClipEntry() }.getOrNull()
                    ?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.text
                    ?.toString()
                if (!text.isNullOrEmpty()) onText(text)
            }
        }
    }
}

@Composable
fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        fun(text: String) {
            scope.launch {
                runCatching {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hazel", text)))
                }
            }
        }
    }
}
