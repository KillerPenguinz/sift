package com.ironclinicgym.sift.core.domain.ports

import com.ironclinicgym.sift.core.domain.ActionHistoryEntry
import com.ironclinicgym.sift.core.domain.SiftNotification
import kotlinx.coroutines.flow.Flow

interface NotificationStore {
    suspend fun insert(notification: SiftNotification)
    fun observeActionable(): Flow<List<SiftNotification>>
    suspend fun markAllRead()
    suspend fun markRead(id: String)
    suspend fun deleteExpired(beforeTimestamp: Long)
    suspend fun unreadCount(): Int
    fun observeUnreadCount(): Flow<Int>
}

interface ActionHistoryStore {
    suspend fun insert(entry: ActionHistoryEntry)
    fun observeRecent(limit: Int = 50): Flow<List<ActionHistoryEntry>>
    suspend fun markSynced(ids: List<String>)

    /** Marks every entry synced. Called after a successful refresh: Notion is authoritative from then on. */
    suspend fun markAllSynced()

    /** The newest entry still undoable (canUndo and not yet synced), matching the active undo token. */
    suspend fun mostRecentUndoable(): ActionHistoryEntry?

    suspend fun deleteOlderThan(timestamp: Long)
}
