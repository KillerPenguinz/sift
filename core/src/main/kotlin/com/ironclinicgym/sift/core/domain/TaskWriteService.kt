package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.domain.ports.TaskCache
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceEngine
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceText
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.mapping.PropertyType
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.NotionException
import com.ironclinicgym.sift.core.notion.NotionParser
import com.ironclinicgym.sift.core.notion.NotionPropertyWriter
import com.ironclinicgym.sift.core.notion.NotionPropertyWriter.PropertyEdit

/**
 * The plain write layer: add, edit, complete, remove, move, snooze, undo — all written back to
 * Notion through the Phase 1 mapping. UI-agnostic and Compose-free, so Phase 4 AI capture reuses
 * it unchanged.
 *
 * Every operation is **optimistic then reconciled**: it mutates the local cache first for an
 * instant board, performs the Notion write, and on failure rolls the cache back and returns an
 * actionable, hyphen-free error (preserving the user's input for add/edit). Property values are
 * always routed through [NotionPropertyWriter] by [Role], so no write ever names a Notion
 * property directly. Idempotency lives in [add]: page creation does not auto-retry ambiguous
 * failures ([NotionClient] uses a non-retrying backoff for it); instead we reconcile by title so
 * a flaky connection never duplicates a task.
 *
 * No Two Axis Model behavior lives here: [move] simply updates the priority, with no date logic,
 * pinning, protected flag, or mismatch prompt. That layer can wrap this path later untouched.
 */
