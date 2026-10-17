package com.simplylauncher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("launcher_prefs")

object Prefs {
    private val KEY_COLOR = intPreferencesKey("bg_color")
    private val KEY_ALPHA = floatPreferencesKey("bg_alpha")
    private val KEY_COLS = intPreferencesKey("grid_cols")
    private val KEY_ROWS = intPreferencesKey("grid_rows")
    private val KEY_HOME_ITEMS = stringPreferencesKey("home_items")
    private val KEY_DOCK_ITEMS = stringPreferencesKey("dock_items")
    private val KEY_CUSTOM_LABELS = stringPreferencesKey("custom_labels")

    fun colorFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLOR] ?: 0xFF000000.toInt() }

    fun alphaFlow(context: Context): Flow<Float> =
        context.dataStore.data.map { it[KEY_ALPHA] ?: 0.9f }

    fun colsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLS] ?: 4 }

    fun rowsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_ROWS] ?: 6 }

    fun homeItemsFlow(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_HOME_ITEMS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    fun dockItemsFlow(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_DOCK_ITEMS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    fun customLabelsFlow(context: Context): Flow<Map<String, String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_CUSTOM_LABELS] ?: ""
            if (raw.isEmpty()) emptyMap()
            else raw.split("|").filter { it.isNotBlank() }.associate {
                val parts = it.split(":", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
        }

    suspend fun setColor(context: Context, color: Int) {
        context.dataStore.edit { it[KEY_COLOR] = color }
    }

    suspend fun setAlpha(context: Context, alpha: Float) {
        context.dataStore.edit { it[KEY_ALPHA] = alpha }
    }

    suspend fun setCols(context: Context, cols: Int) {
        context.dataStore.edit { it[KEY_COLS] = cols }
    }

    suspend fun setRows(context: Context, rows: Int) {
        context.dataStore.edit { it[KEY_ROWS] = rows }
    }

    suspend fun setHomeItems(context: Context, items: List<String>) {
        context.dataStore.edit { it[KEY_HOME_ITEMS] = items.joinToString(",") }
    }

    suspend fun setDockItems(context: Context, items: List<String>) {
        context.dataStore.edit { it[KEY_DOCK_ITEMS] = items.joinToString(",") }
    }

    suspend fun setCustomLabel(context: Context, packageName: String, label: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_CUSTOM_LABELS] ?: ""
            val map = if (raw.isEmpty()) mutableMapOf()
            else raw.split("|").filter { it.isNotBlank() }.associate {
                val parts = it.split(":", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }.toMutableMap()

            map[packageName] = label
            prefs[KEY_CUSTOM_LABELS] = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
    }
}
