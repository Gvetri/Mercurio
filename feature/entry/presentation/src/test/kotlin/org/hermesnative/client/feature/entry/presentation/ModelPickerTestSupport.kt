package org.hermesnative.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.hermesnative.client.feature.entry.application.EntryState
import org.hermesnative.client.feature.entry.application.RemoveGatewayConnection
import org.hermesnative.client.feature.entry.application.VerifyGatewayConnection
import org.hermesnative.client.feature.entry.data.DefaultGatewayConnectionRepository
import org.hermesnative.client.feature.entry.data.InMemoryGatewayConnectionDataSource
import org.hermesnative.client.feature.entry.domain.GatewayCapabilities
import org.hermesnative.client.feature.entry.domain.GatewayEndpoint
import org.hermesnative.client.feature.entry.domain.GatewayErrorCategory
import org.hermesnative.client.feature.entry.domain.GatewayException
import org.hermesnative.client.feature.entry.domain.ModelCatalog
import org.hermesnative.client.feature.entry.domain.ModelCatalogEntry
import org.hermesnative.client.feature.entry.domain.ModelEffortOptions
import org.hermesnative.client.feature.entry.domain.ModelGatewayPort
import org.hermesnative.client.feature.entry.domain.ModelSelection
import org.hermesnative.client.feature.entry.domain.ModelSelectionStore
import org.hermesnative.client.feature.entry.domain.NoOpModelSelectionStore
import org.hermesnative.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.hermesnative.client.feature.entry.domain.Run
import org.hermesnative.client.feature.entry.domain.RunEventObservation
import org.hermesnative.client.feature.entry.domain.RunGatewayPort
import org.hermesnative.client.feature.entry.domain.RunId
import org.hermesnative.client.feature.entry.domain.Session
import org.hermesnative.client.feature.entry.domain.SessionGatewayPort
import org.hermesnative.client.feature.entry.domain.SessionHistory
import org.hermesnative.client.feature.entry.domain.SessionId
import org.hermesnative.client.feature.entry.domain.SessionListRequest
import org.hermesnative.client.feature.entry.domain.SessionPage
import org.hermesnative.client.feature.entry.domain.SessionPinResult

internal const val PICKER_ENDPOINT = "https://gateway.example/profile"
internal val PICKER_SESSION_ID = SessionId("session-1")

internal fun pickerCapabilities(withRunModelsRow: Boolean): GatewayCapabilities {
    val endpoints = PublicBetaGatewayCapabilityManifest.current.requiredEndpoints.toMutableMap()
    if (withRunModelsRow) {
        endpoints[PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint.endpoint] =
            GatewayEndpoint(
                PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint.method,
                PublicBetaGatewayCapabilityManifest.modelCatalogEndpoint.path,
            )
    }
    return GatewayCapabilities(endpoints)
}

internal fun pickerCatalog(): ModelCatalog =
    ModelCatalog(
        discriminator = "hermes.api_server.run_models",
        defaultModel = "alpha",
        entries =
            listOf(
                ModelCatalogEntry("alpha", "First Model", "opencode-go", null),
                ModelCatalogEntry("beta", "Second Model", "opencode-go", null),
            ),
    )

internal fun pickerEffortCatalog(): ModelCatalog =
    ModelCatalog(
        discriminator = "hermes.api_server.run_models",
        defaultModel = "alpha",
        entries =
            listOf(
                ModelCatalogEntry(
                    "alpha",
                    "First Model",
                    "opencode-go",
                    ModelEffortOptions(
                        listOf("low", "medium", "high"),
                        "medium",
                    ),
                ),
                ModelCatalogEntry("beta", "Second Model", "opencode-go", null),
                ModelCatalogEntry("gamma", "Third Model", null, null),
                ModelCatalogEntry("delta", "Fourth Model", null, null),
            ),
    )

/**
 * A model-catalog port double: serves a canned result and counts fetches so tests can
 * prove the fail-closed ladder never fetches or refetches when it must not.
 */
internal class PickerFakeGateway(
    private val delegate: RetryFakeGateway,
    var catalogResult: Result<ModelCatalog> =
        Result.failure(GatewayException(GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE)),
) : SessionGatewayPort by delegate,
    RunGatewayPort by delegate,
    ModelGatewayPort {
    var fetchCount = 0

    override fun fetchModelCatalog(): ModelCatalog {
        fetchCount += 1
        return catalogResult.getOrThrow()
    }
}

