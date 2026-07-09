package com.ironclinicgym.sift.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironclinicgym.sift.core.board.BoardProjection
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.ClockSnapshot
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.board.SampleBoard
import com.ironclinicgym.sift.core.board.SignalInflation
import com.ironclinicgym.sift.core.board.InflationAlert
import com.ironclinicgym.sift.core.board.formatHourMinute
import com.ironclinicgym.sift.core.board.projectBoard
import com.ironclinicgym.sift.core.board.projectBrainDump
import com.ironclinicgym.sift.core.domain.PolicyDecision
import com.ironclinicgym.sift.core.domain.RecurrenceSetupService
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.TwoAxisPolicy
import com.ironclinicgym.sift.data.local.AppPreferencesDataStore
import com.ironclinicgym.sift.core.domain.SnoozeDecision
import com.ironclinicgym.sift.core.domain.TaskDraft
import com.ironclinicgym.sift.core.domain.TaskEdits
import com.ironclinicgym.sift.core.domain.UndoManager
import com.ironclinicgym.sift.core.domain.UndoToken
import com.ironclinicgym.sift.core.domain.WriteResult
import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.data.repository.SiftRepository
import com.ironclinicgym.sift.data.repository.SiftRepository.RefreshResult
import com.ironclinicgym.sift.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Drives the priority grid. Thin over the core: it combines the active mapping, cached tasks,
 * the persisted [BoardSettings], and a per-minute clock into a pure [BoardProjection] via
 * [projectBoard]. Cached data shows instantly; an on-open refresh keeps it current (a failed
 * refresh leaves the board intact and is surfaced non-blocking).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModel(
    private val repository: SiftRepository,
    private val settingsStore: BoardSettingsStore,
    private val undoManager: UndoManager,
    private val recurrenceSetup: RecurrenceSetupService,
    private val appPreferences: AppPreferencesDataStore,
    private val twoAxisPolicy: TwoAxisPolicy,
    private val localStateStore: TaskLocalStateStore,
) : ViewModel() {

    constructor(container: AppContainer) : this(
        container.repository,
        container.boardSettingsStore,
        container.undoManager,
        container.recurrenceSetup,
        container.appPreferences,
        TwoAxisPolicy(),
        container.localStateStore,
    )

    private val writeService get() = repository.writeService

    sealed interface RefreshUi {
        data object Idle : RefreshUi
        data object Refreshing : RefreshUi
        data object Reconnect : RefreshUi
        data class Error(val message: String) : RefreshUi
    }

    private val seededMappingIds = mutableSetOf<String>()

    // Re-emit periodically so time-gated priorities appear/disappear without needing a refresh.
    private val clock: Flow<ClockSnapshot> = flow {
        while (true) {
            val snap = nowSnapshot()
            Log.d("BatteryDebug", "clock tick: ${snap.todayIso} minute=${snap.minuteOfDay}")
            emit(snap)
            delay(300_000)
        }
    }

    private val activeMapping = repository.mappingSet.map { it.active }

    val showNotionReminder: StateFlow<Boolean> =
        combine(
            appPreferences.notionReminderDismissed(),
            activeMapping,
        ) { dismissed, mapping ->
            !dismissed && mapping?.bindings?.any { it.role == com.ironclinicgym.sift.core.mapping.Role.DUE_DATE } == true
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissNotionReminder() {
        viewModelScope.launch { appPreferences.dismissNotionReminder() }
    }

    val settings: StateFlow<BoardSettings?> = activeMapping
        .flatMapLatest { mapping ->
            if (mapping == null) flowOf(null)
            else settingsStore.observe(mapping.id).map { stored ->
                stored ?: BoardSettings.fromMapping(mapping).also { seedOnce(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val availableLabels: StateFlow<List<String>> =
        repository.activeTasks.map { tasks ->
            tasks.flatMap { it.labels }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val boardLabel: StateFlow<String> = activeMapping
        .map { it?.label.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val projection: StateFlow<BoardProjection?> =
        combine(settings.filterNotNull(), activeMapping, repository.activeTasks, clock) { settings, mapping, cached, now ->
            projectBoard(tasksFor(settings, mapping, cached, now.todayIso), settings, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refresh = MutableStateFlow<RefreshUi>(RefreshUi.Idle)
    val refresh: StateFlow<RefreshUi> = _refresh.asStateFlow()

    /** "Updated at 3:45 PM" from the last successful refresh, or null before any success. */
    val lastUpdatedLabel: StateFlow<String?> =
        repository.lastSyncAt.map { at ->
            at?.let { millis ->
                val elapsed = System.currentTimeMillis() - millis
                when {
                    elapsed < 60_000 -> "now"
                    elapsed < 3_600_000 -> "${elapsed / 60_000}m"
                    elapsed < 86_400_000 -> "${elapsed / 3_600_000}h"
                    else -> "${elapsed / 86_400_000}d"
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Load a task's Notion page body on demand (empty for sample tasks or on error). */
    suspend fun loadPageBody(pageId: String): List<String> = repository.pageBody(pageId)

    /** The single priority for the focused view, ignoring the minimize filter. */
    fun focusedPriority(priorityId: String): Flow<ProjectedPriority?> =
        combine(settings.filterNotNull(), activeMapping, repository.activeTasks, clock) { settings, mapping, cached, now ->
            projectBoard(tasksFor(settings, mapping, cached, now.todayIso), settings.copy(minimized = false), now)
                .priorities.firstOrNull { it.view.id == priorityId }
        }

    // Real cached tasks, or the generated overlay when the debug sample toggle is on.
    private fun tasksFor(settings: BoardSettings, mapping: DatabaseMapping?, cached: List<SiftTask>, todayIso: String): List<SiftTask> =
        if (settings.sampleMode && mapping != null) SampleBoard.generate(mapping, todayIso) else cached

    fun refresh() {
        if (_refresh.value is RefreshUi.Refreshing) {
            Log.d("BatteryDebug", "BoardViewModel.refresh() skipped, already refreshing")
            return
        }
        Log.d("BatteryDebug", "BoardViewModel.refresh() starting")
        _refresh.value = RefreshUi.Refreshing
        viewModelScope.launch {
            _refresh.value = when (val result = repository.refresh()) {
                is RefreshResult.Success -> {
                    _notification.value = BoardNotification.RefreshSuccess
                    RefreshUi.Idle
                }
                RefreshResult.NoMapping -> RefreshUi.Idle
                RefreshResult.NeedsReconnect -> RefreshUi.Reconnect
                is RefreshResult.Failed -> {
                    _notification.value = BoardNotification.RefreshError(result.message)
                    RefreshUi.Error(result.message)
                }
            }
            Log.d("BatteryDebug", "BoardViewModel.refresh() done: ${_refresh.value}")
        }
    }

    fun toggleMinimized() = update { it.copy(minimized = !it.minimized) }
    fun toggleLayout() = update { it.copy(twoColumn = !it.twoColumn) }

    private fun update(transform: (BoardSettings) -> BoardSettings) {
        val current = settings.value ?: return
        viewModelScope.launch { settingsStore.save(transform(current)) }
    }

    private fun seedOnce(default: BoardSettings) {
        if (!seededMappingIds.add(default.mappingId)) return
        viewModelScope.launch { settingsStore.save(default) }
    }

    private fun nowSnapshot(): ClockSnapshot {
        val now = LocalDateTime.now()
        return ClockSnapshot(
            dayOfWeek = now.dayOfWeek.value,
            minuteOfDay = now.hour * 60 + now.minute,
            todayIso = now.toLocalDate().toString(),
        )
    }

    // ---- Phase 3: writes ----

    /** The single active undo, and the page id to flash briefly, both from the core UndoManager. */
    val undoToken: StateFlow<UndoToken?> = undoManager.active
    val flashPageId: StateFlow<String?> = undoManager.flashPageId

    // Transient one-shot user messages (errors, snooze notices) for a snackbar.
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Add: suspends and returns the result so the sheet can stay open and preserve input on failure. */
    suspend fun addTask(draft: TaskDraft): WriteResult = writeService.add(draft)

    /** Edit an existing task's mapped fields. */
    suspend fun editTask(pageId: String, edits: TaskEdits): WriteResult = writeService.edit(pageId, edits)

    fun completeTask(pageId: String) = runAction { writeService.complete(pageId) }
    fun removeTask(pageId: String) = runAction { writeService.remove(pageId) }
    fun moveTask(pageId: String, toName: String, toId: String?) {
        val task = findTask(pageId) ?: return runAction { writeService.move(pageId, toName, toId) }
        val toPriorityKey = settings.value?.priorities
            ?.firstOrNull { it.optionId == toId }?.let { runCatching { PriorityKey.valueOf(it.colorKey) }.getOrNull() }
        val fromPriorityKey = settings.value?.priorities
            ?.firstOrNull { it.optionId == task.priorityOptionId }?.let { runCatching { PriorityKey.valueOf(it.colorKey) }.getOrNull() }
        if (toPriorityKey != null) {
            val decision = twoAxisPolicy.evaluateMove(task, toPriorityKey, fromPriorityKey, nowSnapshot().todayIso)
            when (decision) {
                is PolicyDecision.RedirectPrompt -> { _redirectPrompt.value = decision; return }
                is PolicyDecision.ProtectedFriction -> {
                    _protectedFriction.value = decision.copy(targetName = toName, targetId = toId)
                    return
                }
                is PolicyDecision.Proceed -> Unit
                is PolicyDecision.SafetyCatch -> Unit
            }
        }
        runAction { writeService.move(pageId, toName, toId) }
    }

    /** Swipe to snooze: bump one priority (undated) or push the date out a day (dated). */
    fun snoozeTask(task: SiftTask) {
        val s = settings.value ?: return
        val decision = twoAxisPolicy.evaluateSnooze(task, nowSnapshot().todayIso)
        if (decision is PolicyDecision.ProtectedFriction) {
            _protectedFriction.value = decision
            return
        }
        viewModelScope.launch {
            when (val result = writeService.snooze(task.pageId, snoozeDecisionFor(task, s))) {
                is WriteResult.Success -> { flash(task.pageId); _messages.tryEmit("Snoozed. Change in the task menu.") }
                WriteResult.AtLowestPriority -> _messages.tryEmit("This is already the lowest priority.")
                is WriteResult.Failure -> _messages.tryEmit(result.message)
                else -> Unit
            }
        }
    }

    /** Snooze to an explicit date (from the Change flow). */
    fun snoozeToDate(pageId: String, iso: String) = runAction { writeService.snooze(pageId, SnoozeDecision.SetDate(iso)) }

    /** Undo the single active action, flashing the affected row. */
    fun undo() {
        val token = undoManager.take() ?: return
        viewModelScope.launch {
            token.highlightPageId?.let { flash(it) }
            when (val result = token.inverse()) {
                is WriteResult.Failure -> _messages.tryEmit(result.message)
                else -> Unit
            }
        }
    }

    fun dismissUndo() = undoManager.clear()

    // Recurrence consent (bring-your-own databases). Returns the availability so the UI can prompt.
    suspend fun recurrenceAvailability(): RecurrenceSetupService.Availability? {
        val mapping = activeMappingNow() ?: return null
        return recurrenceSetup.availability(mapping)
    }

    fun consentPrompt(): String = recurrenceSetup.consentPrompt()

    /** User consented: add the Sift recurrence fields. Returns true on success. */
    suspend fun grantRecurrence(): Boolean {
        val mapping = activeMappingNow() ?: return false
        return when (val r = recurrenceSetup.addFields(mapping)) {
            is RecurrenceSetupService.AddResult.Success -> true
            is RecurrenceSetupService.AddResult.Failure -> { _messages.tryEmit(r.message); false }
        }
    }

    suspend fun declineRecurrence() {
        activeMappingNow()?.let { mapping -> recurrenceSetup.decline(mapping) }
    }

    private suspend fun activeMappingNow(): DatabaseMapping? = repository.mappingSet.value.active

    /** Run a write, then surface its undo token, flash, and any error message. */
    private fun runAction(block: suspend () -> WriteResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is WriteResult.Success -> {
                    undoManager.offer(result.undo)
                    result.undo?.highlightPageId?.let { flash(it) }
                }
                is WriteResult.Failure -> _messages.tryEmit(result.message)
                WriteResult.NeedsRecurrenceConsent -> _messages.tryEmit("Turn on recurring tasks first in the add screen.")
                WriteResult.AtLowestPriority -> _messages.tryEmit("This is already the lowest priority.")
            }
        }
    }

    /** Flash a row for about a second, then clear. */
    private fun flash(pageId: String) {
        viewModelScope.launch {
            undoManager.flash(pageId)
            delay(1_100)
            if (undoManager.flashPageId.value == pageId) undoManager.flash(null)
        }
    }

    // ---- Phase 3.5: Two Axis Model state ----

    private val _redirectPrompt = MutableStateFlow<PolicyDecision.RedirectPrompt?>(null)
    val redirectPrompt: StateFlow<PolicyDecision.RedirectPrompt?> = _redirectPrompt.asStateFlow()

    private val _protectedFriction = MutableStateFlow<PolicyDecision.ProtectedFriction?>(null)
    val protectedFriction: StateFlow<PolicyDecision.ProtectedFriction?> = _protectedFriction.asStateFlow()

    val brainDumpItems: StateFlow<List<ProjectedItem>> =
        combine(settings.filterNotNull(), repository.activeTasks, clock) { s, tasks, now ->
            projectBrainDump(tasks, s, now)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inflationAlert: StateFlow<InflationAlert?> =
        combine(settings.filterNotNull(), repository.activeTasks) { s, tasks ->
            val active = tasks.filter { !it.isDone && !it.isBrainDump }
            val asapCount = active.count { t ->
                s.priorities.firstOrNull { it.optionId == t.priorityOptionId }?.colorKey == "ASAP"
            }
            val protectedCount = active.count { it.isProtected }
            SignalInflation.checkInflation(asapCount, active.size, protectedCount)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface BoardNotification {
        data class TaskAdded(val priorityName: String, val colorKey: String) : BoardNotification
        data object RefreshSuccess : BoardNotification
        data class RefreshError(val reason: String) : BoardNotification
    }

    private val _notification = MutableStateFlow<BoardNotification?>(null)
    val notification: StateFlow<BoardNotification?> = _notification.asStateFlow()
    fun dismissNotification() { _notification.value = null }
    fun notifyAdded(priorityName: String, colorKey: String) {
        _notification.value = BoardNotification.TaskAdded(priorityName, colorKey)
    }

    data class DraftState(
        val title: String,
        val notes: String,
        val bucketId: String?,
        val isBrainDump: Boolean,
    )
    var pendingDraft: DraftState? = null
    fun clearDraft() { pendingDraft = null }

    fun dismissRedirectPrompt() { _redirectPrompt.value = null }
    fun dismissProtectedFriction() { _protectedFriction.value = null }

    /** Open the redirect prompt from the task detail sheet (when user taps "Change date"). */
    fun openRedirectPromptForTask(task: SiftTask) {
        _redirectPrompt.value = PolicyDecision.RedirectPrompt(task = task, reason = "")
    }

    fun confirmProtectedAction(pageId: String, toName: String, toId: String?) {
        _protectedFriction.value = null
        runAction { writeService.move(pageId, toName, toId) }
    }

    fun removeDateAndMoveManual(pageId: String) {
        runAction { writeService.edit(pageId, TaskEdits(dueIso = TaskEdits.Field(null))) }
    }

    fun pinTask(pageId: String) {
        viewModelScope.launch {
            val task = findTask(pageId) ?: return@launch
            val newLevel = when {
                task.pinLevel == 0 -> 1
                task.pinLevel == 1 && task.isRecurring -> 2
                else -> 0
            }
            upsertLocalField(pageId) { it.copy(pinLevel = newLevel) }
        }
    }

    fun toggleProtected(pageId: String) {
        viewModelScope.launch {
            val task = findTask(pageId) ?: return@launch
            upsertLocalField(pageId) { it.copy(isProtected = !task.isProtected) }
        }
    }

    fun toggleBlocked(pageId: String) {
        viewModelScope.launch {
            val task = findTask(pageId) ?: return@launch
            upsertLocalField(pageId) { it.copy(isBlocked = !task.isBlocked) }
        }
    }

    fun setBrainDump(pageId: String, reviewDateIso: String? = null) {
        viewModelScope.launch {
            upsertLocalField(pageId) { it.copy(isBrainDump = true, reviewDateIso = reviewDateIso) }
        }
    }

    private suspend fun upsertLocalField(pageId: String, transform: (TaskLocalState) -> TaskLocalState) {
        val mappingId = repository.mappingSet.value.active?.id ?: return
        val current = localStateStore.get(pageId) ?: TaskLocalState(pageId = pageId, mappingId = mappingId)
        localStateStore.upsert(transform(current))
    }

    private fun findTask(pageId: String): SiftTask? =
        (projection.value?.priorities?.flatMap { it.items }?.map { it.task }
            ?: emptyList()).firstOrNull { it.pageId == pageId }
            ?: projection.value?.pinnedItems?.map { it.task }?.firstOrNull { it.pageId == pageId }

    private fun snoozeDecisionFor(task: SiftTask, s: BoardSettings): SnoozeDecision {
        val due = task.due
        if (due != null) {
            val date = runCatching { LocalDate.parse(due.take(10)).plusDays(1).toString() }.getOrNull()
                ?: return SnoozeDecision.AlreadyLowest
            val timeSuffix = if (due.length > 10) due.substring(10) else ""
            return SnoozeDecision.SetDate(date + timeSuffix)
        }
        val ordered = s.priorities.filter { !it.hidden }.sortedBy { it.order }
        val idx = ordered.indexOfFirst { it.optionId == task.priorityOptionId }
        val below = if (idx >= 0) ordered.getOrNull(idx + 1) else null
        return if (below != null) SnoozeDecision.BumpToPriority(below.optionName ?: below.displayName, below.optionId)
        else SnoozeDecision.AlreadyLowest
    }
}
