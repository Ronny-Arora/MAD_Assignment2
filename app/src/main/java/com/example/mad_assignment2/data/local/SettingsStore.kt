package com.example.mad_assignment2.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore private constructor(private val appCtx: Context) {

    private object Keys {
        val LAST_QUERY     = stringPreferencesKey("last_query")
        val SEARCH_IDX     = intPreferencesKey("search_idx")
        val SEARCH_OFFSET  = intPreferencesKey("search_offset")
        val LIB_IDX        = intPreferencesKey("lib_idx")
        val LIB_OFFSET     = intPreferencesKey("lib_offset")
    }

    // --- Reads (Flows) ---
    val lastQuery: Flow<String> =
        appCtx.dataStore.data.map { it[Keys.LAST_QUERY] ?: "" }

    val searchScroll: Flow<Pair<Int, Int>> =
        appCtx.dataStore.data.map { (it[Keys.SEARCH_IDX] ?: 0) to (it[Keys.SEARCH_OFFSET] ?: 0) }

    val libScroll: Flow<Pair<Int, Int>> =
        appCtx.dataStore.data.map { (it[Keys.LIB_IDX] ?: 0) to (it[Keys.LIB_OFFSET] ?: 0) }

    // --- Writes (suspend) ---
    suspend fun setLastQuery(q: String) {
        appCtx.dataStore.edit { it[Keys.LAST_QUERY] = q }
    }

    suspend fun setSearchScroll(index: Int, offset: Int) {
        appCtx.dataStore.edit {
            it[Keys.SEARCH_IDX] = index
            it[Keys.SEARCH_OFFSET] = offset
        }
    }

    suspend fun setLibScroll(index: Int, offset: Int) {
        appCtx.dataStore.edit {
            it[Keys.LIB_IDX] = index
            it[Keys.LIB_OFFSET] = offset
        }
    }

    companion object {
        @Volatile private var INSTANCE: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}