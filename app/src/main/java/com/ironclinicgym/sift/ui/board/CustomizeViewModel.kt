package com.ironclinicgym.sift.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.PriorityEdit
import com.ironclinicgym.sift.core.board.PrioritySchedule
import com.ironclinicgym.sift.core.board.PrioritySettings
import com.ironclinicgym.sift.core.board.addPriority
import com.ironclinicgym.sift.core.board.hidePriority
import com.ironclinicgym.sift.core.board.movePriority
import com.ironclinicgym.sift.core.board.nextColorKey
import com.ironclinicgym.sift.core.board.normalized
import com.ironclinicgym.sift.core.board.recolorPriority
import com.ironclinicgym.sift.core.board.recolorBucket
import com.ironclinicgym.sift.core.board.removePriority
import com.ironclinicgym.sift.core.board.reorderPriorities
import com.ironclinicgym.sift.core.board.renamePriority
import com.ironclinicgym.sift.core.board.renameBucket
import com.ironclinicgym.sift.core.board.resolvePrioritySettings
import com.ironclinicgym.sift.core.board.routePriorityEdit
import com.ironclinicgym.sift.core.board.setBucketIcon
import com.ironclinicgym.sift.core.board.setBucketSchedule
import com.ironclinicgym.sift.core.board.setFirstDayOfWeek
import com.ironclinicgym.sift.core.board.setLaterMaxDays
import com.ironclinicgym.sift.core.board.setNextMonthTime
import com.ironclinicgym.sift.core.board.setNextWeekTime
import com.ironclinicgym.sift.core.board.setOneDayLandmarkEnabled
import com.ironclinicgym.sift.core.board.setSingleColumnLimit
import com.ironclinicgym.sift.core.board.setSoonMaxDays
import com.ironclinicgym.sift.core.board.setTimeGating
import com.ironclinicgym.sift.core.board.setTomorrowTime
import com.ironclinicgym.sift.core.board.setUse24HourTime
import com.ironclinicgym.sift.core.board.setUseGlobalPrioritySettings
import com.ironclinicgym.sift.core.board.showPriority
import com.ironclinicgym.sift.core.board.toggleGlance
import com.ironclinicgym.sift.core.board.unmappedOptions
import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore
import com.ironclinicgym.sift.core.domain.ports.PrioritySettingsStore
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.data.repository.SiftRepository
import com.ironclinicgym.sift.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Board customization: priority and bucket editing plus time gating, all through the pure core
 * edits, persisted to the same [BoardSettingsStore] the board reads. Reconciles with the
 * Notion priority options in the active mapping so added/removed priorities align with data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomizeViewModel(
    private val repository: SiftRepository,
    private val store: BoardSettingsStore,
    private val prioritySettingsStore: PrioritySettingsStore,
) : ViewModel() {

    constructor(container: AppContainer) : this(container.repository, container.boardSettingsStore, container.prioritySettingsStore)

    val mapping: StateFlow<DatabaseMapping?> = repository.mappingSet
        .map { it.active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<BoardSettings?> = mapping
        .flatMapLatest { m ->
            if (m == null) flowOf(null) else store.observe(m.id).map { it ?: BoardSettings.fromMapping(m) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Notion priority options not shown on the board, for the "add to board" section. */
    val unmapped: StateFlow<List<PriorityBinding>> = combine(mapping, settings) { m, s ->
        if (m != null && s != null) unmappedOptions(m, s) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Serializes scope toggles and priority-timing edits so a toggle and the edits around it apply
    // in order, and concurrent edits never overwrite one another (single ViewModel instance).
    private val priorityEditMutex = Mutex()

    val globalPrioritySettings: StateFlow<PrioritySettings> = prioritySettingsStore.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrioritySettings.DEFAULT)

    /** The priority-timing settings this database actually uses right now (global or its override). */
    val effectivePriority: StateFlow<PrioritySettings?> = combine(settings, globalPrioritySettings) { s, g ->
        if (s == null) null else resolvePrioritySettings(g, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUseGlobalPrioritySettings(useGlobal: Boolean) {
        val current = settings.value ?: return
        viewModelScope.launch {
            priorityEditMutex.withLock {
                // Read BOTH authoritative values from their stores inside the lock: the StateFlows
                // (settings, globalPrioritySettings) lag DataStore writes, so seeding an override
                // from the flow could copy a stale global.
                val board = store.load(current.mappingId) ?: current
                val global = prioritySettingsStore.load().normalized()
                store.save(board.setUseGlobalPrioritySettings(useGlobal, global))
            }
        }
    }

    fun setSoonMaxDays(days: Int) = editPriority { it.setSoonMaxDays(days) }
    fun setLaterMaxDays(days: Int) = editPriority { it.setLaterMaxDays(days) }
    fun setTomorrowTime(hour: Int, minute: Int) = editPriority { it.setTomorrowTime(hour, minute) }
    fun setNextWeekTime(hour: Int, minute: Int) = editPriority { it.setNextWeekTime(hour, minute) }
    fun setNextMonthTime(hour: Int, minute: Int) = editPriority { it.setNextMonthTime(hour, minute) }
    fun setFirstDayOfWeek(day: Int) = editPriority { it.setFirstDayOfWeek(day) }

    /**
     * Route a priority-timing edit to the active scope through the pure [routePriorityEdit], under
     * the mutex. Reads BOTH authoritative values (board and global) from their stores inside the
     * lock rather than from the lagging StateFlows, so back-to-back edits never transform a stale
     * snapshot, and serializes concurrent edits so neither is lost.
     */
    private fun editPriority(transform: (PrioritySettings) -> PrioritySettings) {
        val current = settings.value ?: return
        viewModelScope.launch {
            priorityEditMutex.withLock {
                val board = store.load(current.mappingId) ?: current
                val global = prioritySettingsStore.load().normalized()
                when (val edit = routePriorityEdit(board, global, transform)) {
                    is PriorityEdit.SaveGlobal -> prioritySettingsStore.save(edit.global)
                    is PriorityEdit.SaveBoard -> store.save(edit.board)
                }
            }
        }
    }

    fun renamePriority(id: String, name: String) = edit { it.renamePriority(id, name) }
    fun recolorPriority(id: String, colorKey: String) = edit { it.recolorPriority(id, colorKey) }
    fun movePriority(id: String, delta: Int) = edit { it.movePriority(id, delta) }
    fun reorderPriorities(orderedIds: List<String>) = edit { it.reorderPriorities(orderedIds) }
    fun toggleGlance(id: String) = edit { it.toggleGlance(id) }
    fun removePriority(id: String) = edit { it.removePriority(id) }
    fun setTimeGating(enabled: Boolean) = edit { it.setTimeGating(enabled) }
    fun setUse24HourTime(enabled: Boolean) = edit { it.setUse24HourTime(enabled) }
    fun setSingleColumnLimit(limit: Int) = edit { it.setSingleColumnLimit(limit) }
    fun setOneDayLandmarkEnabled(name: String, enabled: Boolean) = edit { it.setOneDayLandmarkEnabled(name, enabled) }
    fun setSignalInflationEnabled(enabled: Boolean) = edit { it.copy(signalInflationEnabled = enabled) }
    fun setAsapInflationThreshold(value: Int) = edit { it.copy(asapInflationThreshold = value) }
    fun setProtectedInflationPercent(value: Int) = edit { it.copy(protectedInflationPercent = value) }
    fun setSampleMode(enabled: Boolean) = edit { it.copy(sampleMode = enabled) }
    fun renameBucket(optionId: String?, name: String) = edit { it.renameBucket(optionId, name) }
    fun recolorBucket(optionId: String?, colorKey: String?) = edit { it.recolorBucket(optionId, colorKey) }
    fun setBucketIcon(optionId: String?, icon: String) = edit { it.setBucketIcon(optionId, icon) }
    fun setBucketSchedule(optionId: String?, schedule: PrioritySchedule) = edit { it.setBucketSchedule(optionId, schedule) }

    /** Add a Notion option to the board, unhiding it if it was previously removed. */
    fun addFromOption(option: PriorityBinding) = edit { s ->
        val existing = s.priorities.firstOrNull { it.optionId == option.optionId }
        if (existing != null) s.showPriority(existing.id)
        else s.addPriority(option.optionId, option.optionName, option.priorityKey ?: s.nextColorKey(), option.optionId, option.optionName)
    }

    fun addCustomPriority(name: String) = edit { s ->
        s.addPriority(id = "custom-${UUID.randomUUID().toString().take(8)}", displayName = name, colorKey = s.nextColorKey())
    }

    private fun edit(transform: (BoardSettings) -> BoardSettings) {
        val current = settings.value ?: return
        viewModelScope.launch { store.save(transform(current)) }
    }
}
