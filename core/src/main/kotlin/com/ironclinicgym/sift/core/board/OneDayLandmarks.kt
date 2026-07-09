package com.ironclinicgym.sift.core.board

enum class Landmark {
    THIS_MONTH,
    NEXT_90,
    LATER_THIS_YEAR,
    BEYOND,
    NO_DATE,
}

object OneDayLandmarks {

    fun assignLandmark(dueIso: String?, todayIso: String): Landmark {
        if (dueIso == null) return Landmark.NO_DATE
        val date = dueIso.take(10)
        val todayY = todayIso.substring(0, 4).toInt()
        val todayM = todayIso.substring(5, 7).toInt()
        val dueY = date.substring(0, 4).toInt()
        val dueM = date.substring(5, 7).toInt()

        if (dueY == todayY && dueM == todayM) return Landmark.THIS_MONTH

        val daysDiff = DateBandEngine.daysBetween(todayIso, date)
        if (daysDiff <= 90) return Landmark.NEXT_90
        if (dueY == todayY) return Landmark.LATER_THIS_YEAR
        return Landmark.BEYOND
    }

    fun displayLabel(landmark: Landmark): String = when (landmark) {
        Landmark.THIS_MONTH -> "This month"
        Landmark.NEXT_90 -> "Next 90 days"
        Landmark.LATER_THIS_YEAR -> "Later this year"
        Landmark.BEYOND -> "Beyond"
        Landmark.NO_DATE -> "No date"
    }
}
