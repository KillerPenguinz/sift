package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.theme.PriorityKey
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoAxisPolicyTest {

    private val policy = TwoAxisPolicy()
    private val today = "2026-07-04"

    private fun undatedTask(protected: Boolean = false) = SiftTask(
        pageId = "page-1",
        mappingId = "map-1",
        title = "Test task",
        priorityOptionId = "opt-soon",
        priorityOptionName = "Soon",
        bucketOptionId = null,
        bucketOptionName = null,
        isDone = false,
        due = null,
        isProtected = protected,
    )

    private fun datedTask(due: String = "2026-07-10", protected: Boolean = false, rescheduleCount: Int = 0) = SiftTask(
        pageId = "page-2",
        mappingId = "map-1",
        title = "Dated task",
        priorityOptionId = "opt-soon",
        priorityOptionName = "Soon",
        bucketOptionId = null,
        bucketOptionName = null,
        isDone = false,
        due = due,
        isProtected = protected,
        rescheduleCount = rescheduleCount,
    )

    @Test
    fun `undated unprotected move proceeds`() {
        val result = policy.evaluateMove(undatedTask(), PriorityKey.LATER, PriorityKey.SOON, today)
        assertTrue(result is PolicyDecision.Proceed)
    }

    @Test
    fun `undated protected move downward triggers friction`() {
        val result = policy.evaluateMove(undatedTask(protected = true), PriorityKey.LATER, PriorityKey.SOON, today)
        assertTrue(result is PolicyDecision.ProtectedFriction)
    }

    @Test
    fun `undated protected move upward proceeds`() {
        val result = policy.evaluateMove(undatedTask(protected = true), PriorityKey.TODAY, PriorityKey.SOON, today)
        assertTrue(result is PolicyDecision.Proceed)
    }

    @Test
    fun `dated task move triggers redirect`() {
        val result = policy.evaluateMove(datedTask(), PriorityKey.LATER, PriorityKey.SOON, today)
        assertTrue(result is PolicyDecision.RedirectPrompt)
    }

    @Test
    fun `snooze on protected task triggers friction`() {
        val result = policy.evaluateSnooze(datedTask(protected = true), today)
        assertTrue(result is PolicyDecision.ProtectedFriction)
    }

    @Test
    fun `snooze on unprotected task proceeds`() {
        val result = policy.evaluateSnooze(undatedTask(), today)
        assertTrue(result is PolicyDecision.Proceed)
    }

    @Test
    fun `date change to imminent with prior reschedules triggers safety catch`() {
        val task = datedTask(due = "2026-08-01", rescheduleCount = 2)
        val result = policy.evaluateDateChange(task, "2026-07-04", today)
        assertTrue(result is PolicyDecision.SafetyCatch)
    }

    @Test
    fun `date change to imminent without prior reschedules proceeds`() {
        val task = datedTask(due = "2026-08-01", rescheduleCount = 0)
        val result = policy.evaluateDateChange(task, "2026-07-04", today)
        assertTrue(result is PolicyDecision.Proceed)
    }

    @Test
    fun `date change to non-imminent proceeds`() {
        val task = datedTask(due = "2026-07-10", rescheduleCount = 3)
        val result = policy.evaluateDateChange(task, "2026-08-15", today)
        assertTrue(result is PolicyDecision.Proceed)
    }
}
