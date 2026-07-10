package com.ironclinicgym.sift.di

import android.content.Context
import androidx.room.Room
import com.ironclinicgym.sift.BuildConfig
import com.ironclinicgym.sift.auth.AuthRepository
import com.ironclinicgym.sift.core.domain.OnboardingService
import com.ironclinicgym.sift.core.domain.RecurrenceSetupService
import com.ironclinicgym.sift.core.domain.TaskWriteService
import com.ironclinicgym.sift.core.domain.UndoManager
import com.ironclinicgym.sift.core.domain.ports.ActionHistoryStore
import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.domain.ports.LabelStore
import com.ironclinicgym.sift.core.domain.ports.NotificationStore
import com.ironclinicgym.sift.core.domain.ports.RecurrenceConsentStore
import com.ironclinicgym.sift.core.domain.ports.TaskCache
import com.ironclinicgym.sift.core.domain.ports.TaskLocalStateStore
import com.ironclinicgym.sift.core.domain.ports.TokenStore
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceEngine
import com.ironclinicgym.sift.core.domain.recurrence.RecurrenceEngineDmfs
import com.ironclinicgym.sift.core.notion.HttpTransport
import com.ironclinicgym.sift.core.notion.NotionClient
import com.ironclinicgym.sift.core.notion.RateLimiter
import com.ironclinicgym.sift.core.oauth.OAuthConfig
import com.ironclinicgym.sift.core.mapping.MappingSet
import com.ironclinicgym.sift.core.oauth.OAuthRequestFactory
import com.ironclinicgym.sift.core.oauth.TokenBundle
import com.ironclinicgym.sift.core.domain.ports.BoardSettingsStore
import com.ironclinicgym.sift.data.local.AppPreferencesDataStore
import com.ironclinicgym.sift.data.local.BoardSettingsDataStore
import com.ironclinicgym.sift.data.local.MIGRATION_1_2
import com.ironclinicgym.sift.data.local.MIGRATION_2_3
import com.ironclinicgym.sift.data.local.MIGRATION_3_4
import com.ironclinicgym.sift.data.local.MIGRATION_4_5
import com.ironclinicgym.sift.data.local.MIGRATION_5_6
import com.ironclinicgym.sift.data.local.MIGRATION_6_7
import com.ironclinicgym.sift.data.local.MIGRATION_7_8
import com.ironclinicgym.sift.data.local.RecurrenceConsentDataStore
import com.ironclinicgym.sift.data.local.RoomActionHistoryStore
import com.ironclinicgym.sift.data.local.RoomNotificationStore
import com.ironclinicgym.sift.data.local.RoomLabelStore
import com.ironclinicgym.sift.data.local.RoomTaskCache
import com.ironclinicgym.sift.data.local.RoomTaskLocalStateStore
import com.ironclinicgym.sift.data.local.SiftDatabase
import com.ironclinicgym.sift.data.notion.OkHttpTransport
import com.ironclinicgym.sift.data.repository.SiftRepository
import com.ironclinicgym.sift.data.secure.KeystoreCrypto
import com.ironclinicgym.sift.data.secure.SecureMappingStore
import com.ironclinicgym.sift.data.secure.SecureStateGenerator
import com.ironclinicgym.sift.data.secure.SecureTokenStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * Manual dependency container (Hilt's Gradle plugin is not yet AGP 9.2 compatible).
 *
 * Holds process singletons and wires the core's constructor-injected logic to its platform
 * adapters. The [RateLimiter] is shared by the UI and the WorkManager refresh so their
 * combined Notion traffic stays under the rate limit.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Long-lived scope for fire-and-forget work (for example the OAuth redirect exchange). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Secure storage (Keystore-encrypted).
    private val crypto = KeystoreCrypto()
    private val tokenStore: TokenStore = SecureTokenStore(appContext, crypto)
    private val mappingStore: MappingStore = SecureMappingStore(appContext, crypto)

    // Notion client over a shared rate limiter.
    private val rateLimiter = RateLimiter()
    private val transport: HttpTransport = OkHttpTransport()
    private val notionClient = NotionClient(
        transport = transport,
        authTokenProvider = { tokenStore.load()?.accessToken },
        rateLimiter = rateLimiter,
    )

    // Local cache (non-sensitive board records only) + Sift-only operational state.
    private val database = Room.databaseBuilder(appContext, SiftDatabase::class.java, "sift.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()
    private val taskCache: TaskCache = RoomTaskCache(database.taskCacheDao())
    val localStateStore: TaskLocalStateStore = RoomTaskLocalStateStore(database.taskLocalStateDao())
    private val taskLocalState: TaskLocalStateStore get() = localStateStore
    val notificationStore: NotificationStore = RoomNotificationStore(database.notificationDao())
    val actionHistoryStore: ActionHistoryStore = RoomActionHistoryStore(database.actionHistoryDao())
    val labelStore: LabelStore = RoomLabelStore(database.labelDao())
    // Concrete types kept for the debug reset (clearAll), exposed as ports elsewhere.
    private val recurrenceConsent = RecurrenceConsentDataStore(appContext)
    private val boardSettingsDataStore = BoardSettingsDataStore(appContext)
    val appPreferences = AppPreferencesDataStore(appContext)

    val boardSettingsStore: BoardSettingsStore = boardSettingsDataStore

    // Phase 3 write layer (all logic in core). The single active undo and recurrence engine are
    // process singletons so undo survives navigation and recurrence math is shared.
    private val recurrenceEngine: RecurrenceEngine = RecurrenceEngineDmfs()
    val undoManager = UndoManager()
    val recurrenceSetup = RecurrenceSetupService(notionClient, mappingStore, recurrenceConsent)
    private val writeService = TaskWriteService(
        notion = notionClient,
        mappingStore = mappingStore,
        taskCache = taskCache,
        localState = taskLocalState,
        recurrence = recurrenceEngine,
        todayIso = { LocalDate.now().toString() },
        newId = { UUID.randomUUID().toString() },
    )

    // Services + repository.
    val onboardingService = OnboardingService(notionClient, mappingStore).apply {
        debugLogger = { Log.d("SiftDebug", it) }
    }
    val repository = SiftRepository(notionClient, mappingStore, taskCache, taskLocalState, tokenStore, writeService, applicationScope)

    init {
        notionClient.debugLogger = { Log.d("SiftDebug", it) }
    }

    // OAuth. Values come from local.properties via BuildConfig; the client secret stays
    // server-side in the proxy, never in the app. Falls back to placeholders when unset.
    //
    // Notion only accepts https redirect URIs, so the redirect_uri is the proxy's
    // /oauth/callback (registered with Notion); the proxy bounces it to sift://oauth. The
    // same https redirect_uri is sent in the token exchange so it matches the authorize call.
    private val proxyBase = BuildConfig.NOTION_OAUTH_PROXY_URL.trimEnd('/')
    private val oauthConfig = OAuthConfig(
        clientId = BuildConfig.NOTION_CLIENT_ID.ifBlank { OAuthConfig.PLACEHOLDER_CLIENT_ID },
        redirectUri = if (proxyBase.isBlank()) OAuthConfig.REDIRECT_URI else "$proxyBase/oauth/callback",
        tokenExchangeUrl = if (proxyBase.isBlank()) OAuthConfig.PLACEHOLDER_PROXY_URL else "$proxyBase/oauth/callback",
    )
    private val oauthFactory = OAuthRequestFactory(oauthConfig, SecureStateGenerator())
    val authRepository = AuthRepository(oauthFactory, transport, tokenStore)

    /**
     * Debug dogfooding: seed a personal integration token so the app is "connected" without
     * running OAuth. No-op in release and when no token is configured or one already exists.
     */
    fun seedDevTokenIfPresent(token: String) {
        if (token.isBlank()) return
        applicationScope.launch {
            if (tokenStore.load() == null) {
                tokenStore.save(TokenBundle(accessToken = token, workspaceName = "Dev workspace"))
            }
        }
    }

    /**
     * Debug tool: wipe all local state back to a fresh new-user install (token, mapping, board
     * settings, recurrence consent, cached tasks, and local operational state). Reseeds the dev
     * token when configured so dogfooding stays connected and lands at the door choice; a real
     * build (no dev token) becomes a true new user and routes to the welcome screen. Caller
     * navigates back through the onboarding gate afterwards.
     */
    suspend fun resetToNewUserState() {
        tokenStore.clear()
        mappingStore.save(MappingSet.EMPTY)
        boardSettingsDataStore.clearAll()
        appPreferences.clearAll()
        recurrenceConsent.clearAll()
        withContext(Dispatchers.IO) { database.clearAllTables() }
        val devToken = BuildConfig.SIFT_DEV_NOTION_TOKEN
        if (devToken.isNotBlank()) {
            tokenStore.save(TokenBundle(accessToken = devToken, workspaceName = "Dev workspace"))
        }
    }
}
