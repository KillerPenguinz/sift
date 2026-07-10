package com.ironclinicgym.sift.core.domain.ports

import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.core.oauth.TokenBundle
import kotlinx.coroutines.flow.Flow

/**
 * The ports the platform layer implements. Core depends only on these interfaces, never on
 * Room, the Android Keystore, DataStore, or WorkManager. This is the seam that keeps all
 * business logic KMP-ready; any implementation here that imported a UI/platform type into
 * core would violate the architecture.
 */

/** Secure storage for the OAuth credentials. Implemented with Keystore-backed storage. */
interface TokenStore {
    suspend fun save(bundle: TokenBundle)
    suspend fun load(): TokenBundle?
    suspend fun clear()
}

/** Secure, persistent storage for the mapping set (spec requires secure storage). */
interface MappingStore {
    suspend fun load(): MappingSet
    suspend fun save(set: MappingSet)
    fun observe(): Flow<MappingSet>
}

/** Local cache of board records, keyed by mapping id. Non-sensitive; backed by Room. */
interface TaskCache {
    suspend fun replaceAll(mappingId: String, tasks: List<SiftTask>)
    suspend fun tasksFor(mappingId: String): List<SiftTask>
    fun observe(mappingId: String): Flow<List<SiftTask>>
    suspend fun clear(mappingId: String)

    /** Insert or replace a single record (optimistic write-through). */
    suspend fun upsert(task: SiftTask)

    /** Remove a single record by page id (optimistic remove / rollback of an optimistic add). */
    suspend fun delete(pageId: String)
}

/**
 * Sift-only operational state that must never touch the user's Notion database (spec: keep the
 * database clean). Keyed by the Notion page id. Holds the "Completed today" marker, manual sort
 * order, and the per-op client token used to make page creation idempotent on retry. Backed by
 * Room in the platform layer.
 */
interface TaskLocalStateStore {
    suspend fun get(pageId: String): TaskLocalState?
    suspend fun upsert(state: TaskLocalState)
    suspend fun delete(pageId: String)
    fun observe(mappingId: String): Flow<List<TaskLocalState>>
}

/** One page's local operational state. All fields optional; absence means "default". */
data class TaskLocalState(
    val pageId: String,
    val mappingId: String,
    val completedOnIso: String? = null,
    val manualSortIndex: Int? = null,
    val pinLevel: Int = 0,
    val isProtected: Boolean = false,
    val isBrainDump: Boolean = false,
    val isBlocked: Boolean = false,
    val reviewDateIso: String? = null,
    val rescheduleCount: Int = 0,
    val createdBy: String = "sift",
    val safetyCatchFiredBand: String? = null,
    val lastSafetyCatchShownAt: Long? = null,
)

/**
 * Per-database consent for Sift adding its recurrence columns to a bring-your-own database.
 * Automatic setup databases ship the fields, so they are implicitly [Consent.GRANTED].
 */
interface RecurrenceConsentStore {
    suspend fun get(mappingId: String): Consent
    suspend fun set(mappingId: String, consent: Consent)
}

enum class Consent { UNASKED, GRANTED, DECLINED }

/**
 * Persistent, non-sensitive board display settings (priority/bucket config, minimize, layout,
 * schedules) keyed by mapping id. Backed by DataStore in the platform layer.
 */
interface BoardSettingsStore {
    fun observe(mappingId: String): Flow<BoardSettings?>
    suspend fun load(mappingId: String): BoardSettings?
    suspend fun save(settings: BoardSettings)
}
