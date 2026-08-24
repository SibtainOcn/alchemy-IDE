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
@Composable
fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) {
        fun(text: String) {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hazel", text)))
            }
        }
    }
}
