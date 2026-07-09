package com.ironclinicgym.sift.core.board

data class InflationAlert(
    val kind: InflationKind,
    val count: Int,
    val threshold: Int,
    val message: String,
)

enum class InflationKind {
    ASAP_OVERLOAD,
    PROTECTED_SATURATION,
}

object SignalInflation {

    fun checkInflation(
        asapCount: Int,
        totalTasks: Int,
        protectedCount: Int,
        asapThreshold: Int = 5,
        protectedPercent: Int = 30,
    ): InflationAlert? {
        if (asapCount >= asapThreshold) {
            return InflationAlert(
                kind = InflationKind.ASAP_OVERLOAD,
                count = asapCount,
                threshold = asapThreshold,
                message = "You have $asapCount tasks in ASAP. When everything is urgent, nothing is.",
            )
        }
        if (totalTasks > 0) {
            val pct = (protectedCount * 100) / totalTasks
            if (pct >= protectedPercent) {
                return InflationAlert(
                    kind = InflationKind.PROTECTED_SATURATION,
                    count = protectedCount,
                    threshold = protectedPercent,
                    message = "$pct% of your tasks are protected. The shield loses meaning if everything has one.",
                )
            }
        }
        return null
    }
}
