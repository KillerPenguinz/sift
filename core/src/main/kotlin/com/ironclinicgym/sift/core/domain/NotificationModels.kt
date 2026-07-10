package com.ironclinicgym.sift.core.domain

enum class NotificationTier { TRANSIENT, ACTIONABLE }

data class SiftNotification(
    val id: String,
    val message: String,
    val icon: String,
    val tier: NotificationTier,
    val actionLabel: String? = null,
    val actionRoute: String? = null,
    val isRead: Boolean = false,
    val timestamp: Long,
) {
    val showsBadge: Boolean get() = tier == NotificationTier.ACTIONABLE && !isRead

    /**
     * Whether this notification is stored in the notification center (actionable tier).
     * Read state is tracked separately via [isRead].
     */
    val persistsAsUnread: Boolean get() = tier == NotificationTier.ACTIONABLE
}

data class ActionHistoryEntry(
    val id: String,
    val description: String,
    val taskTitle: String,
    val taskPageId: String?,
    val timestamp: Long,
    val canUndo: Boolean = false,
    val isSynced: Boolean = false,
) {
    val effectiveCanUndo: Boolean get() = canUndo && !isSynced
}
