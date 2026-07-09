package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.board.DateBandConfig
import com.ironclinicgym.sift.core.board.DateBandEngine
import com.ironclinicgym.sift.core.theme.PriorityKey

sealed interface PolicyDecision {
    data class Proceed(val targetPriorityName: String, val targetPriorityId: String?) : PolicyDecision
    data class RedirectPrompt(val task: SiftTask, val reason: String) : PolicyDecision
    data class ProtectedFriction(
        val task: SiftTask,
        val action: String,
        val targetName: String? = null,
        val targetId: String? = null,
    ) : PolicyDecision
    data class SafetyCatch(val task: SiftTask, val bandLabel: String) : PolicyDecision
}

class TwoAxisPolicy(
    private val config: DateBandConfig = DateBandConfig.DEFAULT,
) {

    fun evaluateMove(
        task: SiftTask,
        toPriorityKey: PriorityKey,
        fromPriorityKey: PriorityKey?,
        todayIso: String,
    ): PolicyDecision {
        if (task.isDated) {
            return PolicyDecision.RedirectPrompt(
                task = task,
                reason = "This task has a date. Change the date to move it.",
            )
        }

        if (task.isProtected && fromPriorityKey != null && isDownward(fromPriorityKey, toPriorityKey)) {
            return PolicyDecision.ProtectedFriction(
                task = task,
                action = "move to ${toPriorityKey.name.lowercase()}",
            )
        }

        return PolicyDecision.Proceed(
            targetPriorityName = toPriorityKey.name,
            targetPriorityId = null,
        )
    }

    fun evaluateSnooze(
        task: SiftTask,
        todayIso: String,
    ): PolicyDecision {
        if (task.isProtected) {
            return PolicyDecision.ProtectedFriction(
                task = task,
                action = if (task.isDated) "push back the date" else "snooze",
            )
        }
        return PolicyDecision.Proceed(targetPriorityName = "", targetPriorityId = null)
    }

    fun evaluateDateChange(
        task: SiftTask,
        newDueIso: String,
        todayIso: String,
    ): PolicyDecision {
        val band = DateBandEngine.resolveBand(newDueIso, todayIso, config)
        if (band != null && DateBandEngine.isImminent(newDueIso, todayIso, config)) {
            val wasImminent = DateBandEngine.isImminent(task.due, todayIso, config)
            if (!wasImminent && task.rescheduleCount > 0) {
                return PolicyDecision.SafetyCatch(
                    task = task,
                    bandLabel = DateBandEngine.previewLabel(newDueIso, todayIso, config),
                )
            }
        }
        return PolicyDecision.Proceed(
            targetPriorityName = band?.name ?: "",
            targetPriorityId = null,
        )
    }

    private fun isDownward(from: PriorityKey, to: PriorityKey): Boolean {
        return to.ordinal > from.ordinal
    }
}
