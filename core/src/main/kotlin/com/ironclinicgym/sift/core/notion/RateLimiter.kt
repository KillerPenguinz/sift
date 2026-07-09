package com.ironclinicgym.sift.core.notion

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Enforces Notion's ~3 requests/second ceiling by spacing requests a minimum interval apart.
 *
 * Must be shared as a single instance across the whole process (foreground UI + the
 * WorkManager refresh), otherwise their combined traffic exceeds the limit. The clock and
 * sleep are injectable so this is deterministic under coroutine virtual-time tests and free
 * of any platform time API (KMP-safe).
 */
class RateLimiter(
    permitsPerSecond: Int = 3,
    private val nowMs: () -> Long = defaultClock(),
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val minIntervalMs: Long = (1000.0 / permitsPerSecond).toLong()
    private val mutex = Mutex()
    private var nextAllowedAtMs: Long = Long.MIN_VALUE

    /** Suspends until a request may proceed, then reserves the next slot. */
    suspend fun acquire() {
        val waitMs = mutex.withLock {
            val now = nowMs()
            val earliest = maxOf(now, nextAllowedAtMs)
            nextAllowedAtMs = earliest + minIntervalMs
            earliest - now
        }
        if (waitMs > 0) sleep(waitMs)
    }

    suspend fun <T> withPermit(block: suspend () -> T): T {
        acquire()
        return block()
    }

    companion object {
        private fun defaultClock(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
