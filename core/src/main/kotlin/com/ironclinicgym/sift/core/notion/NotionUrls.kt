package com.ironclinicgym.sift.core.notion

object NotionUrls {

    private const val BASE = "https://www.notion.so"
    private val UUID_PATTERN = Regex("[0-9a-f]{32}")

    fun pageUrl(pageId: String): String = "$BASE/${stripHyphens(pageId)}"

    fun databaseUrl(databaseId: String): String = "$BASE/${stripHyphens(databaseId)}"

    fun parseDatabaseId(input: String): String? {
        val cleaned = input.trim()
        val noDash = stripHyphens(cleaned)
        if (UUID_PATTERN.matches(noDash)) return noDash

        val path = runCatching {
            val afterScheme = cleaned.substringAfter("://", "")
            afterScheme.substringAfter("/").substringBefore("?").substringBefore("#")
        }.getOrNull() ?: return null

        val segments = path.split("/").filter { it.isNotEmpty() }
        for (segment in segments.asReversed()) {
            val candidate = stripHyphens(segment)
            if (UUID_PATTERN.matches(candidate)) return candidate
            val suffix = candidate.substringAfterLast("-", "")
            if (UUID_PATTERN.matches(suffix)) return suffix
        }
        return null
    }

    private fun stripHyphens(id: String): String = id.replace("-", "")
}
