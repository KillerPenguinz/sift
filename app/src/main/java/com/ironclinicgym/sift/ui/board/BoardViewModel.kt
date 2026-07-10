package com.ironclinicgym.sift.ui.board

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironclinicgym.sift.core.board.BoardProjection
import com.ironclinicgym.sift.core.board.BoardSettings
import com.ironclinicgym.sift.core.board.ClockSnapshot
import com.ironclinicgym.sift.core.board.ProjectedItem
import com.ironclinicgym.sift.core.board.ProjectedPriority
import com.ironclinicgym.sift.core.board.SafetyCatchEvaluation
import com.ironclinicgym.sift.core.board.SampleBoard
import com.ironclinicgym.sift.core.board.SignalInflation
import com.ironclinicgym.sift.core.board.InflationAlert
import com.ironclinicgym.sift.core.board.evaluateSafetyCatch
import com.ironclinicgym.sift.core.board.formatHourMinute
import com.ironclinicgym.sift.core.board.projectBoard
import com.ironclinicgym.sift.core.board.projectBrainDump
import com.ironclinicgym.sift.core.domain.ActionHistoryEntry
import com.ironclinicgym.sift.core.domain.NotificationTier
import com.ironclinicgym.sift.core.domain.PolicyDecision
import com.ironclinicgym.sift.core.domain.RecurrenceSetupService
import com.ironclinicgym.sift.core.domain.SiftNotification
import com.ironclinicgym.sift.core.domain.SiftTask
import com.ironclinicgym.sift.core.domain.TwoAxisPolicy
import com.ironclinicgym.sift.data.local.AppPreferencesDataStore
import com.ironclinicgym.sift.core.domain.SnoozeDecision
import com.ironclinicgym.sift.core.domain.TaskDraft
import com.ironclinicgym.sift.core.domain.TaskEdits
import com.ironclinicgym.sift.core.domain.UndoManager
import com.ironclinicgym.sift.core.domain.UndoToken
import com.ironclinicgym.sift.core.domain.WriteResult
import com.ironclinicgym.sift.core.domain.ports.ActionHistoryStore
import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore
import com.ironclinicgym.sift.core.domain.ports.NotificationStore
import com.ironclinicgym.sift.core.domain.ports.TaskLocalState
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.mapping.DatabaseMapping
import com.ironclinicgym.sift.core.theme.PriorityKey
import com.ironclinicgym.sift.data.repository.SiftRepository
import com.ironclinicgym.sift.data.repository.SiftRepository.RefreshResult
import com.ironclinicgym.sift.di.AppContainer
import com.ironclinicgym.sift.ui.navigation.Routes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import com.ironclinicgym.sift.core.theme.PRIORITY_META
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ironclinicgym.sift.core.board.InflationKind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

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
    private val notificationStore: NotificationStore,
    private val actionHistoryStore: ActionHistoryStore,
) : ViewModel() {

    constructor(container: AppContainer) : this(
        container.repository,
        container.boardSettingsStore,
        container.undoManager,
        container.recurrenceSetup,
        container.appPreferences,
        TwoAxisPolicy(),
        container.localStateStore,
        container.notificationStore,
        container.actionHistoryStore,
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

    /** Count of unread actionable notifications, shown as a badge on the menu hub icon. */
    val unreadNotificationCount: StateFlow<Int> =
        notificationStore.observeUnreadCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markNotificationsRead() {
        viewModelScope.launch { notificationStore.markAllRead() }
    }

    /** Chronological (newest first) actionable notifications for the notification center screen. */
    val notifications: StateFlow<List<SiftNotification>> =
        notificationStore.observeActionable()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chronological (newest first) user action log for the action history screen. */
    val actionHistory: StateFlow<List<ActionHistoryEntry>> =
        actionHistoryStore.observeRecent(50)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _safetyCatchQueue = MutableStateFlow<List<Pair<SiftTask, String>>>(emptyList())
    val currentSafetyCatch: StateFlow<Pair<SiftTask, String>?> = _safetyCatchQueue
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
            val result = repository.refresh()
            _refresh.value = when (result) {
                is RefreshResult.Success -> RefreshUi.Idle
                RefreshResult.NoMapping -> RefreshUi.Idle
                RefreshResult.NeedsReconnect -> RefreshUi.Reconnect
                is RefreshResult.Failed -> {
                    // The retry lambda captures this var, assigned once the entry is queued.
                    var errorEntryId = 0L
                    errorEntryId = postNotification(NotificationVariant.RefreshError(
                        reason = result.message,
                        onRetry = { dismissNotification(errorEntryId); refresh() },
                    ))
                    RefreshUi.Error(result.message)
                }
            }
            if (result is RefreshResult.Success) {
                // Notion is authoritative after a successful sync: local undo tokens no longer
                // match remote state, so retire every history entry's undo availability.
                actionHistoryStore.markAllSynced()
                runSafetyCatchEvaluation(repository.activeTasks.first())
            }
            Log.d("BatteryDebug", "BoardViewModel.refresh() done: ${_refresh.value}")
        }
    }

    /**
     * Drops the head of the safety catch queue. When the user dismissed the dialog without
     * acting ([actedOn] false), the task is still unresolved, so it is also logged as an
     * actionable notification: a dismissed prompt does not just vanish, it turns into something
     * revisitable from the notification center. When the user chose to act ("Find it a better
     * spot", which opens the redirect prompt), no notification is logged.
     */
    fun dismissSafetyCatch(actedOn: Boolean = false) {
        val dismissed = _safetyCatchQueue.value.firstOrNull()
        _safetyCatchQueue.update { it.drop(1) }
        if (actedOn) return
        dismissed?.let { (task, _) ->
            viewModelScope.launch {
                notificationStore.insert(
                    SiftNotification(
                        id = UUID.randomUUID().toString(),
                        message = "${task.title} needs a better spot.",
                        icon = "notifications",
                        tier = NotificationTier.ACTIONABLE,
                        actionLabel = "Open",
                        actionRoute = Routes.BOARD,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private suspend fun runSafetyCatchEvaluation(tasks: List<SiftTask>) {
        val todayIso = LocalDate.now().toString()
        val mappingId = repository.mappingSet.value.active?.id ?: return
        val states = localStateStore.observe(mappingId).first().associateBy { it.pageId }
        val evaluation = evaluateSafetyCatch(
            datedTasks = tasks.filter { it.due != null && !it.isDone && !it.isBrainDump },
            localStates = states,
            todayIso = todayIso,
        )
        val shownAt = System.currentTimeMillis()
        evaluation.toFire.forEach { (task, band) ->
            val existing = localStateStore.get(task.pageId)
                ?: TaskLocalState(pageId = task.pageId, mappingId = mappingId)
            localStateStore.upsert(
                existing.copy(safetyCatchFiredBand = band, lastSafetyCatchShownAt = shownAt)
            )
        }
        evaluation.toClear.forEach { pageId ->
            val existing = localStateStore.get(pageId) ?: return@forEach
            localStateStore.upsert(
                existing.copy(safetyCatchFiredBand = null, lastSafetyCatchShownAt = null)
            )
        }
        if (evaluation.toFire.isNotEmpty()) {
            _safetyCatchQueue.update { existing ->
                (existing + evaluation.toFire).distinctBy { it.first.pageId }
            }
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

    /**
     * A queued notification with a monotonic [id] so two structurally equal variants queued back
     * to back still count as distinct heads: the StateFlow re-emits, and the UI keys its slide
     * animation and auto-dismiss timer on [id] so both restart per entry.
     */
    data class QueuedNotification(val id: Long, val variant: NotificationVariant)

    companion object {
        /** Stable [QueuedNotification.id] for the undo bar, which lives outside the queue. */
        const val UNDO_BAR_ID = -1L
    }

    private val notificationIds = AtomicLong(0)
    private val _notificationQueue = MutableStateFlow<List<QueuedNotification>>(emptyList())

    /** The single notification to display right now, or null. One at a time; see [postNotification]. */
    val currentNotification: StateFlow<QueuedNotification?> =
        _notificationQueue.map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Drop the head of the queue, but only if it is still the entry the caller saw ([id]): a
     * double-fired dismiss (X tapped as the auto-dismiss timer lands) is a no-op instead of
     * silently swallowing the next queued notification.
     */
    fun dismissNotification(id: Long) {
        _notificationQueue.update { queue ->
            if (queue.firstOrNull()?.id == id) queue.drop(1) else queue
        }
    }

    /**
     * Enqueue a notification and return its queue entry id. Transient variants (added, removed,
     * undo, info) are display-only and never persisted. Actionable variants (inflation nudges)
     * also get a durable [SiftNotification] row so they show up, unread, in the notification
     * center.
     */
    internal fun postNotification(variant: NotificationVariant): Long {
        val id = notificationIds.incrementAndGet()
        _notificationQueue.update { it + QueuedNotification(id, variant) }
        if (variant is NotificationVariant.InflationNudge) {
            viewModelScope.launch {
                notificationStore.insert(
                    SiftNotification(
                        id = UUID.randomUUID().toString(),
                        message = variant.message,
                        icon = "notifications",
                        tier = NotificationTier.ACTIONABLE,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            }
        }
        return id
    }

    /** Log a user action (add, complete, remove, move, pin, snooze) to the action history. */
    private fun logAction(description: String, taskTitle: String, taskPageId: String?, canUndo: Boolean = false) {
        viewModelScope.launch {
            actionHistoryStore.insert(
                ActionHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    description = description,
                    taskTitle = taskTitle,
                    taskPageId = taskPageId,
                    timestamp = System.currentTimeMillis(),
                    canUndo = canUndo,
                )
            )
        }
    }

    /** Add: suspends and returns the result so the sheet can stay open and preserve input on failure. */
    suspend fun addTask(draft: TaskDraft): WriteResult {
        val result = writeService.add(draft)
        if (result is WriteResult.Success) {
            logAction("Added task", draft.title, result.pageId, canUndo = result.undo != null)
        }
        return result
    }

    /** Edit an existing task's mapped fields. */
    suspend fun editTask(pageId: String, edits: TaskEdits): WriteResult = writeService.edit(pageId, edits)

    fun completeTask(pageId: String) {
        val title = findTask(pageId)?.title.orEmpty()
        runAction(logDescription = "Completed task", logTaskTitle = title, logTaskPageId = pageId) {
            writeService.complete(pageId)
        }
    }

    fun removeTask(pageId: String) {
        val title = findTask(pageId)?.title.orEmpty()
        runAction(logDescription = "Removed task", logTaskTitle = title, logTaskPageId = pageId) {
            writeService.remove(pageId)
        }
    }

    fun moveTask(pageId: String, toName: String, toId: String?) {
        val task = findTask(pageId)
        if (task == null) {
            runAction(logDescription = "Moved to $toName", logTaskTitle = "", logTaskPageId = pageId) {
                writeService.move(pageId, toName, toId)
            }
            return
        }
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
        runAction(logDescription = "Moved to $toName", logTaskTitle = task.title, logTaskPageId = pageId) {
            writeService.move(pageId, toName, toId)
        }
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
                is WriteResult.Success -> {
                    flash(task.pageId)
                    postNotification(NotificationVariant.Info("Snoozed. Change in the task menu."))
                    logAction("Snoozed task", task.title, task.pageId, canUndo = result.undo != null)
                }
                WriteResult.AtLowestPriority -> postNotification(NotificationVariant.Info("This is already the lowest priority."))
                is WriteResult.Failure -> postNotification(NotificationVariant.Info(result.message))
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
            // The taken token corresponds, by construction, to the newest still-undoable history
            // entry. Capture it before the inverse runs so a concurrent logAction cannot shift
            // which entry is "most recent".
            val historyEntry = actionHistoryStore.mostRecentUndoable()
            when (val result = token.inverse()) {
                is WriteResult.Failure -> postNotification(NotificationVariant.Info(result.message))
                else -> {
                    // Retire the history entry so the action history screen's Undo button does
                    // not linger after the token has been consumed.
                    historyEntry?.let { actionHistoryStore.markSynced(listOf(it.id)) }
                }
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
            is RecurrenceSetupService.AddResult.Failure -> { postNotification(NotificationVariant.Info(r.message)); false }
        }
    }

    suspend fun declineRecurrence() {
        activeMappingNow()?.let { mapping -> recurrenceSetup.decline(mapping) }
    }

    private suspend fun activeMappingNow(): DatabaseMapping? = repository.mappingSet.value.active

    /**
     * Run a write, then surface its undo token, flash, and any error message. If [logDescription]
     * is set, a successful write is also logged to the action history (canUndo reflects whether
     * the write actually returned an undo token).
     */
    private fun runAction(
        logDescription: String? = null,
        logTaskTitle: String = "",
        logTaskPageId: String? = null,
        block: suspend () -> WriteResult,
    ) {
        viewModelScope.launch {
            when (val result = block()) {
                is WriteResult.Success -> {
                    undoManager.offer(result.undo)
                    result.undo?.highlightPageId?.let { flash(it) }
                    if (logDescription != null) {
                        logAction(logDescription, logTaskTitle, logTaskPageId ?: result.pageId, canUndo = result.undo != null)
                    }
                }
                is WriteResult.Failure -> postNotification(NotificationVariant.Info(result.message))
                WriteResult.NeedsRecurrenceConsent -> postNotification(NotificationVariant.Info("Turn on recurring tasks first in the add screen."))
                WriteResult.AtLowestPriority -> postNotification(NotificationVariant.Info("This is already the lowest priority."))
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
            if (!s.signalInflationEnabled) return@combine null
            val active = tasks.filter { !it.isDone && !it.isBrainDump }
            val asapCount = active.count { t ->
                s.priorities.firstOrNull { it.optionId == t.priorityOptionId }?.colorKey == "ASAP"
            }
            val protectedCount = active.count { it.isProtected }
            SignalInflation.checkInflation(
                asapCount, active.size, protectedCount,
                asapThreshold = s.asapInflationThreshold,
                protectedPercent = s.protectedInflationPercent,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _inflationDismissed = MutableStateFlow(emptyMap<InflationKind, Boolean>())

    val activeInflationAlert: StateFlow<InflationAlert?> = combine(
        inflationAlert,
        _inflationDismissed,
    ) { alert, dismissed ->
        if (alert != null && dismissed[alert.kind] == true) null else alert
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissInflationAlert() {
        val kind = activeInflationAlert.value?.kind ?: return
        _inflationDismissed.update { it + (kind to true) }
    }

    init {
        // One-time cleanup: notification center and action history entries expire after 30 days.
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            notificationStore.deleteExpired(cutoff)
            actionHistoryStore.deleteOlderThan(cutoff)
        }
        // Safety catch on app launch, from whatever is already cached: don't wait for a manual
        // refresh to notice a task that was already overdue when the app was opened. activeTasks
        // is fed by mappingSet, which is seeded with MappingSet.EMPTY before the real mapping
        // loads from disk; a plain `.first()` here would grab that transient empty emission and
        // never evaluate anything, so wait for the first non-empty task list instead.
        viewModelScope.launch {
            val tasks = repository.activeTasks.first { it.isNotEmpty() }
            runSafetyCatchEvaluation(tasks)
        }
        viewModelScope.launch {
            var lastKind: InflationKind? = null
            inflationAlert.collect { alert ->
                if (alert == null && lastKind != null) {
                    if (_inflationDismissed.value.containsValue(true)) {
                        postNotification(NotificationVariant.Info(encouragingMessage()))
                    }
                    _inflationDismissed.value = emptyMap()
                }
                lastKind = alert?.kind
            }
        }
    }

    private val ENCOURAGING_MESSAGES = listOf(
        "Nice work clearing those out",
        "Good job reprioritizing",
        "ASAP is looking cleaner now",
        "That is more like it",
        "Well done",
        "Much more manageable now",
        "Good call on those",
        "Nicely done",
    )
    private var lastEncouragingMsgIdx = -1

    fun encouragingMessage(): String {
        var idx: Int
        do {
            idx = ENCOURAGING_MESSAGES.indices.random()
        } while (idx == lastEncouragingMsgIdx && ENCOURAGING_MESSAGES.size > 1)
        lastEncouragingMsgIdx = idx
        return ENCOURAGING_MESSAGES[idx]
    }

    fun updateBoardSettings(s: BoardSettings) {
        viewModelScope.launch { settingsStore.save(s) }
    }

    fun onSignalInflationMasterToggle(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                val alreadyShown = appPreferences.inflationMasterOffShown().first()
                if (!alreadyShown) {
                    postNotification(
                        NotificationVariant.Info("This helps keep things manageable, but it is your call.")
                    )
                    appPreferences.setInflationMasterOffShown(true)
                }
            }
            val current = settings.value ?: return@launch
            settingsStore.save(current.copy(signalInflationEnabled = enabled))
        }
    }

    // ---- Task 9: Protected review screen ----

    data class ProtectedTaskEntry(
        val item: ProjectedItem,
        val priorityColorKey: String,
        val priorityLabel: String,
    )

    private val _showProtectedReview = MutableStateFlow(false)
    val showProtectedReview: StateFlow<Boolean> = _showProtectedReview.asStateFlow()

    private val activeTaskList: StateFlow<List<SiftTask>> =
        repository.activeTasks
            .map { tasks -> tasks.filter { !it.isDone && !it.isBrainDump } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val protectedTasks: StateFlow<List<ProtectedTaskEntry>> =
        combine(projection, settings) { proj, s ->
            if (proj == null || s == null) return@combine emptyList()
            val fromPriorities = proj.priorities.flatMap { priority ->
                priority.items
                    .filter { it.task.isProtected }
                    .map { item ->
                        ProtectedTaskEntry(
                            item = item,
                            priorityColorKey = priority.view.colorKey,
                            priorityLabel = priority.view.displayName,
                        )
                    }
            }
            val fromPinned = proj.pinnedItems
                .filter { it.task.isProtected }
                .map { item ->
                    val pv = s.priorities.firstOrNull { p -> p.optionId == item.task.priorityOptionId }
                    ProtectedTaskEntry(
                        item = item,
                        priorityColorKey = pv?.colorKey ?: "ASAP",
                        priorityLabel = pv?.displayName ?: "ASAP",
                    )
                }
            (fromPriorities + fromPinned).distinctBy { it.item.task.pageId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openProtectedReview() {
        _showProtectedReview.value = true
    }

    fun closeProtectedReview() {
        val s = settings.value
        // Use same source as inflationAlert for a consistent percentage
        val activeTasks = activeTaskList.value
        val total = activeTasks.size
        val protectedCount = activeTasks.count { it.isProtected }
        val percent = if (total > 0) (protectedCount * 100) / total else 0
        if (s != null && percent < s.protectedInflationPercent) {
            postNotification(NotificationVariant.Info(encouragingMessage()))
        }
        _showProtectedReview.value = false
    }

    fun unprotectTask(pageId: String) {
        viewModelScope.launch {
            upsertLocalField(pageId) { it.copy(isProtected = false) }
        }
    }

    fun notifyAdded(priorityName: String, colorKey: String) {
        postNotification(NotificationVariant.ConfirmPlacement(
            priorityName = priorityName,
            priorityIcon = PRIORITY_META.firstOrNull { it.key.name == colorKey }?.icon ?: "label",
            colorKey = colorKey,
        ))
    }

    fun notifyAddedBrainDump() {
        postNotification(NotificationVariant.ConfirmPlacement(
            priorityName = "Brain dump",
            priorityIcon = "lightbulb",
            colorKey = "ONEDAY",
        ))
    }

    fun notifySetupComplete() {
        postNotification(NotificationVariant.Info("Setup complete. Syncing with Notion."))
    }

    data class DraftState(
        val title: String,
        val notes: String,
        val bucketId: String?,
        val isBrainDump: Boolean,
        val dateIso: String? = null,
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
        val title = findTask(pageId)?.title.orEmpty()
        runAction(logDescription = "Moved to $toName", logTaskTitle = title, logTaskPageId = pageId) {
            writeService.move(pageId, toName, toId)
        }
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
            val description = if (newLevel > 0) "Pinned task" else "Unpinned task"
            logAction(description, task.title, pageId, canUndo = false)
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
