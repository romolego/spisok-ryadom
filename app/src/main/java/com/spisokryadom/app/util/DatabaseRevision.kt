package com.spisokryadom.app.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Local database identity; deliberately separate from the transferable preferences. */
object DatabaseRevision {
    private const val PREFERENCES = "local_database_revision"
    private const val KEY = "revision"
    private val lock = Any()

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun current(context: Context): String = synchronized(lock) {
        val preferences = preferences(context)
        preferences.getString(KEY, null) ?: UUID.randomUUID().toString().also { revision ->
            check(preferences.edit().putString(KEY, revision).commit()) { "Не удалось сохранить состояние базы" }
        }
    }

    /** Call only after a successful full replacement, while the restore cannot be cancelled. */
    fun invalidate(context: Context): String = synchronized(lock) {
        UUID.randomUUID().toString().also { revision ->
            check(preferences(context).edit().putString(KEY, revision).commit()) { "Не удалось сохранить состояние базы" }
        }
    }

    fun observe(context: Context, onChanged: (String) -> Unit): () -> Unit {
        val preferences = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == KEY) onChanged(current(context))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChanged(current(context))
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
