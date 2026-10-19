package com.spisokryadom.app.util

import android.content.Context
import android.content.SharedPreferences

object ProductEditingPreferences {
    const val KEY = "autosave_product_cards"
    fun preferences(context: Context): SharedPreferences = context.getSharedPreferences("shopping_list_prefs", 0)
    fun enabled(preferences: SharedPreferences?): Boolean = preferences?.getBoolean(KEY, true) ?: true
}
