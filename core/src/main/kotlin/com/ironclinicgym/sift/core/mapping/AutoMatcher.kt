package com.ironclinicgym.sift.core.mapping

import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionPropertySchema
import com.ironclinicgym.sift.core.theme.PRIORITY_META

/** A raw option of a select/status property, the input to auto-matching. */
data class OptionInput(val optionId: String, val optionName: String, val colorHint: String? = null)

/**
 * Bring your own database auto-matching.
 *
 * The user's own options are the source of truth: every option becomes a board priority (or
 * bucket). Auto-matching only *pre-fills* color and order by recognizing names that line up
 * with Sift's defaults. Unmatched options are surfaced (never silently collapsed into a
 * catch-all) so the user can resolve them. Matched priorities take the default urgency order;
 * unmatched ones keep their original order and sort after the matched ones.
 *
 * Synonyms are loaded from bundled data files via [SynonymLoader]; English ships verified,
 * other languages land as drafts and fall back to English.
 */
object AutoMatcher {

    data class PriorityMatch(val bindings: List<PriorityBinding>, val unmatchedOptionIds: List<String>)
    data class BucketMatch(val bindings: List<BucketBinding>, val unmatchedOptionIds: List<String>)

    private val priorityOrder: Map<String, Int> =
        PRIORITY_META.withIndex().associate { (i, m) -> m.key.name to i }

    private fun prioritySynonymMap(language: String): Map<String, String> = buildMap {
        PRIORITY_META.forEach { put(normalize(it.name), it.key.name) }
        SynonymLoader.prioritySynonyms(language).forEach { (key, synonyms) ->
            synonyms.forEach { put(normalize(it), key) }
        }
    }

    private fun bucketSynonymMap(language: String): Map<String, String> = buildMap {
        SynonymLoader.bucketSynonyms(language).forEach { (key, synonyms) ->
            synonyms.forEach { put(normalize(it), key) }
        }
    }

    fun matchPriorities(options: List<OptionInput>, language: String = "en"): PriorityMatch {
        val synonyms = prioritySynonymMap(language)
        val matched = mutableListOf<Pair<OptionInput, String>>()
        val unmatched = mutableListOf<OptionInput>()
        options.forEach { opt ->
            val key = synonyms[normalize(opt.optionName)]
            if (key != null) matched += opt to key else unmatched += opt
        }
        matched.sortBy { priorityOrder[it.second] ?: Int.MAX_VALUE }

        val bindings = buildList {
            matched.forEach { (opt, key) ->
                add(PriorityBinding(opt.optionId, opt.optionName, size, key, opt.colorHint))
            }
            unmatched.forEach { opt ->
                add(PriorityBinding(opt.optionId, opt.optionName, size, null, opt.colorHint))
            }
        }
        return PriorityMatch(bindings, unmatched.map { it.optionId })
    }

    fun matchBuckets(options: List<OptionInput>, language: String = "en"): BucketMatch {
        val synonyms = bucketSynonymMap(language)
        val bindings = options.map { opt ->
            BucketBinding(opt.optionId, opt.optionName, synonyms[normalize(opt.optionName)], opt.colorHint)
        }
        val unmatched = bindings.filter { it.bucketKey == null }.map { it.optionId }
        return BucketMatch(bindings, unmatched)
    }

    /**
     * Match schema properties to Sift roles by type compatibility and name synonyms.
     * Returns bindings ordered by role declaration order, skipping roles with no viable match.
     */
    fun matchRoles(schema: NotionDataSourceSchema, language: String = "en"): List<RolePropertyBinding> {
        val roleSynonyms = SynonymLoader.roleSynonyms(language)
        val used = mutableSetOf<String>()
        return Role.entries.mapNotNull { role ->
            val candidates = schema.properties.filter { it.id !in used && role.accepts(it.type) }
            if (candidates.isEmpty()) return@mapNotNull null
            val hints = roleSynonyms[role] ?: emptyList()
            val chosen = scoreAndPick(candidates, hints) ?: return@mapNotNull null
            used += chosen.id
            RolePropertyBinding(role, chosen.id, chosen.name, chosen.type)
        }
    }

    private fun scoreAndPick(
        candidates: List<NotionPropertySchema>,
        hints: List<String>,
    ): NotionPropertySchema? {
        if (candidates.isEmpty()) return null
        if (hints.isEmpty()) return candidates.firstOrNull()
        var bestProp: NotionPropertySchema? = null
        var bestScore = 0
        for (prop in candidates) {
            val n = normalize(prop.name)
            val score = when {
                hints.any { normalize(it) == n } -> 3
                hints.any { n.contains(normalize(it)) } -> 2
                hints.any { normalize(it).contains(n) } -> 1
                else -> 0
            }
            if (score > bestScore) { bestScore = score; bestProp = prop }
        }
        return bestProp ?: candidates.firstOrNull()
    }

    internal fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }
}
