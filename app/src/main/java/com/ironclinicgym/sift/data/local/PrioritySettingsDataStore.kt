package com.ironclinicgym.sift.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.board.normalized
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// Global priority-timing settings: a single JSON blob, not keyed by mapping. Non-sensitive.
private val Context.priorityDataStore by preferencesDataStore(name = "sift_priority_settings")

/** DataStore-backed [PrioritySettingsStore]. */
class PrioritySettingsDataStore(context: Context) : PrioritySettingsStore {
    private val dataStore = context.priorityDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("priority_settings")

    override fun observe(): Flow<PrioritySettings> =
        dataStore.data.map { prefs -> prefs[key]?.let(::decode) ?: PrioritySettings.DEFAULT }

    override suspend fun load(): PrioritySettings =
        dataStore.data.map { it[key] }.first()?.let(::decode) ?: PrioritySettings.DEFAULT

    override suspend fun save(settings: PrioritySettings) {
        dataStore.edit { it[key] = json.encodeToString(PrioritySettings.serializer(), settings) }
    }

    /** Debug reset: drop the global priority settings. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun decode(raw: String): PrioritySettings? =
        runCatching { json.decodeFromString(PrioritySettings.serializer(), raw).normalized() }.getOrNull()
}
