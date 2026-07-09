package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask

/**
 * Projects raw tasks into the renderable board: tasks grouped into visible priorities, ordered
 * time-and-overdue aware, with minimize and time-gating applied. Pure and clock-injected so it
 * is deterministic and KMP-portable (no java.time). The UI resolves colors from the theme
 * separately; this owns structure only.
 */

/** A snapshot of the device clock, supplied by the platform (uses the local time zone). */
data class ClockSnapshot(
    /** ISO day of week: 1 = Monday .. 7 = Sunday. */
    val dayOfWeek: Int,
    /** Minutes since local midnight, 0..1439. */
    val minuteOfDay: Int,
    /** Local date as YYYY-MM-DD, for lexical overdue comparison against Notion date strings. */
    val todayIso: String,
)

data class ProjectedItem(
    val task: SiftTask,
    val bucket: BucketView,
    val isOverdue: Boolean,
    /** HH:mm when the due value carries a time, else empty. */
    val timeLabel: String,
    /** Human-readable date label (e.g., "Today", "Tuesday", "Jul 15"). */
    val dateLabel: String = "",
    val labels: List<String> = emptyList(),
)

data class ProjectedPriority(
    val view: PriorityView,
    /** Ordered, active (not done) items for the priority. */
    val items: List<ProjectedItem>,
    /** Total active item count (== items.size; kept explicit for the count pill and degrade). */
    val totalCount: Int,
    /** Done items, for the focused view's quiet completed group. */
    val completed: List<ProjectedItem>,
    /** Sub-groups for one day priority. Empty for other priorities. */
    val landmarks: Map<Landmark, List<ProjectedItem>> = emptyMap(),
)

data class BoardProjection(
    val priorities: List<ProjectedPriority>,
    val pinnedItems: List<ProjectedItem> = emptyList(),
    val minimized: Boolean,
    val twoColumn: Boolean,
)

/** Group [tasks] into the board defined by [settings], as of [clock]. */
fun projectBoard(
    tasks: List<SiftTask>,
    settings: BoardSettings,
    clock: ClockSnapshot,
): BoardProjection {
    val boardTasks = tasks.filter { !it.isBrainDump }
    val pinnedTasks = boardTasks.filter { it.isPinned && !it.isDone }
    val pinnedIds = pinnedTasks.map { it.pageId }.toSet()

    val visiblePriorities = settings.priorities
        .asSequence()
        .filter { !it.hidden }
        .filter { !settings.minimized || it.showInGlance }
        .sortedBy { it.order }
        .toList()

    val projected = visiblePriorities.map { priority ->
        val forPriority = boardTasks.filter { it.priorityOptionId == priority.optionId }
        val active = forPriority
            .filter { !it.isDone && it.pageId !in pinnedIds }
            .map { it.toProjectedItem(settings, clock) }
            .filter { isVisibleNow(it.bucket.schedule, clock) }
            .sortedWith(ITEM_ORDER)
        val completed = forPriority
            .filter { it.isDone && it.completedOnIso == clock.todayIso }
            .map { it.toProjectedItem(settings, clock) }
        val isOneDay = priority.colorKey == "ONEDAY"
        val landmarks = if (isOneDay) {
            active.groupBy { OneDayLandmarks.assignLandmark(it.task.due, clock.todayIso) }
        } else emptyMap()
        ProjectedPriority(
            view = priority,
            items = active,
            totalCount = active.size,
            completed = completed,
            landmarks = landmarks,
        )
    }

    val pinnedProjected = pinnedTasks
        .map { it.toProjectedItem(settings, clock) }
        .sortedWith(ITEM_ORDER)

    return BoardProjection(projected, pinnedProjected, settings.minimized, settings.twoColumn)
}

/** Project brain dump tasks (separate from the board). Grouped by landmarks. */
fun projectBrainDump(
    tasks: List<SiftTask>,
    settings: BoardSettings,
    clock: ClockSnapshot,
): List<ProjectedItem> {
    return tasks
        .filter { it.isBrainDump && !it.isDone }
        .map { it.toProjectedItem(settings, clock) }
        .sortedWith(ITEM_ORDER)
}

/** True when [schedule] makes its priority visible at [clock]. Disabled schedules are always visible. */
fun isVisibleNow(schedule: PrioritySchedule, clock: ClockSnapshot): Boolean {
    if (!schedule.enabled) return true
    val dayOk = schedule.days.isEmpty() || clock.dayOfWeek in schedule.days
    if (!dayOk) return false
    val m = clock.minuteOfDay
    return if (schedule.startMinute <= schedule.endMinute) {
        m >= schedule.startMinute && m < schedule.endMinute
    } else {
        // Window wraps past midnight (for example 22:00 to 06:00).
        m >= schedule.startMinute || m < schedule.endMinute
    }
}

private fun SiftTask.toProjectedItem(settings: BoardSettings, clock: ClockSnapshot): ProjectedItem {
    val datePart = due?.take(10)
    val overdue = !isDone && datePart != null && datePart < clock.todayIso
    val rawTime = due?.let { if (it.length >= 16 && it[10] == 'T') it.substring(11, 16) else "" } ?: ""
    return ProjectedItem(
        task = this,
        bucket = settings.bucketFor(bucketOptionId),
        isOverdue = overdue,
        timeLabel = if (rawTime.isEmpty()) "" else formatClock(rawTime, settings.use24HourTime),
        dateLabel = humanDate(datePart, clock.todayIso) ?: "",
        labels = labels,
    )
}

/**
 * A manual drag order takes precedence when set; otherwise the sensible default falls out:
 * overdue first, then dated ascending, then undated, then by title for stability.
 */
private val ITEM_ORDER: Comparator<ProjectedItem> =
    compareBy<ProjectedItem> { it.task.manualSortIndex ?: Int.MAX_VALUE }
        .thenByDescending { it.isOverdue }
        .thenBy { it.task.due == null }
        .thenBy { it.task.due ?: "" }
        .thenBy { it.task.title }
