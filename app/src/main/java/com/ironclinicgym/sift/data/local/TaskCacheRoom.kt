package com.ironclinicgym.sift.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.ports.TaskCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [TaskCache]. Holds only non-sensitive board records so the UI shows last known
 * data instantly; the OAuth token and mapping live in encrypted storage, not here.
 */
@Entity(tableName = "cached_tasks")
data class CachedTaskEntity(
    @PrimaryKey val pageId: String,
    val mappingId: String,
    val title: String,
    val priorityOptionId: String?,
    val priorityOptionName: String?,
    val bucketOptionId: String?,
    val bucketOptionName: String?,
    val isDone: Boolean,
    val due: String?,
    val notes: String?,
    // Recurrence is read back from Notion (Sift fields), so it is cached like any mapped value.
    val recurrenceRule: String? = null,
    val recurrenceDisplay: String? = null,
    val labels: String? = null,
)

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM cached_tasks WHERE mappingId = :mappingId")
    suspend fun tasksFor(mappingId: String): List<CachedTaskEntity>

    @Query("SELECT * FROM cached_tasks WHERE mappingId = :mappingId")
    fun observe(mappingId: String): Flow<List<CachedTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<CachedTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: CachedTaskEntity)

    @Query("DELETE FROM cached_tasks WHERE pageId = :pageId")
    suspend fun deleteByPageId(pageId: String)

    @Query("DELETE FROM cached_tasks WHERE mappingId = :mappingId")
    suspend fun clear(mappingId: String)

    @Transaction
    suspend fun replaceAll(mappingId: String, tasks: List<CachedTaskEntity>) {
        clear(mappingId)
        insertAll(tasks)
    }
}

@Database(
    entities = [CachedTaskEntity::class, TaskLocalStateEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class SiftDatabase : RoomDatabase() {
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun taskLocalStateDao(): TaskLocalStateDao
}

/**
 * v1 -> v2: Phase 3. Creates the Sift-only local operational state table (Completed today, manual
 * order) which cannot be rebuilt from Notion, and brings the task cache to the current schema.
 *
 * The cache is rebuildable from Notion, and some v1 databases carry the pre-refactor column names
 * `sourceOptionId` / `sourceOptionName` (from before the Priority/Bucket rename), so an additive
 * `ALTER TABLE` would leave the schema mismatching the entity. Instead we drop and recreate
 * `cached_tasks` to the exact current schema, which converges both the pre-refactor and clean v1
 * shapes; the next refresh repopulates it. The DDL below must match Room's generated TableInfo
 * (column names, TEXT/INTEGER affinity, NOT NULL, PRIMARY KEY) exactly.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS cached_tasks")
        db.execSQL(
            "CREATE TABLE `cached_tasks` (" +
                "`pageId` TEXT NOT NULL, `mappingId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`priorityOptionId` TEXT, `priorityOptionName` TEXT, " +
                "`bucketOptionId` TEXT, `bucketOptionName` TEXT, " +
                "`isDone` INTEGER NOT NULL, `due` TEXT, `notes` TEXT, " +
                "`recurrenceRule` TEXT, `recurrenceDisplay` TEXT, " +
                "PRIMARY KEY(`pageId`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_local_state` (" +
                "`pageId` TEXT NOT NULL, `mappingId` TEXT NOT NULL, " +
                "`completedOnIso` TEXT, `manualSortIndex` INTEGER, " +
                "PRIMARY KEY(`pageId`))",
        )
    }
}

/** v2 -> v3: adds the labels column for the LABELS role (select / multi-select). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_tasks ADD COLUMN labels TEXT")
    }
}

/** v3 -> v4: Phase 3.5 Two Axis Model local state columns. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN pinLevel INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN isProtected INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN isBrainDump INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN isBlocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN reviewDateIso TEXT")
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN rescheduleCount INTEGER NOT NULL DEFAULT 0")
    }
}

/** v4 -> v5: Task provenance — tracks whether a task was created via Sift or synced from Notion. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task_local_state ADD COLUMN createdBy TEXT NOT NULL DEFAULT 'sift'")
    }
}

/** v5 -> v6: Safety catch band tracking — records which urgency band last triggered the prompt. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE task_local_state ADD COLUMN safetyCatchFiredBand TEXT"
        )
    }
}

/** Adapts the Room DAO to the core [TaskCache] port, converting entity <-> domain. */
class RoomTaskCache(private val dao: TaskCacheDao) : TaskCache {

    override suspend fun replaceAll(mappingId: String, tasks: List<SiftTask>) =
        dao.replaceAll(mappingId, tasks.map { it.toEntity() })

    override suspend fun tasksFor(mappingId: String): List<SiftTask> =
        dao.tasksFor(mappingId).map { it.toDomain() }

    override fun observe(mappingId: String): Flow<List<SiftTask>> =
        dao.observe(mappingId).map { list -> list.map { it.toDomain() } }

    override suspend fun clear(mappingId: String) = dao.clear(mappingId)

    override suspend fun upsert(task: SiftTask) = dao.upsert(task.toEntity())

    override suspend fun delete(pageId: String) = dao.deleteByPageId(pageId)

    private fun SiftTask.toEntity() = CachedTaskEntity(
        pageId, mappingId, title, priorityOptionId, priorityOptionName,
        bucketOptionId, bucketOptionName, isDone, due, notes, recurrenceRule, recurrenceDisplay,
        labels = if (labels.isEmpty()) null else labels.joinToString(""),
    )

    // Local operational fields (completedOnIso, manualSortIndex) are merged in the repository from
    // the local state table, not stored here, so they default to null on read.
    private fun CachedTaskEntity.toDomain() = SiftTask(
        pageId = pageId,
        mappingId = mappingId,
        title = title,
        priorityOptionId = priorityOptionId,
        priorityOptionName = priorityOptionName,
        bucketOptionId = bucketOptionId,
        bucketOptionName = bucketOptionName,
        isDone = isDone,
        due = due,
        notes = notes,
        recurrenceRule = recurrenceRule,
        recurrenceDisplay = recurrenceDisplay,
        labels = labels?.split("")?.filter { it.isNotEmpty() } ?: emptyList(),
    )
}
