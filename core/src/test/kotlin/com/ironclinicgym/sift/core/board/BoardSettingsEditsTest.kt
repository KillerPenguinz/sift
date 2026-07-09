package com.ironclinicgym.sift.core.board

import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.DataSourceRef
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSettingsEditsTest {

    private fun mapping() = DatabaseMapping(
        id = "m", ref = DataSourceRef("w", "d", "ds"), label = "L", bindings = emptyList(),
        priorities = listOf(
            PriorityBinding("asap", "asap", 0, "ASAP"),
            PriorityBinding("today", "today", 1, "TODAY"),
            PriorityBinding("soon", "soon", 2, "SOON"),
        ),
        buckets = listOf(bucketBinding("work", "Work")),
    )

    // Helper to keep the buckets list simple in this test.
    private fun bucketBinding(id: String, name: String) =
        com.ironclinicgym.sift.core.mapping.BucketBinding(id, name, null)

    private fun settings() = BoardSettings.fromMapping(mapping())

    @Test fun `rename and recolor are display-only edits`() {
        val s = settings().renamePriority("asap", "Now").recolorPriority("asap", "TODAY")
        val b = s.priorities.first { it.id == "asap" }
        assertEquals("Now", b.displayName)
        assertEquals("TODAY", b.colorKey)
        assertEquals("asap", b.optionId) // still mapped to the Notion option
    }

    @Test fun `move reorders and reindexes`() {
        val s = settings().movePriority("soon", -1)
        assertEquals(listOf("asap", "soon", "today"), s.priorities.sortedBy { it.order }.map { it.id })
    }

    @Test fun `reorder applies a full ordered list`() {
        val s = settings().reorderPriorities(listOf("soon", "asap", "today"))
        assertEquals(listOf("soon", "asap", "today"), s.priorities.sortedBy { it.order }.map { it.id })
    }

    @Test fun `hide removes from Sift without deleting the mapping option`() {
        val s = settings().hidePriority("today")
        assertTrue(s.priorities.first { it.id == "today" }.hidden)
        // Hidden option is now surfaced as unmapped, offering re-add.
        assertTrue(unmappedOptions(mapping(), s).any { it.optionId == "today" })
    }

    @Test fun `unmapped options exclude visible priorities`() {
        val unmapped = unmappedOptions(mapping(), settings())
        assertTrue(unmapped.isEmpty())
    }

    @Test fun `add custom priority has no option and stays until aligned`() {
        val s = settings().addPriority(id = "custom-1", displayName = "Waiting", colorKey = "LATER")
        val b = s.priorities.first { it.id == "custom-1" }
        assertTrue(b.isUnmapped)
        val aligned = s.alignPriority("custom-1", "today", "today")
        assertFalse(aligned.priorities.first { it.id == "custom-1" }.isUnmapped)
    }

    @Test fun `remove deletes a custom priority but hides a mapped one`() {
        val withCustom = settings().addPriority(id = "custom-1", displayName = "Waiting", colorKey = "LATER")
        assertFalse(withCustom.removePriority("custom-1").priorities.any { it.id == "custom-1" })
        assertTrue(settings().removePriority("asap").priorities.first { it.id == "asap" }.hidden)
    }

    @Test fun `bucket recolor and icon edits apply by option id`() {
        val s = settings().recolorBucket("work", "PERSONAL").setBucketIcon("work", "fitness_center")
        val bucket = s.buckets.first { it.optionId == "work" }
        assertEquals("PERSONAL", bucket.colorKey)
        assertEquals("fitness_center", bucket.icon)
    }

    @Test fun `time gating toggles the global flag`() {
        assertTrue(settings().setTimeGating(true).timeGatingEnabled)
    }
}
