package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState

data class SafetyCatchEvaluation(
    val toFire: List<Pair<SiftTask, String>>,  // task, band ("asap" or "today")
    val toClear: List<String>,                  // page IDs whose band no longer applies
)

/**
 * Pure function. Evaluates which tasks need a safety-catch prompt and which
 * have exited the imminent band so their stored record can be cleared.
 *
 * "asap" band = task is overdue (due < todayIso).
 * "today" band = task is due today (due == todayIso).
 *
 * Firing rule, in two parts:
 *  - If the task has never been shown before (`lastSafetyCatchShownAt == null`), fall back to
 *    the classic one-shot-per-band gate: fire only if `safetyCatchFiredBand` does not already
 *    equal the current band. This covers first-ever evaluation and legacy rows written before
 *    the throttle timestamp existed.
 *  - Once a task HAS been shown (a timestamp is on record), the band no longer matters: the
 *    task is eligible to fire again purely based on elapsed time, at most once per
 *    [throttleHours] window. This is the fix for the bug where a task stuck in the same band
 *    (e.g. still overdue) was suppressed forever after its first, dismissed prompt; it now
 *    re-fires every [throttleHours] as long as it remains imminent.
 *
 * A task's record is cleared if it is no longer in any imminent band.
 */
fun evaluateSafetyCatch(
    datedTasks: List<SiftTask>,
    localStates: Map<String, TaskLocalState>,
    todayIso: String,
    throttleHours: Int = 12,
    nowMillis: Long = System.currentTimeMillis(),
): SafetyCatchEvaluation {
    val throttleMs = throttleHours * 3_600_000L
    val toFire = mutableListOf<Pair<SiftTask, String>>()
    val toClear = mutableListOf<String>()

    for (task in datedTasks) {
        val due = task.due?.take(10) ?: continue
        val currentBand: String? = when {
            due < todayIso -> "asap"
            due == todayIso -> "today"
            else -> null
        }
        val state = localStates[task.pageId]
        val firedBand = state?.safetyCatchFiredBand
        val lastShown = state?.lastSafetyCatchShownAt

        if (currentBand != null) {
            val shouldFire = if (lastShown == null) {
                firedBand != currentBand
            } else {
                (nowMillis - lastShown) >= throttleMs
            }
            if (shouldFire) {
                toFire.add(task to currentBand)
            }
        } else if (firedBand != null) {
            toClear.add(task.pageId)
        }
    }

    return SafetyCatchEvaluation(toFire = toFire, toClear = toClear)
}
