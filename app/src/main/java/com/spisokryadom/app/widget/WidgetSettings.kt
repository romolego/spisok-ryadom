package com.spisokryadom.app.widget

import android.content.SharedPreferences
import com.spisokryadom.app.ui.shoppinglist.ViewMode

/** Shared by every home-screen instance, independent of the application's current view. */
data class WidgetSettings(
    val mode: ViewMode = ViewMode.BY_SHOPS,
    val showDepartments: Boolean = true,
    val textSize: Int = 16
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit().putString(MODE, mode.name).putBoolean(DEPARTMENTS, showDepartments)
            .putInt(TEXT_SIZE, textSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)).apply()
    }

    companion object {
        const val PREFS = "shopping_list_prefs"
        const val MODE = "widget_view_mode"
        const val DEPARTMENTS = "widget_show_departments"
        const val TEXT_SIZE = "widget_text_size"
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 22

        fun load(prefs: SharedPreferences): WidgetSettings {
            val values = prefs.all
            val mode = (values[MODE] as? String)?.let { saved -> ViewMode.entries.find { it.name == saved } }
                ?: ViewMode.BY_SHOPS
            val size = (values[TEXT_SIZE] as? Number)?.toLong()
                ?.coerceIn(MIN_TEXT_SIZE.toLong(), MAX_TEXT_SIZE.toLong())?.toInt() ?: 16
            return WidgetSettings(mode, values[DEPARTMENTS] as? Boolean ?: true, size)
        }
    }
}
