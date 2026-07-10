package com.ironclinicgym.sift.core.domain

import org.junit.Assert.*
import org.junit.Test

class NotificationModelsTest {

    @Test
    fun `transient notification has no badge`() {
        val n = SiftNotification(
            id = "1",
            message = "Added to asap.",
            icon = "check_circle",
            tier = NotificationTier.TRANSIENT,
            timestamp = 1000L,
        )
        assertFalse(n.showsBadge)
        assertFalse(n.persistsAsUnread)
    }

    @Test
    fun `actionable notification shows badge and persists`() {
        val n = SiftNotification(
            id = "2",
            message = "You have 8 tasks in ASAP.",
            icon = "notifications",
            tier = NotificationTier.ACTIONABLE,
            actionLabel = "Go to ASAP",
            timestamp = 1000L,
        )
        assertTrue(n.showsBadge)
        assertTrue(n.persistsAsUnread)
    }

    @Test
    fun `action history entry tracks undo availability`() {
        val entry = ActionHistoryEntry(
            id = "1",
            description = "Added task",
            taskTitle = "Buy milk",
            taskPageId = "page-1",
            timestamp = 1000L,
            canUndo = true,
            isSynced = false,
        )
        assertTrue(entry.canUndo)
        assertFalse(entry.isSynced)
    }

    @Test
    fun `action history entry loses undo after sync`() {
        val entry = ActionHistoryEntry(
            id = "1",
            description = "Removed task",
            taskTitle = "Buy milk",
            taskPageId = "page-1",
            timestamp = 1000L,
            canUndo = true,
            isSynced = true,
        )
        assertFalse(entry.effectiveCanUndo)
    }
}
