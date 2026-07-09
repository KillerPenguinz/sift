package com.ironclinicgym.sift.core.notion

/** One page of a Notion list response. */
data class NotionPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/** The page size Sift always requests. Notion no longer guarantees an implicit default. */
const val NOTION_PAGE_SIZE: Int = 100

/**
 * Follow Notion's cursor pagination to completion, accumulating all items.
 *
 * [fetch] receives the current cursor (null for the first page) and must request exactly
 * [NOTION_PAGE_SIZE] items. Kept generic and pure so it is reused for search and query.
 */
suspend fun <T> paginateAll(fetch: suspend (cursor: String?) -> NotionPage<T>): List<T> {
    val all = mutableListOf<T>()
    var cursor: String? = null
    do {
        val page = fetch(cursor)
        all += page.items
        cursor = page.nextCursor
    } while (page.hasMore && cursor != null)
    return all
}
