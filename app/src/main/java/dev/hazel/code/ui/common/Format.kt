package dev.hazel.code.ui.common

import dev.hazel.code.data.Entry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {

    private val dayMonthYear = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Today collapses to a time, everything else to a date — the way a file list reads best. */
    fun date(millis: Long): String {
        if (millis <= 0L) return "--"
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val now = Calendar.getInstance()
        val sameDay = then.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            then.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) clock.format(Date(millis)) else dayMonthYear.format(Date(millis))
    }

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun subtitle(entry: Entry): String = when {
        !entry.isDir -> size(entry.sizeBytes)
        entry.childCount < 0 -> "Locked"
        entry.childCount == 0 -> "Empty"
        entry.childCount == 1 -> "1 item"
        else -> "${entry.childCount} items"
    }
}
