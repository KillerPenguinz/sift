package com.ironclinicgym.sift.core.notion

import kotlinx.coroutines.delay

/**
 * Exponential backoff with retry for transient Notion failures.
 *
 * Retries [NotionException.RateLimited] (honoring Retry-After), [NotionException.Network],
 * and 5xx [NotionException.Api]. Never retries auth or validation errors. Sleep is injectable
 * for deterministic tests.
 */
class Backoff(
    private val maxAttempts: Int = 4,
    private val baseDelayMs: Long = 500,
    private val maxDelayMs: Long = 8_000,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    /**
     * When false, only 429 (definitely-not-processed) is retried; ambiguous [Network] and 5xx
     * failures bubble immediately. Used for non-idempotent page creation, where a blind retry
     * after a lost acknowledgement could duplicate a task. The caller reconciles instead.
     */
    private val retryAmbiguous: Boolean = true,
) {
    suspend fun <T> retry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: NotionException) {
                attempt++
                if (attempt >= maxAttempts || !isRetryable(e)) throw e
                sleep(delayFor(attempt, e))
            }
        }
    }

    private fun isRetryable(e: NotionException): Boolean = when (e) {
        is NotionException.RateLimited -> true
        is NotionException.Network -> retryAmbiguous
        is NotionException.Api -> retryAmbiguous && e.code in 500..599
        else -> false
    }

    private fun delayFor(attempt: Int, e: NotionException): Long {
        val retryAfter = (e as? NotionException.RateLimited)?.retryAfterSeconds
        if (retryAfter != null) return (retryAfter * 1000).toLong().coerceAtMost(maxDelayMs)
        val exponential = baseDelayMs shl (attempt - 1)
        return exponential.coerceAtMost(maxDelayMs)
    }
}