internal class FakePickerSelectionStore(
    var selection: ModelSelection? = null,
    private var favorites: Set<String> = emptySet(),
    private var recents: List<String> = emptyList(),
) : ModelSelectionStore {
    val writes = mutableListOf<Pair<String, ModelSelection>>()
    val clears = mutableListOf<String>()
    val favoriteCalls = mutableListOf<Triple<String, String, Boolean>>()
    val useLog = mutableListOf<Pair<String, String>>()

    override fun read(endpoint: String): ModelSelection? = selection

    override fun write(
        endpoint: String,
        selection: ModelSelection,
    ) {
        writes += endpoint to selection
        this.selection = selection
    }

    override fun clear(endpoint: String) {
        clears += endpoint
        selection = null
    }

    override fun favorites(endpoint: String): Set<String> = favorites

    override fun setFavorite(
        endpoint: String,
        modelId: String,
        favorite: Boolean,
    ) {
        favoriteCalls += Triple(endpoint, modelId, favorite)
        favorites = if (favorite) favorites + modelId else favorites - modelId
    }

    override fun recents(endpoint: String): List<String> = recents

    override fun recordUse(
        endpoint: String,
        modelId: String,
    ) {
        useLog += endpoint to modelId
        recents = (listOf(modelId) + recents.filterNot { it == modelId }).take(5)
    }
}

internal val PICKER_CREATED_SESSION_ID = SessionId("created-session")

/**
 * A direct three-port double for the create-session path: it lands a created Session
 * on the composer without any SessionClicked and counts catalog fetches, so a test
 * can prove the picker is armed by that landing rather than by opening a Session.
 */
internal class PickerCreateFakeGateway(
    var catalogResult: Result<ModelCatalog> = Result.success(pickerCatalog()),
) : SessionGatewayPort,
    RunGatewayPort,
    ModelGatewayPort {
    var fetchCount = 0
    private var created: Session? = null

    override fun fetchModelCatalog(): ModelCatalog {
        fetchCount += 1
        return catalogResult.getOrThrow()
    }

    override fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listOfNotNull(created), null)

    override fun createSession(title: String?): Session =
        Session(PICKER_CREATED_SESSION_ID, title, "Preview", pinned = false).also { created = it }

    override fun openSession(sessionId: SessionId): Session = created ?: error("session was never created")

    override fun loadSessionHistory(sessionId: SessionId): SessionHistory = SessionHistory(sessionId, emptyList())

    override fun renameSession(
        sessionId: SessionId,
        title: String,
    ): Session = error("not used")

    override fun deleteSession(sessionId: SessionId): Unit = error("not used")

    override fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")

    override fun createRun(
        sessionId: SessionId,
        input: String,
    ): Run = error("not used")

    override fun getRunStatus(runId: RunId): Run = error("not used")

    override fun observeRun(runId: RunId): RunEventObservation = error("not used")
}

internal fun pickerHolder(
    gateway: Any,
    capabilities: GatewayCapabilities = pickerCapabilities(withRunModelsRow = true),
    store: ModelSelectionStore = NoOpModelSelectionStore,
): EntryStateHolder = pickerHolder(gateway, store = store) { capabilities }

internal fun pickerHolder(
    gateway: Any,
    store: ModelSelectionStore = NoOpModelSelectionStore,
    capabilitiesProvider: () -> GatewayCapabilities,
): EntryStateHolder =
    EntryStateHolder(
        initialState = EntryState(isGatewayConnectionConfigured = false),
        verifyGatewayConnection =
            VerifyGatewayConnection(
                DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource()),
            ) { _, _ -> capabilitiesProvider() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        sessionGatewayFactory = { _, _ -> gateway as SessionGatewayPort },
        runGatewayFactory = { _, _ -> gateway as RunGatewayPort },
        dependencies =
            EntryStateHolderDependencies(
                removeGatewayConnectionUseCase =
                    RemoveGatewayConnection(
                        DefaultGatewayConnectionRepository(InMemoryGatewayConnectionDataSource()),
                    ),
                modelSelectionStore = store,
            ),
    )

internal fun EntryStateHolder.connectAndOpenPickerSession(gateway: RetryFakeGateway) {
    connectRetryGateway(this, gateway)
    onEvent(EntryUiEvent.SessionClicked(PICKER_SESSION_ID))
    awaitRetryState(this) { it.sessionList?.openedSession?.session?.id == PICKER_SESSION_ID }
}

internal fun awaitPickerState(
    holder: EntryStateHolder,
    predicate: (ModelPickerUiState) -> Boolean,
) {
    awaitRetryState(holder) { predicate(it.sessionList?.modelPicker ?: ModelPickerUiState()) }
}
