package com.ironclinicgym.sift.data.repository

import android.util.Log
import com.ironclinicgym.sift.core.domain.NotionRecordMapper
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.TaskWriteService
import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.domain.ports.TaskCache
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.domain.ports.TokenStore
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.NotionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The app-facing repository. Coordinates the Notion client, the mapping, the cache, and the
 * token store. All Notion access flows through the active mapping, never a hardcoded name.
 */
class SiftRepository(
    private val notion: NotionClient,
    private val mappingStore: MappingStore,
    private val taskCache: TaskCache,
    private val localState: TaskLocalStateStore,
    private val tokenStore: TokenStore,
    /** The plain write layer. Exposed so ViewModels call it directly; all logic stays in core. */
    val writeService: TaskWriteService,
    externalScope: CoroutineScope,
) {
    sealed interface RefreshResult {
        data class Success(val count: Int) : RefreshResult
        data object NoMapping : RefreshResult
        data object NeedsReconnect : RefreshResult
        data class Failed(val message: String) : RefreshResult
    }

    // Shared once, kept warm: the encrypted mapping is decrypted a single time and every
    // collector (splash gate, board, settings) reuses the same value, avoiding repeated
    // decrypts and spurious Room re-subscriptions on the startup path.
    val mappingSet: StateFlow<MappingSet> = mappingStore.observe()
        .distinctUntilChanged()
        .stateIn(externalScope, SharingStarted.Eagerly, MappingSet.EMPTY)

    // Epoch millis of the last successful refresh, for the "last updated" pill. In-memory: on a
    // failed refresh the previous value stays, so the board still shows when it was last current.
    private val _lastSyncAt = MutableStateFlow<Long?>(null)
    val lastSyncAt: StateFlow<Long?> = _lastSyncAt

    /** Drives onboarding gating: the board is ready once an active mapping exists. */
    val isOnboarded: Flow<Boolean> = mappingSet.map { it.active != null }.distinctUntilChanged()

    // The board reads cache + local operational state merged: the cache holds Notion truth, the
    // local store holds the Completed-today marker and manual order (kept out of Notion). Merging
    // here keeps that split invisible to the projection, which just sees a complete SiftTask.
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTasks: Flow<List<SiftTask>> = mappingSet.flatMapLatest { set ->
        val active = set.active ?: return@flatMapLatest flowOf(emptyList())
        combine(taskCache.observe(active.id), localState.observe(active.id)) { tasks, states ->
            val byId = states.associateBy { it.pageId }
            tasks.map { task ->
                val s = byId[task.pageId] ?: return@map task
                task.copy(
                    completedOnIso = s.completedOnIso,
                    manualSortIndex = s.manualSortIndex,
                    pinLevel = s.pinLevel,
                    isProtected = s.isProtected,
                    isBrainDump = s.isBrainDump,
                    isBlocked = s.isBlocked,
                    reviewDateIso = s.reviewDateIso,
                    rescheduleCount = s.rescheduleCount,
                    createdBy = s.createdBy,
                )
            }
        }
    }

    /** Pull the active mapping's records into the cache. Pull-based, called manually or by Work. */
    suspend fun refresh(): RefreshResult {
        val mapping = mappingStore.load().active ?: return RefreshResult.NoMapping
        Log.d("BatteryDebug", "SiftRepository.refresh() starting for mapping=${mapping.id}")
        val start = System.currentTimeMillis()
        return try {
            val pages = notion.queryDataSource(mapping.ref.dataSourceId)
            val tasks = pages.map { NotionRecordMapper.toTask(it, mapping) }
            taskCache.replaceAll(mapping.id, tasks)
            _lastSyncAt.value = System.currentTimeMillis()
            Log.d("BatteryDebug", "SiftRepository.refresh() success: ${tasks.size} tasks in ${System.currentTimeMillis() - start}ms")
            RefreshResult.Success(tasks.size)
        } catch (e: NotionException.Unauthorized) {
            Log.w("BatteryDebug", "SiftRepository.refresh() unauthorized after ${System.currentTimeMillis() - start}ms")
            RefreshResult.NeedsReconnect
        } catch (e: NotionException) {
            Log.w("BatteryDebug", "SiftRepository.refresh() failed after ${System.currentTimeMillis() - start}ms: ${e.message}")
            RefreshResult.Failed(e.message.orEmpty())
        }
    }

    /** Read a task's Notion page body as plain-text lines. Empty for sample tasks or on error. */
    suspend fun pageBody(pageId: String): List<String> =
        if (pageId.startsWith("sample-")) emptyList()
        else runCatching { notion.retrievePageBlocks(pageId) }.getOrDefault(emptyList())

    /** One-shot read for the startup gate (avoids the shared flow's EMPTY seed ambiguity). */
    suspend fun isOnboardedNow(): Boolean = mappingStore.load().active != null

    suspend fun activeWorkspaceId(): String? = tokenStore.load()?.workspaceId

    suspend fun workspaceName(): String? = tokenStore.load()?.workspaceName

    /** Reconnect path: drop the token so the UI routes back through OAuth. Mapping is kept. */
    suspend fun disconnect() = tokenStore.clear()
}
