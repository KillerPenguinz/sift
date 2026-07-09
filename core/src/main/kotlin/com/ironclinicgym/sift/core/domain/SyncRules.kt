package com.ironclinicgym.sift.core.domain

/**
 * Pull-based sync policy. Pure constants and decisions; the actual scheduling lives in the
 * platform layer (WorkManager). No Android types here so the rules stay testable and portable.
 */
object SyncRules {
    /** WorkManager's realistic periodic floor. */
    const val PERIODIC_REFRESH_MINUTES: Long = 15

    /** Cache is considered stale after this long; UI may trigger a foreground refresh. */
    const val STALE_AFTER_MS: Long = PERIODIC_REFRESH_MINUTES * 60 * 1000

    fun isStale(lastSyncAtMs: Long?, nowMs: Long): Boolean =
        lastSyncAtMs == null || (nowMs - lastSyncAtMs) >= STALE_AFTER_MS
}
