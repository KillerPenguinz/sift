package com.ironclinicgym.sift.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ironclinicgym.sift.core.domain.ActionHistoryEntry
import com.ironclinicgym.sift.core.domain.NotificationTier
import com.ironclinicgym.sift.core.domain.SiftNotification
import com.ironclinicgym.sift.core.domain.ports.ActionHistoryStore
import com.ironclinicgym.sift.core.domain.ports.NotificationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val message: String,
    val icon: String,
    val tier: String,
    val actionLabel: String?,
    val actionRoute: String?,
    val isRead: Int = 0,
    val timestamp: Long,
)

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE tier = 'ACTIONABLE' ORDER BY timestamp DESC LIMIT 50")
    fun observeActionable(): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM notifications WHERE timestamp < :beforeTimestamp")
    suspend fun deleteExpired(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND tier = 'ACTIONABLE'")
    suspend fun unreadCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0 AND tier = 'ACTIONABLE'")
    fun observeUnreadCount(): Flow<Int>
}

/**
 * Room-backed [NotificationStore]. Holds Sift-only notification center entries so they survive
 * process death; none of this ends up in the user's Notion database. Non-sensitive, like the
 * task cache.
 */
class RoomNotificationStore(private val dao: NotificationDao) : NotificationStore {
    override suspend fun insert(notification: SiftNotification) =
        dao.insert(notification.toEntity())

    override fun observeActionable(): Flow<List<SiftNotification>> =
        dao.observeActionable().map { list -> list.map { it.toDomain() } }

    override suspend fun markAllRead() = dao.markAllRead()
    override suspend fun markRead(id: String) = dao.markRead(id)
    override suspend fun deleteExpired(beforeTimestamp: Long) = dao.deleteExpired(beforeTimestamp)
    override suspend fun unreadCount(): Int = dao.unreadCount()
    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    private fun SiftNotification.toEntity() = NotificationEntity(
        id = id, message = message, icon = icon, tier = tier.name,
        actionLabel = actionLabel, actionRoute = actionRoute,
        isRead = if (isRead) 1 else 0, timestamp = timestamp,
    )

    private fun NotificationEntity.toDomain() = SiftNotification(
        id = id, message = message, icon = icon,
        tier = runCatching { NotificationTier.valueOf(tier) }.getOrDefault(NotificationTier.TRANSIENT),
        actionLabel = actionLabel, actionRoute = actionRoute,
        isRead = isRead != 0, timestamp = timestamp,
    )
}

@Entity(tableName = "action_history")
data class ActionHistoryEntity(
    @PrimaryKey val id: String,
    val description: String,
    val taskTitle: String,
    val taskPageId: String?,
    val timestamp: Long,
    val canUndo: Int = 0,
    val isSynced: Int = 0,
)

@Dao
interface ActionHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActionHistoryEntity)

    @Query("SELECT * FROM action_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActionHistoryEntity>>

    @Query("UPDATE action_history SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM action_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}

/**
 * Room-backed [ActionHistoryStore]. Holds the Sift-only log of recent user actions (with undo
 * and sync state) keyed by entry id; never written to the user's Notion database. Non-sensitive,
 * like the task cache.
 */
class RoomActionHistoryStore(private val dao: ActionHistoryDao) : ActionHistoryStore {
    override suspend fun insert(entry: ActionHistoryEntry) =
        dao.insert(entry.toEntity())

    override fun observeRecent(limit: Int): Flow<List<ActionHistoryEntry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)
    override suspend fun deleteOlderThan(timestamp: Long) = dao.deleteOlderThan(timestamp)

    private fun ActionHistoryEntry.toEntity() = ActionHistoryEntity(
        id = id, description = description, taskTitle = taskTitle,
        taskPageId = taskPageId, timestamp = timestamp,
        canUndo = if (canUndo) 1 else 0, isSynced = if (isSynced) 1 else 0,
    )

    private fun ActionHistoryEntity.toDomain() = ActionHistoryEntry(
        id = id, description = description, taskTitle = taskTitle,
        taskPageId = taskPageId, timestamp = timestamp,
        canUndo = canUndo != 0, isSynced = isSynced != 0,
    )
}
