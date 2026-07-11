package com.ironclinicgym.sift.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferences by preferencesDataStore(name = "sift_app_prefs")

class AppPreferencesDataStore(context: Context) {
    private val dataStore = context.appPreferences

    private val notionReminderDismissed = booleanPreferencesKey("notion_reminder_dismissed")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val inflationMasterOffShownKey = booleanPreferencesKey("inflation_master_off_shown")
    private val brainDumpSortKey = stringPreferencesKey("brain_dump_sort")
    private val brainDumpSortAscKey = booleanPreferencesKey("brain_dump_sort_ascending")

    fun notionReminderDismissed(): Flow<Boolean> =
        dataStore.data.map { it[notionReminderDismissed] == true }

    suspend fun dismissNotionReminder() {
        dataStore.edit { it[notionReminderDismissed] = true }
    }

    fun themeMode(): Flow<String> =
        dataStore.data.map { it[themeModeKey] ?: "auto" }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[themeModeKey] = mode }
    }

    fun inflationMasterOffShown(): Flow<Boolean> =
        dataStore.data.map { it[inflationMasterOffShownKey] == true }

    suspend fun setInflationMasterOffShown(shown: Boolean) {
        dataStore.edit { it[inflationMasterOffShownKey] = shown }
    }

    fun brainDumpSort(): Flow<String> =
        dataStore.data.map { it[brainDumpSortKey] ?: "label" }

    suspend fun setBrainDumpSort(sort: String) {
        dataStore.edit { it[brainDumpSortKey] = sort }
    }

    fun brainDumpSortAscending(): Flow<Boolean> =
        dataStore.data.map { it[brainDumpSortAscKey] != false }

    suspend fun setBrainDumpSortAscending(ascending: Boolean) {
        dataStore.edit { it[brainDumpSortAscKey] = ascending }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
