package com.spisokryadom.app.widget

import android.content.SharedPreferences
import com.spisokryadom.app.ui.shoppinglist.ViewMode

/** Shared by every home-screen instance, independent of the application's current view. */
data class WidgetSettings(
    val mode: ViewMode = ViewMode.BY_SHOPS,
    val showDepartments: Boolean = true,
    val textSize: Int = 16,
    val showBought: Boolean = true,
    val showBoughtToggle: Boolean = true,
    val showShopBackground: Boolean = false,
    val showProductBackground: Boolean = false
) {
    fun save(prefs: SharedPreferences) {
        prefs.edit().putString(MODE, mode.name).putBoolean(DEPARTMENTS, showDepartments)
            .putInt(TEXT_SIZE, textSize.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
            .putBoolean(SHOW_BOUGHT, showBought).putBoolean(BOUGHT_TOGGLE, showBoughtToggle)
            .putBoolean(SHOP_BACKGROUND, showShopBackground).putBoolean(PRODUCT_BACKGROUND, showProductBackground)
            .apply()
    }

    companion object {
        const val PREFS = "shopping_list_prefs"
        const val MODE = "widget_view_mode"
        const val DEPARTMENTS = "widget_show_departments"
        const val TEXT_SIZE = "widget_text_size"
        const val SHOW_BOUGHT = "widget_show_bought"
        const val BOUGHT_TOGGLE = "widget_bought_toggle"
        const val SHOP_BACKGROUND = "widget_shop_background"
        const val PRODUCT_BACKGROUND = "widget_product_background"
        val booleanKeys = setOf(DEPARTMENTS, SHOW_BOUGHT, BOUGHT_TOGGLE, SHOP_BACKGROUND, PRODUCT_BACKGROUND)
        val keys = booleanKeys + setOf(MODE, TEXT_SIZE)
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 22

        fun load(prefs: SharedPreferences): WidgetSettings {
            val values = prefs.all
            val mode = (values[MODE] as? String)?.let { saved -> ViewMode.entries.find { it.name == saved } }
                ?: ViewMode.BY_SHOPS
            val size = (values[TEXT_SIZE] as? Number)?.toLong()
                ?.coerceIn(MIN_TEXT_SIZE.toLong(), MAX_TEXT_SIZE.toLong())?.toInt() ?: 16
            return WidgetSettings(mode, values[DEPARTMENTS] as? Boolean ?: true, size,
                values[SHOW_BOUGHT] as? Boolean ?: true, values[BOUGHT_TOGGLE] as? Boolean ?: true,
                values[SHOP_BACKGROUND] as? Boolean ?: false, values[PRODUCT_BACKGROUND] as? Boolean ?: false)
        }
    }
}
