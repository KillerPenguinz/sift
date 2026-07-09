package com.ironclinicgym.sift.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironclinicgym.sift.auth.OAuthLauncher
import com.ironclinicgym.sift.core.domain.OnboardingService
import com.ironclinicgym.sift.core.mapping.AutoMatcher
import com.ironclinicgym.sift.core.mapping.PriorityBinding
import com.ironclinicgym.sift.core.mapping.MappingValidator
import com.ironclinicgym.sift.core.mapping.OptionInput
import com.ironclinicgym.sift.core.mapping.Role
import com.ironclinicgym.sift.core.mapping.RolePropertyBinding
import com.ironclinicgym.sift.core.mapping.BucketBinding
import com.ironclinicgym.sift.core.notion.model.NotionDataSourceSchema
import com.ironclinicgym.sift.core.notion.model.NotionPageSummary
import com.ironclinicgym.sift.di.AppContainer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives onboarding. Thin: all logic lives in core [OnboardingService]; this only holds UI
 * state and marshals user actions. Never references a Notion property name directly.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val service: OnboardingService = container.onboardingService

    val authState = container.authRepository.state

    // ---- Connect ----
    val isOAuthConfigured: Boolean get() = container.authRepository.isOAuthConfigured

    fun beginConnect(context: Context): Boolean {
        val url = container.authRepository.beginAuthorization() ?: return false
        OAuthLauncher.launch(context, url)
        return true
    }

    fun resetAuth() = container.authRepository.reset()

    // ---- Automatic setup ----
    sealed interface AutoSetupUi {
        data object Idle : AutoSetupUi
        data object Preparing : AutoSetupUi
        data class ChoosePage(val pages: List<NotionPageSummary>) : AutoSetupUi
        data object Working : AutoSetupUi
        data class Done(val seedFailures: Int) : AutoSetupUi
        data class Error(val message: String) : AutoSetupUi
    }

    private val _autoSetup = MutableStateFlow<AutoSetupUi>(AutoSetupUi.Idle)
    val autoSetup: StateFlow<AutoSetupUi> = _autoSetup.asStateFlow()

    fun prepareAutoSetup() {
        val state = _autoSetup.value
        if (state is AutoSetupUi.Preparing || state is AutoSetupUi.Working || state is AutoSetupUi.ChoosePage) return
        _autoSetup.value = AutoSetupUi.Preparing
        viewModelScope.launch {
            val pages = runCatching { service.listGrantedPages() }.getOrElse {
                _autoSetup.value = AutoSetupUi.Error(it.message.orEmpty()); return@launch
            }
            when {
                pages.isEmpty() -> _autoSetup.value = AutoSetupUi.Error(
                    "Sift could not find a page you granted access to. Grant a page in Notion and try again.",
                )
                pages.size == 1 -> provision(pages.first().pageId)
                else -> _autoSetup.value = AutoSetupUi.ChoosePage(pages)
            }
        }
    }

    fun choosePage(pageId: String) = provision(pageId)

    private fun provision(pageId: String) {
        _autoSetup.value = AutoSetupUi.Working
        viewModelScope.launch {
            val workspaceId = container.repository.activeWorkspaceId().orEmpty()
            _autoSetup.value = when (val result = service.provisionAutoSetup(workspaceId, pageId)) {
                is OnboardingService.ProvisionResult.Success -> AutoSetupUi.Done(result.seedFailures)
                is OnboardingService.ProvisionResult.Failure -> AutoSetupUi.Error(result.message)
            }
        }
    }

    // ---- Bring your own database ----
    sealed interface ByoList {
        data object Loading : ByoList
        data class Loaded(val databases: List<NotionDataSourceSchema>) : ByoList
        data class Error(val message: String) : ByoList
    }

    data class ByoEdit(
        val schema: NotionDataSourceSchema,
        val selected: Map<Role, String?>,
        val priorities: List<PriorityBinding>,
        val buckets: List<BucketBinding>,
        val unmatchedPriorityIds: List<String>,
        val label: String,
        val validation: MappingValidator.ValidationResult,
        val completing: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
        val missingProperties: List<OnboardingService.MissingProperty> = emptyList(),
        val selectedMissing: Set<Role> = emptySet(),
        val creating: Boolean = false,
    ) {
        val canProceed: Boolean get() {
            if (validation.isSatisfied) return true
            val unsatisfied = validation.issues.filter { it.role.required }.map { it.role }.toSet()
            return unsatisfied.all { it in selectedMissing }
        }
    }

    private val _byoList = MutableStateFlow<ByoList>(ByoList.Loading)
    val byoList: StateFlow<ByoList> = _byoList.asStateFlow()

    private val _byoEdit = MutableStateFlow<ByoEdit?>(null)
    val byoEdit: StateFlow<ByoEdit?> = _byoEdit.asStateFlow()

    fun loadDatabases() {
        _byoList.value = ByoList.Loading
        Log.d("SiftDebug", "VM: loadDatabases() called")
        viewModelScope.launch {
            _byoList.value = try {
                val databases = service.listDatabases()
                Log.d("SiftDebug", "VM: loadDatabases got ${databases.size} results")
                databases.forEachIndexed { i, ds ->
                    Log.d("SiftDebug", "VM: db[$i] title=${ds.title}, dbId=${ds.databaseId}, dsId=${ds.dataSourceId}, props=${ds.properties.size}")
                }
                ByoList.Loaded(databases)
            } catch (e: Exception) {
                Log.d("SiftDebug", "VM: loadDatabases EXCEPTION: ${e::class.simpleName} ${e.message}")
                Log.d("SiftDebug", "VM: loadDatabases stacktrace: ${e.stackTraceToString().take(500)}")
                ByoList.Error(e.message.orEmpty())
            }
        }
    }

    private val _urlError = MutableStateFlow<String?>(null)
    val urlError: StateFlow<String?> = _urlError.asStateFlow()

    fun clearUrlError() { _urlError.value = null }

    fun resolveByUrl(id: String) {
        Log.d("SiftDebug", "VM: resolveByUrl called with id=$id")
        viewModelScope.launch {
            val result = try {
                service.loadSchemaFromPastedId(id)
            } catch (e: Exception) {
                Log.d("SiftDebug", "VM: resolveByUrl UNCAUGHT EXCEPTION: ${e::class.simpleName} ${e.message}")
                Log.d("SiftDebug", "VM: resolveByUrl stacktrace: ${e.stackTraceToString().take(500)}")
                OnboardingService.SchemaResult.NoAccess("Unexpected error: ${e.message}")
            }
            when (result) {
                is OnboardingService.SchemaResult.Success -> {
                    Log.d("SiftDebug", "VM: resolveByUrl SUCCESS, title=${result.schema.title}, props=${result.schema.properties.size}")
                    _urlError.value = null
                    fillFromSchema(result.schema)
                }
                is OnboardingService.SchemaResult.NoAccess -> {
                    Log.d("SiftDebug", "VM: resolveByUrl NO ACCESS: ${result.message}")
                    _urlError.value = result.message
                }
            }
        }
    }

    fun selectSchema(schema: NotionDataSourceSchema) = fillFromSchema(schema)

    private fun fillFromSchema(schema: NotionDataSourceSchema) {
        val suggestion = service.suggestMapping(schema)
        val selected = Role.entries.associateWith { role ->
            suggestion.bindings.firstOrNull { it.role == role }?.propertyId
        }
        val missing = suggestion.missingProperties
        _byoEdit.value = ByoEdit(
            schema = schema,
            selected = selected,
            priorities = suggestion.priorities,
            buckets = suggestion.buckets,
            unmatchedPriorityIds = suggestion.unmatchedPriorityOptionIds,
            label = schema.title,
            validation = MappingValidator.validate(bindingsOf(schema, selected)),
            missingProperties = missing,
            selectedMissing = missing.map { it.role }.toSet(),
        )
    }

    fun setRoleProperty(role: Role, propertyId: String?) {
        val current = _byoEdit.value ?: return
        val selected = current.selected.toMutableMap().apply { this[role] = propertyId }
        val priorities = if (role == Role.PRIORITY) matchPriorities(current.schema, propertyId) else current.priorities
        val buckets = if (role == Role.BUCKET) matchBuckets(current.schema, propertyId) else current.buckets
        val unmatched = if (role == Role.PRIORITY) {
            AutoMatcher.matchPriorities(optionsFor(current.schema, propertyId)).unmatchedOptionIds
        } else current.unmatchedPriorityIds
        _byoEdit.value = current.copy(
            selected = selected,
            priorities = priorities,
            buckets = buckets,
            unmatchedPriorityIds = unmatched,
            validation = MappingValidator.validate(bindingsOf(current.schema, selected)),
        )
    }

    fun setLabel(label: String) {
        _byoEdit.value = _byoEdit.value?.copy(label = label)
    }

    fun toggleMissingProperty(role: Role) {
        val current = _byoEdit.value ?: return
        if (current.missingProperties.firstOrNull { it.role == role }?.required == true) return
        val updated = if (role in current.selectedMissing) current.selectedMissing - role else current.selectedMissing + role
        _byoEdit.value = current.copy(selectedMissing = updated)
    }

    fun completeByo() {
        val current = _byoEdit.value ?: return
        if (current.completing || current.creating) return

        val toCreate = current.missingProperties.filter { it.role in current.selectedMissing }
        if (toCreate.isNotEmpty()) {
            _byoEdit.value = current.copy(creating = true, error = null)
            viewModelScope.launch {
                when (val result = service.createMissingProperties(current.schema.dataSourceId, toCreate)) {
                    is OnboardingService.CreateResult.Success -> {
                        fillFromSchema(result.schema)
                        val refreshed = _byoEdit.value ?: return@launch
                        if (refreshed.validation.isSatisfied) {
                            _byoEdit.value = refreshed.copy(completing = true)
                            doComplete()
                        }
                    }
                    is OnboardingService.CreateResult.Failure -> {
                        _byoEdit.value = current.copy(creating = false, error = result.message)
                    }
                }
            }
            return
        }

        if (!current.validation.isSatisfied) return
        _byoEdit.value = current.copy(completing = true, error = null)
        viewModelScope.launch { doComplete() }
    }

    private suspend fun doComplete() {
        val current = _byoEdit.value ?: return
        val workspaceId = container.repository.activeWorkspaceId().orEmpty()
        val result = service.completeByo(
            workspaceId = workspaceId,
            schema = current.schema,
            bindings = bindingsOf(current.schema, current.selected),
            priorities = current.priorities,
            buckets = current.buckets,
            label = current.label,
        )
        _byoEdit.value = when (result) {
            is OnboardingService.CompleteResult.Success -> current.copy(completing = false, done = true)
            is OnboardingService.CompleteResult.Invalid ->
                current.copy(completing = false, validation = MappingValidator.ValidationResult(false, result.issues))
            is OnboardingService.CompleteResult.Failure -> current.copy(completing = false, error = result.message)
        }
    }

    // ---- helpers ----
    private fun bindingsOf(schema: NotionDataSourceSchema, selected: Map<Role, String?>): List<RolePropertyBinding> =
        selected.mapNotNull { (role, propertyId) ->
            val prop = schema.properties.firstOrNull { it.id == propertyId } ?: return@mapNotNull null
            RolePropertyBinding(role, prop.id, prop.name, prop.type)
        }

    private fun optionsFor(schema: NotionDataSourceSchema, propertyId: String?): List<OptionInput> {
        val prop = schema.properties.firstOrNull { it.id == propertyId } ?: return emptyList()
        return prop.options.map { OptionInput(it.id ?: it.name, it.name, it.color) }
    }

    private fun matchPriorities(schema: NotionDataSourceSchema, propertyId: String?) =
        AutoMatcher.matchPriorities(optionsFor(schema, propertyId)).bindings

    private fun matchBuckets(schema: NotionDataSourceSchema, propertyId: String?) =
        AutoMatcher.matchBuckets(optionsFor(schema, propertyId)).bindings
}
