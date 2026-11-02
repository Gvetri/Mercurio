package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.application.RemoveGatewayConnection
import org.mercurio.client.feature.entry.application.VerifyGatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.mercurio.client.feature.entry.domain.ModelSelectionStore
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.Run
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunStatusNotificationSettingsStore
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintyStore
import org.mercurio.client.feature.entry.domain.SessionDraftKey
import org.mercurio.client.feature.entry.domain.SessionDraftStore
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val SEND_CLAIM_AND_CLOSE_MARKER_ADDS = 2

internal class MainThreadStorageBoundaryTest {
    private val eventThread = Thread.currentThread()
    private val recorder = StorageCallRecorder()
    private val session = session("session-1")
    private val gateway = FakeGateway(listOf(session))
    private val uncertaintyStore = RecordingUncertaintyStore(recorder)
    private val draftStore = RecordingDraftStore(recorder)
    private val selectionStore = RecordingModelSelectionStore(recorder)
    private val repository = RecordingConnectionRepository(recorder)
    private val notificationSettingsStore = RecordingRunStatusNotificationSettingsStore(recorder)

    @Test
    fun a_send_keeps_run_storage_off_the_event_thread() =
        runTest {
            val holder = storageBoundaryHolder()
            try {
                open(holder, gateway, session.id)
                gateway.enqueueRun(activeRun())
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                recorder.assertOperationsRanOn(eventThread, "draft.write", "uncertainty.contains")
                recorder.reset()

                holder.onEvent(EntryUiEvent.SendMessageClicked)
                awaitState(holder) { it.sessionList?.openedSession?.isSending == false }
                awaitState(holder) { it.sessionList?.openedSession?.latestRun != null }

                recorder.assertOperationsRanOffThread(
                    eventThread,
                    "draft.write",
                    "selection.read",
                    "uncertainty.add",
                    "recovery.save",
                )
            } finally {
                holder.close()
            }
        }

