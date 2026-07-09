package com.ironclinicgym.sift.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// Board display settings are not sensitive, so they live in a plain (unencrypted) DataStore,
// one JSON blob per mapping id. The token and mapping stay in the encrypted store.
private val Context.boardDataStore by preferencesDataStore(name = "sift_board")

/** DataStore-backed [BoardSettingsStore]. */
class BoardSettingsDataStore(context: Context) : BoardSettingsStore {
    private val dataStore = context.boardDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(mappingId: String) = stringPreferencesKey("settings_$mappingId")

    override fun observe(mappingId: String): Flow<BoardSettings?> =
        dataStore.data.map { prefs -> prefs[key(mappingId)]?.let(::decode) }

    override suspend fun load(mappingId: String): BoardSettings? =
        dataStore.data.map { it[key(mappingId)] }.first()?.let(::decode)

    override suspend fun save(settings: BoardSettings) {
        dataStore.edit { it[key(settings.mappingId)] = json.encodeToString(BoardSettings.serializer(), settings) }
    }

    /** Debug reset: drop every mapping's board settings. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private fun decode(raw: String): BoardSettings? =
        runCatching { json.decodeFromString(BoardSettings.serializer(), raw) }.getOrNull()
}