class TaskWriteService(
    private val notion: NotionClient,
    private val mappingStore: MappingStore,
    private val taskCache: TaskCache,
    private val localState: TaskLocalStateStore,
    private val recurrence: RecurrenceEngine,
    /** Local calendar day as YYYY-MM-DD; injected so core stays free of java.time. */
    private val todayIso: () -> String,
    /** Fresh unique id for optimistic temp rows; injected for deterministic tests. */
    private val newId: () -> String,
) {

    // ---- Add ----

    suspend fun add(draft: TaskDraft): WriteResult {
        val mapping = mappingFor(draft.mappingId)
            ?: return WriteResult.Failure("This board is not connected yet. Please finish setup and try again.", draft)
        if (!draft.recurrenceRule.isNullOrBlank() && !mapping.hasRecurrenceFields) {
            return WriteResult.NeedsRecurrenceConsent
        }

        val properties = NotionPropertyWriter.buildProperties(mapping, addEdits(draft))
        val tempId = "pending-${newId()}"
        taskCache.upsert(draftToTask(draft, tempId))

        val pageId = try {
            doCreate(mapping, properties, draft.title)
        } catch (e: NotionException) {
            taskCache.delete(tempId)
            return WriteResult.Failure(addFailureMessage(e), draft)
        }
        taskCache.delete(tempId)
        taskCache.upsert(draftToTask(draft, pageId))
        return WriteResult.Success(pageId)
    }

    // ---- Edit ----

    suspend fun edit(pageId: String, edits: TaskEdits): WriteResult {
        if (edits.isEmpty) return WriteResult.Success(pageId)
        val mapping = mappingFor(mappingIdOf(pageId) ?: return notFound()) ?: return notFound()
        val cur = current(mapping, pageId) ?: return notFound()
        if (edits.recurrenceRule?.value?.isNotBlank() == true && !mapping.hasRecurrenceFields) {
            return WriteResult.NeedsRecurrenceConsent
        }

        val properties = NotionPropertyWriter.buildProperties(mapping, editEdits(edits))
        val updated = applyEdits(cur, edits)
        taskCache.upsert(updated)
        return try {
            notion.updatePage(pageId, properties)
            WriteResult.Success(pageId)
        } catch (e: NotionException) {
            taskCache.upsert(cur) // roll back
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not save your changes. Your edits are still here." })
        }
    }

    // ---- Complete (distinct from Remove) ----

    suspend fun complete(pageId: String): WriteResult {
        val mapping = mappingFor(mappingIdOf(pageId) ?: return notFound()) ?: return notFound()
        val cur = current(mapping, pageId) ?: return notFound()
        val statusEdit = completeStatusEdit(mapping)
            ?: return WriteResult.Failure("Sift could not tell which status means done. Please re map your status property in settings.")

        val today = todayIso()
        // Optimistic: mark done and record the local "completed today" marker (never a Notion column).
        taskCache.upsert(cur.copy(isDone = true))
        localState.upsert(TaskLocalState(pageId, mapping.id, completedOnIso = today, manualSortIndex = cur.manualSortIndex))

        return try {
            notion.updatePage(pageId, NotionPropertyWriter.buildProperties(mapping, listOf(statusEdit)))
            // Recurrence: completing generates the next occurrence with the next due date.
            val spawnedId = spawnNextOccurrence(mapping, cur)
            WriteResult.Success(
                pageId,
                UndoToken(
                    label = "Task completed",
                    highlightPageId = pageId,
                    inverse = { reopen(mapping, cur, spawnedId) },
                ),
            )
        } catch (e: NotionException) {
            taskCache.upsert(cur) // roll back
            localState.upsert(TaskLocalState(pageId, mapping.id, completedOnIso = null, manualSortIndex = cur.manualSortIndex))
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not complete the task. Please try again." })
        }
    }

    /** Undo of complete: reopen, clear the local marker, and drop any spawned occurrence. */
    private suspend fun reopen(mapping: DatabaseMapping, cur: SiftTask, spawnedId: String?): WriteResult {
        return try {
            notion.updatePage(cur.pageId, NotionPropertyWriter.buildProperties(mapping, listOf(reopenStatusEdit(mapping))))
            localState.upsert(TaskLocalState(cur.pageId, mapping.id, completedOnIso = null, manualSortIndex = cur.manualSortIndex))
            taskCache.upsert(cur.copy(isDone = false))
            if (spawnedId != null) {
                runCatching { notion.archivePage(spawnedId, archived = true) }
                taskCache.delete(spawnedId)
            }
            WriteResult.Success(cur.pageId)
        } catch (e: NotionException) {
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not undo. Please try again." })
        }
    }

    // ---- Remove (archive to Notion trash, reversible) ----

    suspend fun remove(pageId: String): WriteResult {
        val mapping = mappingFor(mappingIdOf(pageId) ?: return notFound()) ?: return notFound()
        val cur = current(mapping, pageId) ?: return notFound()
        taskCache.delete(pageId) // optimistic

        return try {
            notion.archivePage(pageId, archived = true)
            WriteResult.Success(
                pageId,
                UndoToken(
                    label = "Task removed",
                    highlightPageId = pageId,
                    inverse = {
                        try {
                            notion.archivePage(pageId, archived = false)
                            taskCache.upsert(cur)
                            WriteResult.Success(pageId)
                        } catch (e: NotionException) {
                            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not restore the task." })
                        }
                    },
                ),
            )
        } catch (e: NotionException) {
            taskCache.upsert(cur) // roll back
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not remove the task. It is still here." })
        }
    }

    // ---- Move between priorities (plain: just updates the priority) ----

    suspend fun move(pageId: String, toPriorityOptionName: String, toPriorityOptionId: String?): WriteResult {
        val mapping = mappingFor(mappingIdOf(pageId) ?: return notFound()) ?: return notFound()
        val cur = current(mapping, pageId) ?: return notFound()
        val edit = PropertyEdit.SelectByName(Role.PRIORITY, toPriorityOptionName)
        taskCache.upsert(cur.copy(priorityOptionId = toPriorityOptionId, priorityOptionName = toPriorityOptionName))

        return try {
            notion.updatePage(pageId, NotionPropertyWriter.buildProperties(mapping, listOf(edit)))
            WriteResult.Success(
                pageId,
                UndoToken(
                    label = "Task moved",
                    highlightPageId = pageId,
                    inverse = { restorePriority(mapping, cur) },
                ),
            )
        } catch (e: NotionException) {
            taskCache.upsert(cur) // roll back
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not move the task. Please try again." })
        }
    }

    private suspend fun restorePriority(mapping: DatabaseMapping, cur: SiftTask): WriteResult = try {
        val edit = PropertyEdit.SelectByName(Role.PRIORITY, cur.priorityOptionName)
        notion.updatePage(cur.pageId, NotionPropertyWriter.buildProperties(mapping, listOf(edit)))
        taskCache.upsert(cur)
        WriteResult.Success(cur.pageId)
    } catch (e: NotionException) {
        WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not undo the move." })
    }

    /** Manual drag order within a priority. Purely local state; no Notion write, no rollback. */
    suspend fun setManualOrder(mappingId: String, orderedPageIds: List<String>) {
        orderedPageIds.forEachIndexed { index, id ->
            val existing = localState.get(id)
            localState.upsert(
                TaskLocalState(id, mappingId, completedOnIso = existing?.completedOnIso, manualSortIndex = index),
            )
        }
    }

    // ---- Snooze / defer ----

    suspend fun snooze(pageId: String, decision: SnoozeDecision): WriteResult {
        val mapping = mappingFor(mappingIdOf(pageId) ?: return notFound()) ?: return notFound()
        val cur = current(mapping, pageId) ?: return notFound()
        return when (decision) {
            is SnoozeDecision.AlreadyLowest -> WriteResult.AtLowestPriority
            is SnoozeDecision.BumpToPriority -> {
                taskCache.upsert(cur.copy(priorityOptionId = decision.priorityOptionId, priorityOptionName = decision.priorityOptionName))
                writeOrRollback(pageId, cur, PropertyEdit.SelectByName(Role.PRIORITY, decision.priorityOptionName), mapping)
            }
            is SnoozeDecision.SetDate -> {
                taskCache.upsert(cur.copy(due = decision.iso))
                writeOrRollback(pageId, cur, PropertyEdit.DateValue(Role.DUE_DATE, decision.iso), mapping)
            }
        }
    }

    private suspend fun writeOrRollback(pageId: String, cur: SiftTask, edit: PropertyEdit, mapping: DatabaseMapping): WriteResult =
        try {
            notion.updatePage(pageId, NotionPropertyWriter.buildProperties(mapping, listOf(edit)))
            WriteResult.Success(pageId)
        } catch (e: NotionException) {
            taskCache.upsert(cur)
            WriteResult.Failure(e.message.orEmpty().ifBlank { "Could not snooze the task. Please try again." })
        }

    // ---- internals ----

    /** Create with reconcile: on an ambiguous failure, adopt an existing page rather than duplicate. */
    private suspend fun doCreate(mapping: DatabaseMapping, properties: kotlinx.serialization.json.JsonObject, title: String): String =
        try {
            notion.createPage(mapping.ref.dataSourceId, properties).pageId
        } catch (e: NotionException) {
            // Auth / validation are definitive: no page was created, do not reconcile.
            if (e is NotionException.Unauthorized || e is NotionException.Validation) throw e
            reconcileCreate(mapping, title) ?: throw e
        }

    private suspend fun reconcileCreate(mapping: DatabaseMapping, title: String): String? {
        val titleId = mapping.propertyFor(Role.TITLE)?.propertyId ?: return null
        val known = taskCache.tasksFor(mapping.id).map { it.pageId }.toSet()
        return try {
            notion.findPagesByTitle(mapping.ref.dataSourceId, titleId, title)
                .map { NotionParser.parsePageId(it) }
                .firstOrNull { it.isNotBlank() && it !in known }
        } catch (e: NotionException) {
            null
        }
    }

    private suspend fun spawnNextOccurrence(mapping: DatabaseMapping, cur: SiftTask): String? {
        if (!cur.isRecurring) return null
        val rule = cur.recurrenceRule ?: return null
        val nextDue = recurrence.next(rule, cur.due ?: todayIso()) ?: return null
        val nextDraft = TaskDraft(
            mappingId = mapping.id,
            title = cur.title,
            priorityOptionName = cur.priorityOptionName,
            bucketOptionName = cur.bucketOptionName,
            dueIso = nextDue,
            notes = cur.notes,
            recurrenceRule = rule,
            priorityOptionId = cur.priorityOptionId,
            bucketOptionId = cur.bucketOptionId,
        )
        // Best effort: a failed spawn must not undo the completion the user already saw succeed.
        return try {
            val id = doCreate(mapping, NotionPropertyWriter.buildProperties(mapping, addEdits(nextDraft)), cur.title)
            taskCache.upsert(draftToTask(nextDraft, id))
            id
        } catch (e: NotionException) {
            null
        }
    }

    private fun addEdits(draft: TaskDraft): List<PropertyEdit> = buildList {
        add(PropertyEdit.TitleText(draft.title))
        draft.priorityOptionName?.let { add(PropertyEdit.SelectByName(Role.PRIORITY, it)) }
        draft.bucketOptionName?.let { add(PropertyEdit.SelectByName(Role.BUCKET, it)) }
        draft.dueIso?.let { add(PropertyEdit.DateValue(Role.DUE_DATE, it)) }
        draft.notes?.takeIf { it.isNotBlank() }?.let { add(PropertyEdit.RichText(Role.NOTES, it)) }
        draft.recurrenceRule?.takeIf { it.isNotBlank() }?.let {
            add(PropertyEdit.RichText(Role.RECURRENCE_RULE, it))
            add(PropertyEdit.RichText(Role.RECURRENCE_DISPLAY, RecurrenceText.describe(it)))
        }
        draft.labels?.let { add(PropertyEdit.Labels(it)) }
    }

    private fun editEdits(e: TaskEdits): List<PropertyEdit> = buildList {
        e.title?.let { add(PropertyEdit.TitleText(it.value)) }
        e.priorityOptionName?.let { add(PropertyEdit.SelectByName(Role.PRIORITY, it.value)) }
        e.bucketOptionName?.let { add(PropertyEdit.SelectByName(Role.BUCKET, it.value)) }
        e.dueIso?.let { add(PropertyEdit.DateValue(Role.DUE_DATE, it.value)) }
        e.notes?.let { add(PropertyEdit.RichText(Role.NOTES, it.value ?: "")) }
        e.recurrenceRule?.let { field ->
            val rule = field.value
            if (rule.isNullOrBlank()) {
                add(PropertyEdit.RichText(Role.RECURRENCE_RULE, ""))
                add(PropertyEdit.RichText(Role.RECURRENCE_DISPLAY, ""))
            } else {
                add(PropertyEdit.RichText(Role.RECURRENCE_RULE, rule))
                add(PropertyEdit.RichText(Role.RECURRENCE_DISPLAY, RecurrenceText.describe(rule)))
            }
        }
        e.labels?.let { add(PropertyEdit.Labels(it.value)) }
    }

    private fun applyEdits(cur: SiftTask, e: TaskEdits): SiftTask = cur.copy(
        title = e.title?.value ?: cur.title,
        priorityOptionName = e.priorityOptionName?.value ?: cur.priorityOptionName,
        priorityOptionId = if (e.priorityOptionName != null) e.priorityOptionId else cur.priorityOptionId,
        bucketOptionName = e.bucketOptionName?.value ?: cur.bucketOptionName,
        bucketOptionId = if (e.bucketOptionName != null) e.bucketOptionId else cur.bucketOptionId,
        due = e.dueIso?.value ?: cur.due,
        notes = e.notes?.value ?: cur.notes,
        recurrenceRule = e.recurrenceRule?.value ?: cur.recurrenceRule,
        recurrenceDisplay = e.recurrenceRule?.let { it.value?.takeIf { r -> r.isNotBlank() }?.let(RecurrenceText::describe) }
            ?: cur.recurrenceDisplay,
        labels = e.labels?.value ?: cur.labels,
    )

    /** The status write that means "done": checkbox true, or the mapped done option by name. */
    private fun completeStatusEdit(mapping: DatabaseMapping): PropertyEdit? {
        val status = mapping.propertyFor(Role.STATUS) ?: return null
        return when (status.propertyType) {
            PropertyType.CHECKBOX -> PropertyEdit.Checkbox(Role.STATUS, true)
            PropertyType.STATUS -> mapping.doneStatus?.let { PropertyEdit.SelectByName(Role.STATUS, it.optionName) }
            else -> null
        }
    }

    /** Reopen: checkbox false, or clear the status. (Prior status option name is not retained.) */
    private fun reopenStatusEdit(mapping: DatabaseMapping): PropertyEdit {
        val status = mapping.propertyFor(Role.STATUS)
        return if (status?.propertyType == PropertyType.CHECKBOX) PropertyEdit.Checkbox(Role.STATUS, false)
        else PropertyEdit.SelectByName(Role.STATUS, null)
    }

    private fun draftToTask(draft: TaskDraft, pageId: String): SiftTask = SiftTask(
        pageId = pageId,
        mappingId = draft.mappingId,
        title = draft.title,
        priorityOptionId = draft.priorityOptionId,
        priorityOptionName = draft.priorityOptionName,
        bucketOptionId = draft.bucketOptionId,
        bucketOptionName = draft.bucketOptionName,
        isDone = false,
        due = draft.dueIso,
        notes = draft.notes,
        recurrenceRule = draft.recurrenceRule,
        recurrenceDisplay = draft.recurrenceRule?.takeIf { it.isNotBlank() }?.let(RecurrenceText::describe),
        labels = draft.labels ?: emptyList(),
    )

    private suspend fun mappingFor(mappingId: String): DatabaseMapping? =
        mappingStore.load().mappings.firstOrNull { it.id == mappingId }

    private suspend fun mappingIdOf(pageId: String): String? =
        mappingStore.load().mappings.firstOrNull { m -> taskCache.tasksFor(m.id).any { it.pageId == pageId } }?.id

    private suspend fun current(mapping: DatabaseMapping, pageId: String): SiftTask? =
        taskCache.tasksFor(mapping.id).firstOrNull { it.pageId == pageId }

    private fun addFailureMessage(e: NotionException): String = when (e) {
        is NotionException.Unauthorized -> "Sift has lost access to your Notion workspace. Please reconnect in settings."
        is NotionException.Validation -> "Notion rejected the task. Please check your fields and try again."
        else -> "Could not save the task. Your details are still here, please try again."
    }

    private fun notFound() = WriteResult.Failure("That task is no longer here. Please refresh and try again.")
}
