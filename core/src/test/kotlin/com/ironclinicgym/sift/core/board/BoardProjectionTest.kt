package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.DataSourceRef
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import com.ironclinicgym.sift.core.mapping.BucketBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardProjectionTest {

    private val clock = ClockSnapshot(dayOfWeek = 3, minuteOfDay = 10 * 60, todayIso = "2026-07-01")

    private fun mapping(): DatabaseMapping = DatabaseMapping(
        id = "mapping-ds1",
        ref = DataSourceRef("ws", "db", "ds1"),
        label = "My tasks",
        bindings = listOf(RolePropertyBinding(Role.PRIORITY, "pr", "Priority", PropertyType.SELECT)),
        priorities = listOf(
            PriorityBinding("asap", "asap", 0, "ASAP"),
            PriorityBinding("today", "today", 1, "TODAY"),
            PriorityBinding("soon", "soon", 2, "SOON"),
        ),
        buckets = listOf(
            BucketBinding("work", "Work", "WORK"),
            BucketBinding("home", "Home", "PERSONAL"),
        ),
    )

    private fun task(id: String, priority: String?, bucket: String?, done: Boolean = false, due: String? = null) =
        SiftTask(
            pageId = "p$id",
            mappingId = "mapping-ds1",
            title = "Task $id",
            priorityOptionId = priority,
            priorityOptionName = priority,
            bucketOptionId = bucket,
            bucketOptionName = bucket,
            isDone = done,
            due = due,
            notes = null,
            // A done task counts as "completed today" only with today's local marker (Phase 3).
            completedOnIso = if (done) clock.todayIso else null,
        )

    @Test fun `settings seed from mapping with default glance of asap and today`() {
        val settings = BoardSettings.fromMapping(mapping())
        assertEquals(3, settings.priorities.size)
        assertEquals(setOf("asap", "today"), settings.priorities.filter { it.showInGlance }.map { it.optionId }.toSet())
        // Unset bucket is present as the neutral default.
        assertTrue(settings.buckets.any { it.optionId == null })
    }

    @Test fun `project groups tasks into their priorities and excludes done from active`() {
        val settings = BoardSettings.fromMapping(mapping())
        val tasks = listOf(
            task("1", "asap", "work"),
            task("2", "asap", "home", done = true),
            task("3", "today", "work"),
        )
        val board = projectBoard(tasks, settings, clock)
        val asap = board.priorities.first { it.view.optionId == "asap" }
        assertEquals(1, asap.totalCount)
        assertEquals(1, asap.completed.size)
        assertEquals(1, board.priorities.first { it.view.optionId == "today" }.totalCount)
    }

    @Test fun `unknown bucket falls back to the neutral unset coding`() {
        val settings = BoardSettings.fromMapping(mapping())
        val board = projectBoard(listOf(task("1", "asap", "mystery")), settings, clock)
        val item = board.priorities.first { it.view.optionId == "asap" }.items.single()
        assertEquals(null, item.bucket.colorKey)
        assertEquals("label", item.bucket.icon)
    }

    @Test fun `overdue sorts first and derives its flag from the due date`() {
        val settings = BoardSettings.fromMapping(mapping())
        val tasks = listOf(
            task("future", "today", "work", due = "2026-07-05"),
            task("late", "today", "work", due = "2026-06-20"),
        )
        val today = projectBoard(tasks, settings, clock).priorities.first { it.view.optionId == "today" }
        assertEquals("Task late", today.items.first().task.title)
        assertTrue(today.items.first().isOverdue)
        assertFalse(today.items.last().isOverdue)
    }

    @Test fun `datetime due yields a time label formatted per the clock preference`() {
        val settings = BoardSettings.fromMapping(mapping())
        val task = listOf(task("1", "today", "work", due = "2026-07-01T14:30:00.000+00:00"))
        // Default is AM/PM.
        assertEquals("2:30 PM", projectBoard(task, settings, clock).priorities.first { it.view.optionId == "today" }.items.single().timeLabel)
        // Opt into 24 hour.
        assertEquals("14:30", projectBoard(task, settings.copy(use24HourTime = true), clock).priorities.first { it.view.optionId == "today" }.items.single().timeLabel)
    }

    @Test fun `minimized shows only the glance subset`() {
        val settings = BoardSettings.fromMapping(mapping()).copy(minimized = true)
        val board = projectBoard(emptyList(), settings, clock)
        assertEquals(setOf("asap", "today"), board.priorities.map { it.view.optionId }.toSet())
    }

    @Test fun `hidden priorities never render`() {
        val base = BoardSettings.fromMapping(mapping())
        val settings = base.copy(priorities = base.priorities.map { if (it.optionId == "soon") it.copy(hidden = true) else it })
        val board = projectBoard(emptyList(), settings, clock)
        assertFalse(board.priorities.any { it.view.optionId == "soon" })
    }

    @Test fun `bucket schedule hides its items when gating is on`() {
        val base = BoardSettings.fromMapping(mapping())
        val gated = base.buckets.map {
            if (it.optionId == "work") it.copy(schedule = PrioritySchedule(true, emptySet(), 9 * 60, 9 * 60 + 30)) else it
        }
        val settings = base.copy(buckets = gated, timeGatingEnabled = true)
        val tasks = listOf(task("1", "asap", "work"), task("2", "asap", "home"))
        // Clock is 10:00, the Work window is 09:00 to 09:30 -> Work item hidden, home visible.
        val asap = projectBoard(tasks, settings, clock).priorities.first { it.view.optionId == "asap" }
        assertEquals(1, asap.totalCount)
        assertEquals("home", asap.items.single().bucket.optionId)
    }

    // Regression guard for the terminology refactor: priority == urgency, bucket == life area.
    // If the two dimensions were ever swapped, one half of these assertions must fail.
    @Test fun `priority and bucket resolve to their correct dimensions end to end`() {
        val settings = BoardSettings.fromMapping(mapping())
        // A task whose urgency option is "asap" and whose life-area option is "work".
        val board = projectBoard(listOf(task("1", priority = "asap", bucket = "work")), settings, clock)

        // Urgency dimension: the task lands in the ASAP *priority* column, never one named "work".
        val column = board.priorities.single { it.items.isNotEmpty() }
        assertEquals("asap", column.view.optionId)
        assertEquals("ASAP", column.view.colorKey)
        assertTrue("no priority column should carry a life-area id", board.priorities.none { it.view.optionId == "work" })

        // Life-area dimension: that same task's bucket resolves to Work, never to "asap".
        val item = column.items.single()
        assertEquals("work", item.bucket.optionId)
        assertEquals("WORK", item.bucket.colorKey)
        assertNotEquals("asap", item.bucket.optionId)

        // The two dimensions are carried by distinct SiftTask fields.
        assertEquals("asap", item.task.priorityOptionId)
        assertEquals("work", item.task.bucketOptionId)
    }

    @Test fun `overnight window wraps midnight`() {
        val schedule = PrioritySchedule(enabled = true, startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(isVisibleNow(schedule, clock.copy(minuteOfDay = 23 * 60)))
        assertTrue(isVisibleNow(schedule, clock.copy(minuteOfDay = 2 * 60)))
        assertFalse(isVisibleNow(schedule, clock.copy(minuteOfDay = 12 * 60)))
    }
}
