package com.sibtainocn.alchemy.data

import android.content.Context
import androidx.core.content.edit

/** The sticky UI choices. SharedPreferences is enough here; no DataStore dependency. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("alchemy", Context.MODE_PRIVATE)

    var lastDir: String?
        get() = sp.getString("last_dir", null)
        set(v) = sp.edit { putString("last_dir", v) }

    var showHidden: Boolean
        get() = sp.getBoolean("show_hidden", false)
        set(v) = sp.edit { putBoolean("show_hidden", v) }

    var sortBy: SortBy
        get() = runCatching { SortBy.valueOf(sp.getString("sort_by", null) ?: "") }
            .getOrDefault(SortBy.MODIFIED)
        set(v) = sp.edit { putString("sort_by", v.name) }

    var sortDescending: Boolean
        get() = sp.getBoolean("sort_desc", true)
        set(v) = sp.edit { putBoolean("sort_desc", v) }

    /**
     * Off by default. Code is written to a width and read at one; wrapping a long line
     * into three re-flows the shape the indentation was carrying, and the editor scrolls
     * sideways perfectly well for the lines that overrun.
     */
    var wordWrap: Boolean
        get() = sp.getBoolean("word_wrap", false)
        set(v) = sp.edit { putBoolean("word_wrap", v) }

    var autoPair: Boolean
        get() = sp.getBoolean("auto_pair", true)
        set(v) = sp.edit { putBoolean("auto_pair", v) }

    var fontSizeSp: Int
        get() = sp.getInt("font_sp", 15)
        set(v) = sp.edit { putInt("font_sp", v) }

    /**
     * Markdown preview zoom, as a percentage. Kept apart from [fontSizeSp]: the size that
     * suits editing source is not the size that suits reading a rendered page.
     */
    var previewZoomPct: Int
        get() = sp.getInt("preview_zoom", 100)
        set(v) = sp.edit { putInt("preview_zoom", v) }

    var lineNumbers: Boolean
        get() = sp.getBoolean("line_numbers", true)
        set(v) = sp.edit { putBoolean("line_numbers", v) }

    /** Text size in the terminal sheet, kept apart from the editor's own. */
    var terminalFontSp: Int
        get() = sp.getInt("terminal_font_sp", 12)
        set(v) = sp.edit { putInt("terminal_font_sp", v) }

    /** Whether each command reports how long it took. */
    var terminalTimings: Boolean
        get() = sp.getBoolean("terminal_timings", true)
        set(v) = sp.edit { putBoolean("terminal_timings", v) }

    /**
     * Whether the runner setup has been offered once already.
     *
     * Offered, not completed: someone who declined it should not be asked again every
     * time they open a file, and someone who finished it has nothing left to be asked.
     */
    var setupOffered: Boolean
        get() = sp.getBoolean("setup_offered", false)
        set(v) = sp.edit { putBoolean("setup_offered", v) }

    /**
     * Absolute paths the user has pinned to the top of their folder.
     *
     * The filesystem has nowhere to record this, so the app keeps its own note of it.
     * Paths rather than anything cleverer: it survives a restart, costs one read at
     * startup, and a pin whose file has gone simply never matches anything again.
     *
     * SharedPreferences hands back the same mutable set it is holding, and editing that
     * set in place corrupts what is written next, so it is copied on the way out and a
     * fresh one always goes in.
     */
    var pinned: Set<String>
        get() = sp.getStringSet("pinned", null)?.toSet().orEmpty()
        set(v) = sp.edit { putStringSet("pinned", HashSet(v)) }

    /**
     * The user's dragged key-bar order, as stable key ids.
     *
     * Stored per language, because the order that suits Python is not the order that
     * suits Markdown. An empty list means "never reordered", which is what tells the bar
     * to use its defaults.
     */
    fun keyOrder(language: String): List<String> =
        sp.getString("key_order_$language", null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun setKeyOrder(language: String, order: List<String>) =
        sp.edit { putString("key_order_$language", order.joinToString(",")) }
}
