package com.ironclinicgym.sift.core.domain.recurrence

/**
 * Recurrence over the RRULE (RFC 5545) standard. Sift does not invent an engine; a real RRULE
 * library computes the next occurrence. This interface is the seam that keeps the library out
 * of the rest of core, so a KMP extraction can swap the JVM implementation for a multiplatform
 * one without touching callers.
 */
interface RecurrenceEngine {

    /**
     * The next occurrence strictly after [afterIso] for [rrule], or null when the rule has ended
     * (a COUNT/UNTIL bound is exhausted) or cannot be parsed. [afterIso] and the result are date
     * or date-time ISO strings; any time-of-day suffix on [afterIso] is preserved on the result.
     */
    fun next(rrule: String, afterIso: String): String?

    /** True if [rrule] parses as a valid recurrence rule. */
    fun isValid(rrule: String): Boolean
}
