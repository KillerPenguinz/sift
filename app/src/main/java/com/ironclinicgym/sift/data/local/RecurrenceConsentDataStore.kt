package com.ironclinicgym.sift.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironclinicgym.sift.core.domain.ports.Consent
import com.ironclinicgym.sift.core.domain.ports.RecurrenceConsentStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Consent is not sensitive; a plain DataStore, one entry per mapping id, mirrors BoardSettings.
private val Context.recurrenceConsentStore by preferencesDataStore(name = "sift_recurrence_consent")

/** DataStore-backed [RecurrenceConsentStore]. Absent entry means [Consent.UNASKED]. */
class RecurrenceConsentDataStore(context: Context) : RecurrenceConsentStore {
    private val dataStore = context.recurrenceConsentStore

    private fun key(mappingId: String) = stringPreferencesKey("consent_$mappingId")

    override suspend fun get(mappingId: String): Consent {
        val raw = dataStore.data.map { it[key(mappingId)] }.first()
        return raw?.let { runCatching { Consent.valueOf(it) }.getOrNull() } ?: Consent.UNASKED
    }

    override suspend fun set(mappingId: String, consent: Consent) {
        dataStore.edit { it[key(mappingId)] = consent.name }
    }

    /** Debug reset: drop consent for every database. */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