    @Test
    fun connection_removal_never_touches_storage_on_the_event_thread() =
        runTest {
            val holder = storageBoundaryHolder()
            try {
                connect(holder, gateway)
                recorder.reset()

                holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                awaitState(holder) { !it.isGatewayConnectionConfigured && it.sessionList == null }
                assertTrue(selectionStore.cleared.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

                recorder.assertOperationsRanOffThread(
                    eventThread,
                    "repository.load",
                    "repository.clear",
                    "uncertainty.clearEndpoint",
                    "draft.clearEndpoint",
                    "selection.clearEndpoint",
                )
                recorder.assertOperationsNeverRan("notificationSettings.load", "notificationSettings.save")
            } finally {
                holder.close()
            }
        }

    @Test
    fun close_persists_a_pending_submission_off_the_event_thread() =
        runTest {
            val blockedGateway =
                FakeGateway(listOf(session)).apply {
                    enqueueRun(activeRun())
                    blockRunCreation = true
                }
            val holder = storageBoundaryHolder(blockedGateway)
            try {
                open(holder, blockedGateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(blockedGateway.runStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                recorder.reset()

                holder.close()
                recorder.assertNoCallOn(eventThread)
                assertTrue(uncertaintyStore.awaitAdds())
                recorder.assertOperationsRanOffThread(eventThread, "uncertainty.add")
            } finally {
                blockedGateway.releaseRun.countDown()
            }
        }

    @Test
    fun connection_removal_writes_the_pending_marker_before_it_purges() =
        runTest {
            val blockedGateway =
                FakeGateway(listOf(session)).apply {
                    enqueueRun(activeRun())
                    blockRunCreation = true
                }
            val holder = storageBoundaryHolder(blockedGateway)
            try {
                open(holder, blockedGateway, session.id)
                holder.onEvent(EntryUiEvent.ComposerTextChanged("Run once"))
                holder.onEvent(EntryUiEvent.SendMessageClicked)
                assertTrue(blockedGateway.runStarted.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                recorder.reset()

                holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
                holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
                assertTrue(selectionStore.cleared.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

                recorder.assertRanInOrder("uncertainty.add", "repository.clear", "uncertainty.clearEndpoint")
            } finally {
                blockedGateway.releaseRun.countDown()
                holder.close()
            }
        }

    @Test
    fun model_picker_selection_never_touches_storage_on_the_event_thread() =
        runTest {
            val holder = storageBoundaryHolder()
            try {
                connect(holder, gateway)
                recorder.reset()

                holder.onEvent(EntryUiEvent.ModelPickerClicked)
                awaitState(holder) { it.sessionList?.modelPicker?.isPickerOpen == true }
                holder.onEvent(EntryUiEvent.ModelRowClicked("alpha"))
                awaitState(holder) { it.sessionList?.modelPicker?.selection?.model == "alpha" }

                recorder.assertOperationsRanOffThread(eventThread, "selection.read", "selection.write")
            } finally {
                holder.close()
            }
        }

    private fun storageBoundaryHolder(holderGateway: FakeGateway = gateway): EntryStateHolder =
        EntryStateHolder(
            initialState = EntryState(isGatewayConnectionConfigured = false),
            verifyGatewayConnection =
                VerifyGatewayConnection(repository) { _, _ -> clientManifestCapabilities() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            sessionGatewayFactory = { _, _ -> holderGateway },
            runGatewayFactory = { _, _ -> holderGateway },
            dependencies =
                EntryStateHolderDependencies(
                    removeGatewayConnectionUseCase =
                        RemoveGatewayConnection(repository) { endpoint ->
                            endpoint?.let(uncertaintyStore::clearEndpoint)
                            endpoint?.let(draftStore::clearEndpoint)
                            endpoint?.let(selectionStore::clearEndpoint)
                        },
                    runSubmissionUncertaintyStore = uncertaintyStore,
                    sessionDraftStore = draftStore,
                    modelSelectionStore = selectionStore,
                    runStatusNotificationSettingsStore = notificationSettingsStore,
                    persistRunRecoveryEntry = { _, _ -> recorder.record("recovery.save") },
                ),
        )
}

private fun activeRun(): Run = Run(RunId("run-1"), SessionId("session-1"), "starting")

private class StorageCallRecorder {
    private val calls = CopyOnWriteArrayList<StorageCall>()

    fun record(operation: String) {
        calls += StorageCall(operation, Thread.currentThread())
    }

    fun reset() {
        calls.clear()
    }

    fun assertNoCallOn(thread: Thread) {
        val violations = calls.filter { it.thread === thread }
        assertTrue("storage ran on ${thread.name}: $violations", violations.isEmpty())
    }

    fun assertOperationsRanOffThread(
        thread: Thread,
        vararg operations: String,
    ) {
        operations.forEach { operation ->
            val matching = calls.filter { it.operation == operation }
            assertFalse("$operation never ran", matching.isEmpty())
            assertTrue("$operation ran on ${thread.name}", matching.none { it.thread === thread })
        }
    }

    fun assertOperationsRanOn(
        thread: Thread,
        vararg operations: String,
    ) {
        operations.forEach { operation ->
            val matching = calls.filter { it.operation == operation }
            assertFalse("$operation never ran", matching.isEmpty())
            assertTrue("$operation no longer runs on ${thread.name}", matching.any { it.thread === thread })
        }
    }

    fun assertOperationsNeverRan(vararg operations: String) {
        operations.forEach { operation ->
            val matching = calls.filter { it.operation == operation }
            assertTrue("$operation ran: $matching", matching.isEmpty())
        }
    }

    fun assertRanInOrder(vararg operations: String) {
        val indexes =
            operations.map { operation ->
                val index = calls.indexOfFirst { it.operation == operation }
                assertTrue("$operation never ran", index >= 0)
                index
            }
        assertTrue(
            "${operations.joinToString()} did not run in order: ${calls.map { it.operation }}",
            indexes == indexes.sorted(),
        )
    }

    private data class StorageCall(val operation: String, val thread: Thread)
}

private class RecordingDraftStore(private val recorder: StorageCallRecorder) : SessionDraftStore {
    private val drafts = mutableMapOf<SessionDraftKey, String>()

    override fun read(key: SessionDraftKey): String? {
        recorder.record("draft.read")
        return synchronized(drafts) { drafts[key] }
    }

    override fun write(
        key: SessionDraftKey,
        text: String,
    ) {
        recorder.record("draft.write")
        synchronized(drafts) { drafts[key] = text }
    }

    override fun remove(key: SessionDraftKey) {
        recorder.record("draft.remove")
        synchronized(drafts) { drafts.remove(key) }
    }

    override fun clearEndpoint(endpoint: String) {
        recorder.record("draft.clearEndpoint")
        synchronized(drafts) { drafts.keys.removeAll { key -> key.endpoint == endpoint } }
    }
}

private class RecordingModelSelectionStore(private val recorder: StorageCallRecorder) : ModelSelectionStore {
    private var selection: ModelSelection? = null
    val cleared = CountDownLatch(1)

    override fun read(endpoint: String): ModelSelection? {
        recorder.record("selection.read")
        return selection
    }

    override fun write(
        endpoint: String,
        selection: ModelSelection,
    ) {
        recorder.record("selection.write")
        this.selection = selection
    }

    override fun clear(endpoint: String) {
        recorder.record("selection.clear")
        selection = null
    }

    override fun favorites(endpoint: String): Set<String> {
        recorder.record("selection.favorites")
        return emptySet()
    }

    override fun setFavorite(
        endpoint: String,
        modelId: String,
        favorite: Boolean,
    ) {
        recorder.record("selection.setFavorite")
    }

    override fun recents(endpoint: String): List<String> {
        recorder.record("selection.recents")
        return emptyList()
    }

    override fun recordUse(
        endpoint: String,
        modelId: String,
    ) {
        recorder.record("selection.recordUse")
    }

    override fun clearEndpoint(endpoint: String) {
        recorder.record("selection.clearEndpoint")
        selection = null
        cleared.countDown()
    }
}

private class RecordingUncertaintyStore(private val recorder: StorageCallRecorder) : RunSubmissionUncertaintyStore {
    private val keys = mutableSetOf<PendingRunSubmissionKey>()
    private val adds = CountDownLatch(SEND_CLAIM_AND_CLOSE_MARKER_ADDS)

    fun awaitAdds(): Boolean = adds.await(ASYNC_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)

    override fun add(
        key: PendingRunSubmissionKey,
        knownRunIds: Set<RunId>,
        attemptId: String?,
    ): Boolean {
        recorder.record("uncertainty.add")
        synchronized(keys) { keys += key }
        adds.countDown()
        return true
    }

    override fun remove(
        key: PendingRunSubmissionKey,
        attemptId: String?,
    ): Boolean {
        recorder.record("uncertainty.remove")
        return synchronized(keys) { keys.remove(key) }
    }

    override fun contains(key: PendingRunSubmissionKey): Boolean {
        recorder.record("uncertainty.contains")
        return synchronized(keys) { key in keys }
    }

    override fun clearEndpoint(endpoint: String) {
        recorder.record("uncertainty.clearEndpoint")
        synchronized(keys) { keys.removeAll { key -> key.endpoint == endpoint } }
    }

    override fun snapshot(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? = null
}

private class RecordingConnectionRepository(private val recorder: StorageCallRecorder) : GatewayConnectionRepository {
    private var saved: GatewayConnection? = null

    override fun load(): GatewayConnection? {
        recorder.record("repository.load")
        return saved
    }

    override fun save(connection: GatewayConnection) {
        recorder.record("repository.save")
        saved = connection
    }

    override fun clear() {
        recorder.record("repository.clear")
        saved = null
    }
}

private class RecordingRunStatusNotificationSettingsStore(
    private val recorder: StorageCallRecorder,
) : RunStatusNotificationSettingsStore {
    private var enabled = false

    override fun loadEnabled(): Boolean {
        recorder.record("notificationSettings.load")
        return enabled
    }

    override fun saveEnabled(enabled: Boolean) {
        recorder.record("notificationSettings.save")
        this.enabled = enabled
    }
}
