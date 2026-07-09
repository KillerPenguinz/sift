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
 * A task is NOT fired again if safetyCatchFiredBand already equals the current band.
 * A task's record is cleared if it is no longer in any imminent band.
 */
fun evaluateSafetyCatch(
    datedTasks: List<SiftTask>,
    localStates: Map<String, TaskLocalState>,
    todayIso: String,
): SafetyCatchEvaluation {
    val toFire = mutableListOf<Pair<SiftTask, String>>()
    val toClear = mutableListOf<String>()

    for (task in datedTasks) {
        val due = task.due?.take(10) ?: continue
        val currentBand: String? = when {
            due < todayIso -> "asap"
            due == todayIso -> "today"
            else -> null
        }
        val firedBand = localStates[task.pageId]?.safetyCatchFiredBand

        if (currentBand != null) {
            if (firedBand != currentBand) {
                toFire.add(task to currentBand)
            }
        } else if (firedBand != null) {
            toClear.add(task.pageId)
        }
    }

    return SafetyCatchEvaluation(toFire = toFire, toClear = toClear)
}
