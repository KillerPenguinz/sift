package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.ports.TaskCache
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.mapping.BucketBinding
import com.ironclinicgym.sift.core.mapping.DataSourceRef
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TaskCache] for write-layer tests. */
class FakeTaskCache : TaskCache {
    val tasks = MutableStateFlow<List<SiftTask>>(emptyList())
    override suspend fun replaceAll(mappingId: String, tasks: List<SiftTask>) {
        this.tasks.value = this.tasks.value.filterNot { it.mappingId == mappingId } + tasks
    }
    override suspend fun tasksFor(mappingId: String): List<SiftTask> = tasks.value.filter { it.mappingId == mappingId }
    override fun observe(mappingId: String): Flow<List<SiftTask>> = tasks.map { l -> l.filter { it.mappingId == mappingId } }
    override suspend fun clear(mappingId: String) { tasks.value = tasks.value.filterNot { it.mappingId == mappingId } }
    override suspend fun upsert(task: SiftTask) { tasks.value = tasks.value.filterNot { it.pageId == task.pageId } + task }
    override suspend fun delete(pageId: String) { tasks.value = tasks.value.filterNot { it.pageId == pageId } }
}

/** In-memory [TaskLocalStateStore] for write-layer tests. */
class FakeLocalStateStore : TaskLocalStateStore {
    val states = MutableStateFlow<List<TaskLocalState>>(emptyList())
    override suspend fun get(pageId: String): TaskLocalState? = states.value.firstOrNull { it.pageId == pageId }
    override suspend fun upsert(state: TaskLocalState) { states.value = states.value.filterNot { it.pageId == state.pageId } + state }
    override suspend fun delete(pageId: String) { states.value = states.value.filterNot { it.pageId == pageId } }
    override fun observe(mappingId: String): Flow<List<TaskLocalState>> = states.map { l -> l.filter { it.mappingId == mappingId } }
}

/** A mapping fixture: title + select priority/bucket + checkbox status (the automatic setup shape). */
fun mappingFixture(withRecurrence: Boolean = false): DatabaseMapping = DatabaseMapping(
    id = "m1",
    ref = DataSourceRef("ws", "db", "ds1"),
    label = "Tasks",
    bindings = buildList {
        add(RolePropertyBinding(Role.TITLE, "t", "Name", PropertyType.TITLE))
        add(RolePropertyBinding(Role.PRIORITY, "p", "Priority", PropertyType.SELECT))
        add(RolePropertyBinding(Role.BUCKET, "b", "Bucket", PropertyType.SELECT))
        add(RolePropertyBinding(Role.STATUS, "d", "Done", PropertyType.CHECKBOX))
        add(RolePropertyBinding(Role.DUE_DATE, "due", "Due", PropertyType.DATE))
        add(RolePropertyBinding(Role.NOTES, "n", "Notes", PropertyType.RICH_TEXT))
        if (withRecurrence) {
            add(RolePropertyBinding(Role.RECURRENCE_RULE, "rr", "Sift Recurrence", PropertyType.RICH_TEXT))
            add(RolePropertyBinding(Role.RECURRENCE_DISPLAY, "rd", "Sift Repeats", PropertyType.RICH_TEXT))
        }
    },
    priorities = listOf(
        PriorityBinding("asap-id", "asap", 0, "ASAP"),
        PriorityBinding("soon-id", "soon", 1, "SOON"),
    ),
    buckets = listOf(BucketBinding("work-id", "Work", "WORK")),
)

fun taskFixture(pageId: String = "page-1", isDone: Boolean = false, recurrenceRule: String? = null, due: String? = null) = SiftTask(
    pageId = pageId,
    mappingId = "m1",
    title = "Water the plants",
    priorityOptionId = "asap-id",
    priorityOptionName = "asap",
    bucketOptionId = "work-id",
    bucketOptionName = "Work",
    isDone = isDone,
    due = due,
    notes = null,
    recurrenceRule = recurrenceRule,
)
