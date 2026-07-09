package com.ironclinicgym.sift.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TaskLocalStateStore]. Holds Sift-only operational state keyed by Notion page id
 * (the "Completed today" marker and manual sort order) so none of it ends up as a column in the
 * user's Notion database. Non-sensitive, like the task cache.
 */
@Entity(tableName = "task_local_state")
data class TaskLocalStateEntity(
    @PrimaryKey val pageId: String,
    val mappingId: String,
    val completedOnIso: String?,
    val manualSortIndex: Int?,
    val pinLevel: Int = 0,
    val isProtected: Int = 0,
    val isBrainDump: Int = 0,
    val isBlocked: Int = 0,
    val reviewDateIso: String? = null,
    val rescheduleCount: Int = 0,
    val createdBy: String = "sift",
)

@Dao
interface TaskLocalStateDao {
    @Query("SELECT * FROM task_local_state WHERE pageId = :pageId")
    suspend fun get(pageId: String): TaskLocalStateEntity?

    @Query("SELECT * FROM task_local_state WHERE mappingId = :mappingId")
    fun observe(mappingId: String): Flow<List<TaskLocalStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: TaskLocalStateEntity)

    @Query("DELETE FROM task_local_state WHERE pageId = :pageId")
    suspend fun delete(pageId: String)
}

/** Adapts the DAO to the core port, converting entity <-> domain. */
class RoomTaskLocalStateStore(private val dao: TaskLocalStateDao) : TaskLocalStateStore {

    override suspend fun get(pageId: String): TaskLocalState? = dao.get(pageId)?.toDomain()

    override suspend fun upsert(state: TaskLocalState) = dao.upsert(state.toEntity())

    override suspend fun delete(pageId: String) = dao.delete(pageId)

    override fun observe(mappingId: String): Flow<List<TaskLocalState>> =
        dao.observe(mappingId).map { list -> list.map { it.toDomain() } }

    private fun TaskLocalState.toEntity() = TaskLocalStateEntity(
        pageId = pageId,
        mappingId = mappingId,
        completedOnIso = completedOnIso,
        manualSortIndex = manualSortIndex,
        pinLevel = pinLevel,
        isProtected = if (isProtected) 1 else 0,
        isBrainDump = if (isBrainDump) 1 else 0,
        isBlocked = if (isBlocked) 1 else 0,
        reviewDateIso = reviewDateIso,
        rescheduleCount = rescheduleCount,
        createdBy = createdBy,
    )

    private fun TaskLocalStateEntity.toDomain() = TaskLocalState(
        pageId = pageId,
        mappingId = mappingId,
        completedOnIso = completedOnIso,
        manualSortIndex = manualSortIndex,
        pinLevel = pinLevel,
        isProtected = isProtected != 0,
        isBrainDump = isBrainDump != 0,
        isBlocked = isBlocked != 0,
        reviewDateIso = reviewDateIso,
        rescheduleCount = rescheduleCount,
        createdBy = createdBy,
    )
}
