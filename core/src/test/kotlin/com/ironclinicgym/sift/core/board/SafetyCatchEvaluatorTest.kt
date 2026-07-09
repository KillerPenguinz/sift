package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import org.junit.Assert.*
import org.junit.Test

class SafetyCatchEvaluatorTest {

    private fun makeTask(pageId: String, due: String) = SiftTask(
        pageId = pageId,
        mappingId = "m1",
        title = "Test",
        priorityOptionId = null,
        priorityOptionName = null,
        bucketOptionId = null,
        bucketOptionName = null,
        isDone = false,
        due = due,
    )

    private fun makeState(pageId: String, firedBand: String? = null) = TaskLocalState(
        pageId = pageId,
        mappingId = "m1",
        safetyCatchFiredBand = firedBand,
    )

    @Test fun `fires for overdue task with no prior record`() {
        val task = makeTask("p1", "2026-07-08")
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(1, eval.toFire.size)
        assertEquals("p1", eval.toFire[0].first.pageId)
        assertEquals("asap", eval.toFire[0].second)
    }

    @Test fun `fires for due-today task with no prior record`() {
        val task = makeTask("p1", "2026-07-09")
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(1, eval.toFire.size)
        assertEquals("today", eval.toFire[0].second)
    }

    @Test fun `does not fire if safetyCatchFiredBand matches current band`() {
        val task = makeTask("p1", "2026-07-09")
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(0, eval.toFire.size)
    }

    @Test fun `fires again if band changes from today to asap`() {
        val task = makeTask("p1", "2026-07-08")
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(1, eval.toFire.size)
        assertEquals("asap", eval.toFire[0].second)
    }

    @Test fun `clears record when task is no longer imminent`() {
        val task = makeTask("p1", "2026-07-15")
        val eval = evaluateSafetyCatch(
            listOf(task),
            mapOf("p1" to makeState("p1", firedBand = "today")),
            "2026-07-09"
        )
        assertEquals(0, eval.toFire.size)
        assertEquals(listOf("p1"), eval.toClear)
    }

    @Test fun `does not fire for future tasks`() {
        val task = makeTask("p1", "2026-07-20")
        val eval = evaluateSafetyCatch(listOf(task), mapOf("p1" to makeState("p1")), "2026-07-09")
        assertEquals(0, eval.toFire.size)
        assertEquals(0, eval.toClear.size)
    }

    @Test fun `queues multiple imminent tasks`() {
        val t1 = makeTask("p1", "2026-07-08")
        val t2 = makeTask("p2", "2026-07-09")
        val eval = evaluateSafetyCatch(
            listOf(t1, t2),
            mapOf("p1" to makeState("p1"), "p2" to makeState("p2")),
            "2026-07-09"
        )
        assertEquals(2, eval.toFire.size)
    }
}
