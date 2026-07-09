package com.ironclinicgym.sift.data.secure

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.domain.ports.TokenStore
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.core.oauth.TokenBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

// One encrypted-at-rest preferences store. Values are Keystore-encrypted before writing.
private val Context.secureDataStore by preferencesDataStore(name = "sift_secure")

private val TOKEN_KEY = stringPreferencesKey("token_bundle")
private val MAPPING_KEY = stringPreferencesKey("mapping_set")

private val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Keystore-encrypted [TokenStore]. The serialized [TokenBundle] is encrypted with
 * [KeystoreCrypto] before it touches DataStore, so the OAuth token is never stored in plain
 * form (spec requires secure storage; EncryptedSharedPreferences is deprecated).
 */
class SecureTokenStore(
    context: Context,
    private val crypto: KeystoreCrypto,
) : TokenStore {
    private val dataStore = context.secureDataStore

    override suspend fun save(bundle: TokenBundle) {
        val payload = crypto.encrypt(storeJson.encodeToString(TokenBundle.serializer(), bundle))
        dataStore.edit { it[TOKEN_KEY] = payload }
    }

    override suspend fun load(): TokenBundle? {
        val encrypted = dataStore.data.map { it[TOKEN_KEY] }.first() ?: return null
        return runCatching {
            storeJson.decodeFromString(TokenBundle.serializer(), crypto.decrypt(encrypted))
        }.getOrNull()
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }
}

/**
 * Keystore-encrypted [MappingStore]. The mapping is sensitive (it identifies the user's data
 * bucket and property ids), so it lives here, not in Room. Exposes a [Flow] for reactive UI.
 */
class SecureMappingStore(
    context: Context,
    private val crypto: KeystoreCrypto,
) : MappingStore {
    private val dataStore = context.secureDataStore

    override suspend fun load(): MappingSet =
        decode(dataStore.data.map { it[MAPPING_KEY] }.first())

    override suspend fun save(set: MappingSet) {
        val payload = crypto.encrypt(storeJson.encodeToString(MappingSet.serializer(), set))
        dataStore.edit { it[MAPPING_KEY] = payload }
    }

    override fun observe(): Flow<MappingSet> =
        dataStore.data.map { decode(it[MAPPING_KEY]) }

    private fun decode(encrypted: String?): MappingSet {
        if (encrypted == null) return MappingSet.EMPTY
        return runCatching {
            storeJson.decodeFromString(MappingSet.serializer(), crypto.decrypt(encrypted))
        }.getOrDefault(MappingSet.EMPTY)
    }
}
