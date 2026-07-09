package com.ironclinicgym.sift.core.notion

/**
 * Typed failures from the Notion API. Surfaced to the UI as plain-language, hyphen-free
 * messages; [Unauthorized] specifically drives the reconnect path when a user revokes
 * access from Notion's side (the app must not crash).
 */
sealed class NotionException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 429. [retryAfterSeconds] echoes the Retry-After header when present. */
    class RateLimited(val retryAfterSeconds: Double?) :
        NotionException("Notion is rate limiting requests.")

    /** 401 / token revoked or invalid. Trigger the reconnect flow. */
    class Unauthorized :
        NotionException("Sift has lost access to your Notion workspace. Please reconnect.")

    /** 400 / 409 validation problems (bad property shape, conflict). */
    class Validation(val notionCode: String?, detail: String) :
        NotionException(detail)

    /** Transport / connectivity failure. Retryable. */
    class Network(cause: Throwable?) :
        NotionException("Could not reach Notion. Check your connection and try again.", cause)

    /** Any other non-success status. */
    class Api(val code: Int, val notionCode: String?, detail: String) :
        NotionException(detail)
}
