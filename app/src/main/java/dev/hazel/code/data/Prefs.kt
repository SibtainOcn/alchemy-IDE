package dev.hazel.code.data

import android.content.Context
import androidx.core.content.edit

/** The sticky UI choices. SharedPreferences is enough here; no DataStore dependency. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("hazel", Context.MODE_PRIVATE)

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

    var wordWrap: Boolean
        get() = sp.getBoolean("word_wrap", true)
        set(v) = sp.edit { putBoolean("word_wrap", v) }

    var autoPair: Boolean
        get() = sp.getBoolean("auto_pair", true)
        set(v) = sp.edit { putBoolean("auto_pair", v) }

    var fontSizeSp: Int
        get() = sp.getInt("font_sp", 15)
        set(v) = sp.edit { putInt("font_sp", v) }

    var lineNumbers: Boolean
        get() = sp.getBoolean("line_numbers", true)
        set(v) = sp.edit { putBoolean("line_numbers", v) }

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
