package org.mercurio.client.feature.entry.wiring

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.GatewayCapabilities
import org.mercurio.client.feature.entry.domain.GatewayCredentialStore
import org.mercurio.client.feature.entry.domain.GatewayHistoryMessage
import org.mercurio.client.feature.entry.domain.HistoryCacheKey
import org.mercurio.client.feature.entry.domain.HistoryCacheStore
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.PublicBetaGatewayCapabilityManifest
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunEvent
import org.mercurio.client.feature.entry.domain.RunEventObservation
import org.mercurio.client.feature.entry.domain.RunGatewayPort
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunRecoveryEntry
import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionGatewayPort
import org.mercurio.client.feature.entry.domain.SessionHistory
import org.mercurio.client.feature.entry.domain.SessionId
import org.mercurio.client.feature.entry.domain.SessionListRequest
import org.mercurio.client.feature.entry.domain.SessionPage
import org.mercurio.client.feature.entry.domain.SessionPinResult
import org.mercurio.client.feature.entry.presentation.EntryStateHolder
import org.mercurio.client.feature.entry.presentation.EntryUiEvent
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryWiringRestartIntegrationTest {
    private val holders = mutableListOf<EntryStateHolder>()
    private val gateways = mutableSetOf<RestartGateway>()
    private val credentialStore = InMemoryGatewayCredentialStore()

    @Before
    fun clearSharedSubmissionState() {
        val context = RuntimeEnvironment.getApplication()
        val key = PendingRunSubmissionKey("https://gateway.example/profile", RestartGateway.SESSION_ID)
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(key) }
    }

    @After
    fun finishHolderWork() {
        gateways.forEach { gateway ->
            gateway.releaseCreate.complete(Unit)
            gateway.releaseStatus.complete(Unit)
        }
        holders.forEach(EntryStateHolder::close)
        holders.forEach(::joinHolder)
        gateways.flatMap { it.observations }.forEach { observation ->
            assertTrue(
                "every observation must be closed after the holder work finishes",
                observation.closed.isCompleted,
            )
            if (observation.started.isCompleted) {
                assertTrue("a started observation must finish", observation.finished.isCompleted)
            }
        }
    }

    @Test
    fun production_wiring_recovers_persisted_run_for_the_same_endpoint() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        val otherEndpointStorage =
            SharedPreferencesRunRecoveryStorage(context) { "https://other-gateway.example/profile" }
        storage.clearForTest()
        otherEndpointStorage.clearForTest()
        try {
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, " $endpoint ", hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                sendFromComposer(firstHolder, gateway, "Persist this run")
                awaitState(firstHolder) { it.sessionList?.openedSession?.latestRun?.id == gateway.activeRun.id }
                runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.observationStarted.await() } }
                awaitRecoveryEntries(storage, setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id)))
                assertEquals(1, storage.load().size)
                assertTrue(otherEndpointStorage.load().isEmpty())
            } finally {
                firstHolder.close()
                joinHolder(firstHolder)
            }

            gateway.terminal = true
            val restartedHolder = createHolder(context, gateway)
            try {
                connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                runBlocking {
                    withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) {
                        while (!gateway.statusRequests.contains(gateway.activeRun.id) || storage.load().isNotEmpty()) {
                            kotlinx.coroutines.delay(25)
                        }
                    }
                }
                assertTrue(storage.load().isEmpty())
                assertFalse(gateway.statusRequests.contains(gateway.externalRun.id))
            } finally {
                restartedHolder.close()
            }
        } finally {
            storage.clearForTest()
            otherEndpointStorage.clearForTest()
        }
    }

    @Test
    fun production_wiring_blocks_send_until_a_delayed_recovery_status_finishes() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        storage.clearForTest()
        try {
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, endpoint, hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                sendFromComposer(firstHolder, gateway, "Persist before reconnect")
                awaitState(firstHolder) { it.sessionList?.openedSession?.latestRun?.id == gateway.activeRun.id }
                awaitRecoveryEntries(storage, setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id)))
            } finally {
                firstHolder.close()
                joinHolder(firstHolder)
            }

            val expectedRecovery = setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id))
            assertEquals(expectedRecovery, storage.load())
            gateway.blockStatus = true
            val restartedHolder = createHolder(context, gateway)
            try {
                connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                openSession(restartedHolder, gateway.session.id)
                runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.statusStarted.await() } }
                restartedHolder.onEvent(EntryUiEvent.ComposerTextChanged("Do not duplicate"))
                restartedHolder.onEvent(EntryUiEvent.SendMessageClicked)
                val blocked = requireNotNull(requireNotNull(restartedHolder.uiState.value.sessionList).openedSession)
                assertFalse(blocked.isSending)
                assertEquals(1, gateway.runRequests.size)

                gateway.releaseStatus.complete(Unit)
                awaitState(restartedHolder) {
                    it.sessionList?.openedSession?.let { opened ->
                        !opened.isReconciliationInProgress &&
                            opened.activeRuns.any { run -> run.id == gateway.activeRun.id }
                    } == true
                }
            } finally {
                gateway.releaseStatus.complete(Unit)
                restartedHolder.close()
            }
        } finally {
            gateway.releaseStatus.complete(Unit)
            storage.clearForTest()
        }
    }

    @Test
    fun production_wiring_settles_recovery_when_the_run_status_is_terminal() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        storage.clearForTest()
        try {
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, endpoint, hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                sendFromComposer(firstHolder, gateway, "Keep recovery")
                awaitState(firstHolder) { it.sessionList?.openedSession?.latestRun?.id == gateway.activeRun.id }
                awaitRecoveryEntries(storage, setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id)))
            } finally {
                firstHolder.close()
                joinHolder(firstHolder)
            }

            assertEquals(setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id)), storage.load())
            gateway.statusByRun[gateway.activeRun.id] = gateway.activeRun.copy(status = "succeeded")
            val restartedHolder = createHolder(context, gateway)
            try {
                connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                openSession(restartedHolder, gateway.session.id)
                awaitCondition { gateway.statusRequests.contains(gateway.activeRun.id) }
                awaitState(restartedHolder) {
                    it.sessionList?.openedSession?.let { opened -> !opened.isReconciliationInProgress } == true
                }
                assertTrue(gateway.statusRequests.contains(gateway.activeRun.id))
                awaitCondition { storage.load().isEmpty() }
                assertTrue(storage.load().isEmpty())
            } finally {
                restartedHolder.close()
            }
        } finally {
            storage.clearForTest()
        }
    }

    @Test
    fun production_wiring_retains_recovery_when_closed_as_soon_as_the_run_is_visible() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        storage.clearForTest()
        val holder = createHolder(context, gateway)
        try {
            connect(holder, endpoint, hasSavedEndpoint = false)
            openSession(holder, gateway.session.id)
            sendFromComposer(holder, gateway, "Close immediately")
            awaitState(holder) { it.sessionList?.openedSession?.latestRun?.id == gateway.activeRun.id }

            holder.close()
            joinHolder(holder)

            assertEquals(setOf(RunRecoveryEntry(gateway.session.id, gateway.activeRun.id)), storage.load())
            assertEquals(1, gateway.runRequests.size)
        } finally {
            holder.close()
            joinHolder(holder)
            storage.clearForTest()
        }
    }

    @Test
    fun production_wiring_keeps_a_response_loss_uncertain_after_holder_recreation() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        val uncertaintyKey = PendingRunSubmissionKey(endpoint, RestartGateway.SESSION_ID)
        storage.clearForTest()
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        try {
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, endpoint, hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                gateway.failCreateAfterAcceptance = true
                sendFromComposer(firstHolder, gateway, "Response may be lost")
                awaitState(firstHolder) {
                    it.sessionList?.openedSession?.hasUnresolvedSubmission == true &&
                        it.sessionList?.openedSession?.isSending == false
                }
                assertEquals(1, gateway.runRequests.size)
                assertTrue(SharedPreferencesRunSubmissionUncertaintyStore(context).contains(uncertaintyKey))
                assertTrue(storage.load().isEmpty())
            } finally {
                firstHolder.close()
            }

            val restartedHolder = createHolder(context, gateway)
            try {
                connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                openSession(restartedHolder, gateway.session.id)
                awaitState(restartedHolder) {
                    it.sessionList?.openedSession?.hasUnresolvedSubmission == true &&
                        it.sessionList?.openedSession?.isSending == false
                }
                restartedHolder.onEvent(EntryUiEvent.SendMessageClicked)
                assertEquals(1, gateway.runRequests.size)
            } finally {
                restartedHolder.close()
            }
        } finally {
            storage.clearForTest()
            runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        }
    }

    @Test
    fun production_wiring_persists_uncertainty_before_a_create_returns() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val uncertaintyKey = PendingRunSubmissionKey(endpoint, RestartGateway.SESSION_ID)
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        storage.clearForTest()
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        try {
            gateway.blockCreate = true
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, endpoint, hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                sendFromComposer(firstHolder, gateway, "Keep one request")
                runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.runStarted.await() } }
                assertTrue(SharedPreferencesRunSubmissionUncertaintyStore(context).contains(uncertaintyKey))

                val restartedHolder = createHolder(context, gateway)
                try {
                    connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                    openSession(restartedHolder, gateway.session.id)
                    awaitState(restartedHolder) {
                        it.sessionList?.openedSession?.hasUnresolvedSubmission == true &&
                            !it.sessionList!!.openedSession!!.isSending
                    }
                    restartedHolder.onEvent(EntryUiEvent.SendMessageClicked)
                    assertEquals(1, gateway.runRequests.size)
                } finally {
                    restartedHolder.close()
                }
            } finally {
                firstHolder.close()
            }
            gateway.releaseCreate.complete(Unit)
            runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.runFinished.await() } }
            awaitCondition { !SharedPreferencesRunSubmissionUncertaintyStore(context).contains(uncertaintyKey) }
        } finally {
            gateway.releaseCreate.complete(Unit)
            storage.clearForTest()
            runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        }
    }

    @Test
    fun production_wiring_does_not_bind_a_known_recovery_run_to_a_new_uncertain_submission() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        val uncertaintyKey = PendingRunSubmissionKey(endpoint, RestartGateway.SESSION_ID)
        val knownRun = gateway.externalRun
        val submittedRun = gateway.activeRun
        storage.clearForTest()
        storage.save(
            setOf(
                RunRecoveryEntry(gateway.session.id, knownRun.id),
                RunRecoveryEntry(gateway.session.id, submittedRun.id),
            ),
        )
        gateway.statusByRun[knownRun.id] = knownRun
        gateway.statusByRun[submittedRun.id] = submittedRun
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).add(uncertaintyKey, setOf(knownRun.id)) }
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).markSettled(uncertaintyKey) }
        try {
            val holder = createHolder(context, gateway)
            try {
                connect(holder, endpoint, hasSavedEndpoint = false)
                openSession(holder, gateway.session.id)
                awaitState(holder) {
                    val opened = it.sessionList?.openedSession
                    opened != null &&
                        opened.hasUnresolvedSubmission &&
                        opened.activeRuns.any { run -> run.id == submittedRun.id } &&
                        !opened.isReconciliationInProgress
                }
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(gateway.runRequests.isEmpty())
                assertTrue(SharedPreferencesRunSubmissionUncertaintyStore(context).contains(uncertaintyKey))
            } finally {
                holder.close()
            }
        } finally {
            storage.clearForTest()
            runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        }
    }

    @Test
    fun production_wiring_settles_a_pending_create_when_the_holder_closes() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val uncertaintyKey = PendingRunSubmissionKey(endpoint, RestartGateway.SESSION_ID)
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        storage.clearForTest()
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        try {
            val firstHolder = createHolder(context, gateway)
            try {
                connect(firstHolder, endpoint, hasSavedEndpoint = false)
                openSession(firstHolder, gateway.session.id)
                gateway.blockCreate = true
                sendFromComposer(firstHolder, gateway, "Do not lose recovery")
                runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.runStarted.await() } }

                firstHolder.close()
                assertFalse(SharedPreferencesRunSubmissionUncertaintyStore(context).isSettled(uncertaintyKey))
            } finally {
                gateway.releaseCreate.complete(Unit)
                firstHolder.close()
            }
            runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.runFinished.await() } }
            awaitCondition { !SharedPreferencesRunSubmissionUncertaintyStore(context).contains(uncertaintyKey) }

            gateway.terminal = true
            val restartedHolder = createHolder(context, gateway)
            try {
                connect(restartedHolder, endpoint, hasSavedEndpoint = true)
                openSession(restartedHolder, gateway.session.id)
                awaitState(restartedHolder) {
                    val opened = it.sessionList?.openedSession
                    opened != null &&
                        !opened.hasUnresolvedSubmission &&
                        opened.activeRuns.isEmpty() &&
                        !opened.isReconciliationInProgress
                }
                assertEquals(1, gateway.runRequests.size)
            } finally {
                restartedHolder.close()
            }
        } finally {
            gateway.releaseCreate.complete(Unit)
            storage.clearForTest()
            runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        }
    }

    @Test
    fun production_wiring_settles_a_create_canceled_before_its_body_starts() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        val endpoint = "https://gateway.example/profile"
        val uncertaintyKey = PendingRunSubmissionKey(endpoint, RestartGateway.SESSION_ID)
        val storage = SharedPreferencesRunRecoveryStorage(context) { endpoint }
        val dispatcher = PausingDispatcher()
        storage.clearForTest()
        runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        try {
            val holder =
                createHolder(
                    context = context,
                    gateway = gateway,
                    coroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
                )
            try {
                connect(holder, endpoint, hasSavedEndpoint = false)
                openSession(holder, gateway.session.id)
                awaitComposerReady(holder)
                dispatcher.paused = true
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Cancel before create"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)

                holder.close()

                assertTrue(dispatcher.queuedCount > 0)
                var steps = 0
                while (dispatcher.queuedCount > 0 && steps < 50) {
                    dispatcher.runNext()
                    steps += 1
                }
                assertTrue(SharedPreferencesRunSubmissionUncertaintyStore(context).isSettled(uncertaintyKey))
                assertTrue(gateway.runRequests.isEmpty())
            } finally {
                holder.close()
                dispatcher.drain()
            }
        } finally {
            storage.clearForTest()
            runBlocking { SharedPreferencesRunSubmissionUncertaintyStore(context).remove(uncertaintyKey) }
        }
    }

    @Test
    fun production_wiring_awaits_a_gateway_create_signal_slower_than_five_seconds() {
        val context = RuntimeEnvironment.getApplication()
        val gateway = RestartGateway()
        gateway.blockCreate = true
        gateway.createSignalDelayMillis = SLOW_GATEWAY_SIGNAL_MILLIS
        val holder = createHolder(context, gateway)
        try {
            connect(holder, "https://gateway.example/profile", hasSavedEndpoint = false)
            openSession(holder, gateway.session.id)
            sendFromComposer(holder, gateway, "A loaded Runner answers slowly")
            runBlocking { withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { gateway.runStarted.await() } }
        } finally {
            gateway.releaseCreate.complete(Unit)
            holder.close()
        }
    }

    @Test
    fun production_wiring_sends_after_a_leftover_unresolved_submission_is_cleared() {
        val context = RuntimeEnvironment.getApplication()
        val key = PendingRunSubmissionKey("https://gateway.example/profile", RestartGateway.SESSION_ID)
        val store = SharedPreferencesRunSubmissionUncertaintyStore(context)
        runBlocking { store.remove(key) }
        runBlocking { store.add(key, emptySet(), "leftover-attempt") }
        clearSharedSubmissionState()
        val gateway = RestartGateway()
        val holder = createHolder(context, gateway)
        try {
            connect(holder, "https://gateway.example/profile", hasSavedEndpoint = false)
            openSession(holder, gateway.session.id)
            sendFromComposer(holder, gateway, "Send after the clear")
            awaitState(holder) { it.sessionList?.openedSession?.latestRun?.id == gateway.activeRun.id }
            assertEquals(1, gateway.runRequests.size)
        } finally {
            holder.close()
            runBlocking { store.remove(key) }
        }
    }

    private fun createHolder(
        context: Context,
        gateway: RestartGateway,
        coroutineScope: CoroutineScope? = null,
    ): EntryStateHolder =
        EntryWiring.createEntryStateHolder(
            context = context,
            overrides =
                EntryWiringOverrides(
                    capabilityDiscovery = { _, _ ->
                        GatewayCapabilities(
                            PublicBetaGatewayCapabilityManifest.current.requiredEndpoints,
                        )
                    },
                    sessionGatewayFactory = { _, _ -> gateway },
                    runGatewayFactory = { _, _ -> gateway },
                    coroutineScope = coroutineScope,
                    credentialStore = credentialStore,
                    historyCacheStore = InMemoryHistoryCacheStore(),
                ),
        ).also {
            holders += it
            gateways += gateway
        }

    private fun connect(
        holder: EntryStateHolder,
        endpoint: String,
        hasSavedEndpoint: Boolean,
    ) {
        if (!hasSavedEndpoint) {
            holder.onEvent(EntryUiEvent.AddGatewayConnectionClicked)
            holder.onEvent(EntryUiEvent.EndpointChanged(endpoint))
        }
        holder.onEvent(EntryUiEvent.BearerCredentialChanged("test-only-credential"))
        holder.onEvent(EntryUiEvent.VerifyGatewayConnectionClicked)
        awaitState(holder) { it.sessionList?.sessions?.singleOrNull()?.id == RestartGateway.SESSION_ID }
    }

    private fun openSession(
        holder: EntryStateHolder,
        sessionId: SessionId,
    ) {
        holder.onEvent(EntryUiEvent.SessionClicked(sessionId))
        awaitState(holder) { it.sessionList?.openedSession?.session?.id == sessionId }
    }

    private fun awaitComposerReady(holder: EntryStateHolder) {
        awaitState(holder) { state ->
            state.sessionList?.let { list ->
                val opened = list.openedSession
                opened != null &&
                    !list.hasPendingMutation &&
                    !opened.isRefreshing &&
                    !opened.isReconciliationInProgress &&
                    !opened.hasUnresolvedSubmission &&
                    !opened.isSending
            } == true
        }
    }

    private fun sendFromComposer(
        holder: EntryStateHolder,
        gateway: RestartGateway,
        text: String,
    ) {
        awaitComposerReady(holder)
        holder.onEvent(EntryUiEvent.ComposerTextChanged(text))
        holder.onEvent(EntryUiEvent.SendMessageClicked)
        awaitState(holder) {
            val opened = it.sessionList?.openedSession
            opened != null && (opened.isSending || gateway.runRequests.isNotEmpty())
        }
        val opened = requireNotNull(holder.uiState.value.sessionList?.openedSession)
        assertTrue(
            "The submission was dropped before it reached the Gateway.",
            opened.isSending || gateway.runRequests.isNotEmpty(),
        )
    }

    private fun awaitState(
        holder: EntryStateHolder,
        predicate: (org.mercurio.client.feature.entry.presentation.EntryUiState) -> Boolean,
    ) {
        runBlocking {
            withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) {
                holder.uiState.first(predicate)
            }
        }
    }

    private fun awaitRecoveryEntries(
        storage: SharedPreferencesRunRecoveryStorage,
        expected: Set<RunRecoveryEntry>,
    ) {
        runBlocking {
            withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) {
                while (storage.load() != expected) delay(10)
            }
        }
    }

    private fun awaitCondition(predicate: () -> Boolean) {
        runBlocking {
            withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) {
                while (!predicate()) delay(10)
            }
        }
    }

    private fun joinHolder(holder: EntryStateHolder) {
        val scope =
            EntryStateHolder::class.java.getDeclaredField("scope")
                .apply { isAccessible = true }
                .get(holder) as CoroutineScope
        runBlocking {
            withTimeout(BOUNDED_AWAIT_TIMEOUT_MILLIS) { scope.coroutineContext[Job]?.join() }
        }
    }

    private fun SharedPreferencesRunRecoveryStorage.clearForTest() {
        save(emptySet())
    }

    private class InMemoryGatewayCredentialStore : GatewayCredentialStore {
        private var credential: String? = null

        override fun load(): String? = credential

        override fun save(credential: String) {
            this.credential = credential
        }

        override fun clear() {
            credential = null
        }
    }

    private class RestartGateway :
        SessionGatewayPort,
        RunGatewayPort {
        companion object {
            val SESSION_ID = SessionId("wiring-session")
        }

        val session = Session(SESSION_ID, "Wiring session", null, pinned = false)
        val activeRun = Run(RunId("wiring-run"), SESSION_ID, "running")
        val externalRun = Run(RunId("external-run"), SESSION_ID, "succeeded")
        val statusRequests = CopyOnWriteArrayList<RunId>()
        val runRequests = CopyOnWriteArrayList<Pair<SessionId, String>>()
        val observationStarted = CompletableDeferred<Unit>()
        val observations = CopyOnWriteArrayList<BlockingObservation>()
        val statusByRun = ConcurrentHashMap<RunId, Run>()

        @Volatile
        var terminal = false

        @Volatile
        var failCreateAfterAcceptance = false

        @Volatile
        var acceptedRunVisible = false

        @Volatile
        var blockCreate = false

        @Volatile
        var blockStatus = false

        @Volatile
        var createSignalDelayMillis = 0L

        val runStarted = CompletableDeferred<Unit>()
        val runFinished = CompletableDeferred<Unit>()
        val releaseCreate = CompletableDeferred<Unit>()
        val statusStarted = CompletableDeferred<Unit>()
        val releaseStatus = CompletableDeferred<Unit>()

        override suspend fun listSessions(request: SessionListRequest): SessionPage = SessionPage(listOf(session), null)

        override suspend fun createSession(title: String?): Session = error("not used")

        override suspend fun openSession(sessionId: SessionId): Session = session

        override suspend fun loadSessionHistory(sessionId: SessionId): SessionHistory =
            if (acceptedRunVisible) {
                SessionHistory(
                    sessionId,
                    listOf(
                        GatewayHistoryMessage(
                            id = "accepted-result",
                            role = "assistant",
                            content = "Accepted result",
                            runId = activeRun.id,
                            runStatus = if (terminal) "succeeded" else activeRun.status,
                            runResult = null,
                        ),
                    ),
                )
            } else if (terminal) {
                SessionHistory(
                    sessionId,
                    listOf(
                        GatewayHistoryMessage(
                            id = "external-result",
                            role = "assistant",
                            content = "External result",
                            runId = externalRun.id,
                            runStatus = externalRun.status,
                            runResult = "External result",
                        ),
                        GatewayHistoryMessage(
                            id = "wiring-result",
                            role = "assistant",
                            content = "Recovered result",
                            runId = activeRun.id,
                            runStatus = "succeeded",
                            runResult = "Recovered result",
                        ),
                    ),
                )
            } else {
                SessionHistory(sessionId, emptyList())
            }

        override suspend fun renameSession(
            sessionId: SessionId,
            title: String,
        ): Session = error("not used")

        override suspend fun deleteSession(sessionId: SessionId): Unit = error("not used")

        override suspend fun pinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override suspend fun unpinSession(sessionId: SessionId): SessionPinResult = error("not used")

        override suspend fun createRun(
            sessionId: SessionId,
            input: String,
        ): Run {
            runRequests += sessionId to input
            if (createSignalDelayMillis > 0) delay(createSignalDelayMillis)
            if (failCreateAfterAcceptance) {
                acceptedRunVisible = true
                error("response lost after acceptance")
            }
            if (blockCreate) {
                runStarted.complete(Unit)
                try {
                    releaseCreate.await()
                } finally {
                    runFinished.complete(Unit)
                }
            }
            return activeRun
        }

        override suspend fun getRunStatus(runId: RunId): Run {
            statusRequests += runId
            if (blockStatus) {
                statusStarted.complete(Unit)
                releaseStatus.await()
            }
            return statusByRun[runId] ?: if (terminal) activeRun.copy(status = "succeeded") else activeRun
        }

        override suspend fun observeRun(runId: RunId): RunEventObservation =
            BlockingObservation(observationStarted).also {
                observations += it
            }
    }

    private class BlockingObservation(
        private val anyObservationStarted: CompletableDeferred<Unit>,
    ) : RunEventObservation {
        val started = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<Unit>()

        override suspend fun next(): RunEvent? {
            started.complete(Unit)
            anyObservationStarted.complete(Unit)
            return try {
                release.await()
                null
            } finally {
                finished.complete(Unit)
            }
        }

        override fun close() {
            closed.complete(Unit)
            release.complete(Unit)
        }
    }

    private class PausingDispatcher : CoroutineDispatcher() {
        @Volatile
        var paused = false

        private val queued = ConcurrentLinkedQueue<Runnable>()

        val queuedCount: Int
            get() = queued.size

        fun drain() {
            paused = false
            while (true) {
                val next = queued.poll() ?: return
                next.run()
            }
        }

        fun runNext() {
            val next = queued.poll() ?: return
            next.run()
        }

        override fun dispatch(
            context: kotlin.coroutines.CoroutineContext,
            block: Runnable,
        ) {
            if (paused) {
                queued += block
            } else {
                block.run()
            }
        }
    }

    private class InMemoryHistoryCacheStore : HistoryCacheStore {
        private val transcripts = mutableMapOf<HistoryCacheKey, List<GatewayHistoryMessage>>()
        private val sessionLists = mutableMapOf<String, List<Session>>()

        override fun read(key: HistoryCacheKey): List<GatewayHistoryMessage> = transcripts[key].orEmpty()

        override suspend fun write(
            key: HistoryCacheKey,
            messages: List<GatewayHistoryMessage>,
        ) {
            transcripts[key] = messages
        }

        override fun readSessionList(endpoint: String): List<Session> = sessionLists[endpoint].orEmpty()

        override suspend fun writeSessionList(
            endpoint: String,
            sessions: List<Session>,
        ) {
            sessionLists[endpoint] = sessions
        }

        override suspend fun clearEndpoint(endpoint: String) {
            sessionLists.remove(endpoint)
            transcripts.keys.removeAll { it.endpoint == endpoint }
        }

        override suspend fun clear() {
            transcripts.clear()
            sessionLists.clear()
        }
    }

    private companion object {
        const val BOUNDED_AWAIT_TIMEOUT_MILLIS = 60_000L

        const val SLOW_GATEWAY_SIGNAL_MILLIS = 5_500L
    }
}
