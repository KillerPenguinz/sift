package com.ironclinicgym.sift.core.mapping

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads synonym data from bundled JSON resources. English ships verified; other languages
 * land as drafts and fall back to English when missing. Structured for easy community
 * contribution: each language is a standalone file under `synonyms/`.
 */
object SynonymLoader {

    private val cache = mutableMapOf<String, SynonymSet?>()

    data class SynonymSet(
        val roles: Map<Role, List<String>>,
        val priorities: Map<String, List<String>>,
        val buckets: Map<String, List<String>>,
    )

    fun load(language: String = "en"): SynonymSet {
        cache[language]?.let { return it }
        val parsed = tryLoad(language) ?: tryLoad("en")
        ?: SynonymSet(emptyMap(), emptyMap(), emptyMap())
        cache[language] = parsed
        return parsed
    }

    fun roleSynonyms(language: String = "en"): Map<Role, List<String>> = load(language).roles
    fun prioritySynonyms(language: String = "en"): Map<String, List<String>> = load(language).priorities
    fun bucketSynonyms(language: String = "en"): Map<String, List<String>> = load(language).buckets

    private fun tryLoad(language: String): SynonymSet? {
        val stream = SynonymLoader::class.java.getResourceAsStream("/synonyms/$language.json")
            ?: return null
        val text = stream.bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(text).jsonObject
        return SynonymSet(
            roles = parseRoles(root["roles"]?.jsonObject),
            priorities = parseStringMap(root["priorities"]?.jsonObject),
            buckets = parseStringMap(root["buckets"]?.jsonObject),
        )
    }

    private fun parseRoles(obj: JsonObject?): Map<Role, List<String>> {
        if (obj == null) return emptyMap()
        return obj.entries.mapNotNull { (key, value) ->
            val role = runCatching { Role.valueOf(key) }.getOrNull() ?: return@mapNotNull null
            role to value.jsonArray.map { it.jsonPrimitive.content }
        }.toMap()
    }

    private fun parseStringMap(obj: JsonObject?): Map<String, List<String>> {
        if (obj == null) return emptyMap()
        return obj.entries.associate { (key, value) ->
            key to value.jsonArray.map { it.jsonPrimitive.content }
        }
    }
}
