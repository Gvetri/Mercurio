package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.PendingRunSubmissionKey
import org.mercurio.client.feature.entry.domain.RunId
import org.mercurio.client.feature.entry.domain.RunSubmissionUncertaintySnapshot
import org.mercurio.client.feature.entry.domain.SessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DefaultRunSubmissionUncertaintyStoreTest {
    private val key = PendingRunSubmissionKey("https://gateway.example/profile", SessionId("session-1"))

    @Test
    fun an_older_attempt_cannot_update_or_remove_a_newer_marker() =
        runBlocking<Unit> {
            val store = DefaultRunSubmissionUncertaintyStore(InMemoryStorage())
            val known = RunId("known-run")
            val submitted = RunId("submitted-run")

            assertTrue(store.add(key, setOf(known), "current"))
            assertFalse(store.add(key, emptySet(), "old"))
            assertFalse(store.bindRun(key, submitted, "old"))
            assertFalse(store.markSettled(key, "old"))
            assertFalse(store.markAmbiguous(key, "old"))
            assertFalse(store.remove(key, "old"))
            assertFalse(store.removeIfKnownRunIdsMatch(key, setOf(known), "old"))
            assertEquals("current", store.attemptId(key))
            assertEquals(setOf(known), store.knownRunIds(key))
            assertEquals(null, store.boundRunId(key))
            assertFalse(store.isSettled(key))
            assertFalse(store.requiresRunMatch(key))
        }

    @Test
    fun removal_requires_the_complete_latest_uncertainty_snapshot() =
        runBlocking<Unit> {
            val store = DefaultRunSubmissionUncertaintyStore(InMemoryStorage())
            val runId = RunId("submitted-run")
            assertTrue(store.add(key, emptySet(), "attempt-1"))
            val initial = requireNotNull(store.snapshot(key))

            assertTrue(store.bindRun(key, runId, "attempt-1"))
            assertTrue(store.markSettled(key, "attempt-1"))
            assertTrue(store.markAmbiguous(key, "attempt-1"))
            assertFalse(store.removeIfSnapshotMatches(key, initial))
            assertEquals(runId, store.boundRunId(key))
            assertTrue(store.isSettled(key))
            assertTrue(store.requiresRunMatch(key))
            assertTrue(store.removeIfSnapshotMatches(key, requireNotNull(store.snapshot(key))))
            assertFalse(store.contains(key))
        }

    @Test
    fun a_storage_failure_does_not_report_an_accepted_submission() =
        runBlocking<Unit> {
            val storage = InMemoryStorage().apply { failWrites = true }
            val store = DefaultRunSubmissionUncertaintyStore(storage)

            assertThrows(IllegalStateException::class.java) { runBlocking { store.add(key, emptySet(), "attempt-1") } }
            assertFalse(store.contains(key))
        }

    @Test
    fun separate_stores_serialize_updates_to_the_same_attempt() =
        runBlocking<Unit> {
            val storage = InMemoryStorage()
            val first = DefaultRunSubmissionUncertaintyStore(storage)
            val second = DefaultRunSubmissionUncertaintyStore(storage)
            val firstRun = RunId("first-run")
            val secondRun = RunId("second-run")
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val workers = Executors.newFixedThreadPool(2)

            try {
                val firstUpdate =
                    workers.submit<Boolean> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        runBlocking { first.add(key, setOf(firstRun), "attempt-1") }
                    }
                val secondUpdate =
                    workers.submit<Boolean> {
                        ready.countDown()
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        runBlocking { second.add(key, setOf(secondRun), "attempt-1") }
                    }
                assertTrue(ready.await(5, TimeUnit.SECONDS))
                start.countDown()

                assertTrue(firstUpdate.get(5, TimeUnit.SECONDS))
                assertTrue(secondUpdate.get(5, TimeUnit.SECONDS))
                assertEquals(setOf(firstRun, secondRun), first.knownRunIds(key))
            } finally {
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            }
        }

    @Test
    fun a_matching_attempt_removal_deletes_the_marker() =
        runBlocking<Unit> {
            val store = DefaultRunSubmissionUncertaintyStore(InMemoryStorage())
            assertTrue(store.add(key, emptySet(), "attempt-1"))

            assertTrue(store.remove(key, "attempt-1"))

            assertFalse(store.contains(key))
        }

    @Test
    fun a_removal_holds_the_store_while_it_checks_so_a_racing_add_cannot_lose_its_marker() =
        runBlocking<Unit> {
            val storage = BlockingReadStorage()
            val removalStore = DefaultRunSubmissionUncertaintyStore(storage)
            val additionStore = DefaultRunSubmissionUncertaintyStore(storage)
            storage.seed(key, uncertaintySnapshot("attempt-1"))
            val workers = Executors.newFixedThreadPool(2)

            try {
                storage.blockNextRead()
                val removal =
                    workers.submit<Boolean> {
                        runBlocking { removalStore.remove(key, "attempt-1") }
                    }
                assertTrue("The removal must reach the storage read", storage.awaitBlockedRead())
                val addition =
                    workers.submit<Boolean> {
                        storage.markAddDispatched()
                        runBlocking { additionStore.add(key, emptySet(), "attempt-2") }
                    }
                assertTrue("The racing add must be dispatched", storage.awaitAddDispatched())
                assertFalse(
                    "A new attempt must not reach storage while the removal checks the old one",
                    storage.awaitAnotherRead(500, TimeUnit.MILLISECONDS),
                )
                storage.releaseBlockedRead()

                assertTrue(removal.get(5, TimeUnit.SECONDS))
                assertTrue(addition.get(5, TimeUnit.SECONDS))
                assertTrue(storage.removalRanBeforeTheNextRead())
                assertEquals("attempt-2", removalStore.attemptId(key))
            } finally {
                workers.shutdownNow()
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            }
        }

    private fun uncertaintySnapshot(attemptId: String): RunSubmissionUncertaintySnapshot =
        RunSubmissionUncertaintySnapshot(
            attemptId = attemptId,
            knownRunIds = emptySet(),
            boundRunId = null,
            settled = false,
            requiresRunMatch = false,
        )

    private class BlockingReadStorage : RunSubmissionUncertaintyStorage {
        private val records = ConcurrentHashMap<PendingRunSubmissionKey, RunSubmissionUncertaintySnapshot>()
        private val blockNextRead = AtomicBoolean(false)
        private val blockedReadActive = AtomicBoolean(false)
        private val readEntered = CountDownLatch(1)
        private val readAllowed = CountDownLatch(1)
        private val anotherRead = CountDownLatch(1)
        private val addDispatched = CountDownLatch(1)
        private val readCalls = java.util.concurrent.atomic.AtomicInteger(0)
        private val readsBeforeRemove = java.util.concurrent.atomic.AtomicInteger(-1)

        fun seed(
            key: PendingRunSubmissionKey,
            snapshot: RunSubmissionUncertaintySnapshot,
        ) {
            records[key] = snapshot
        }

        fun blockNextRead() {
            blockNextRead.set(true)
        }

        fun awaitBlockedRead(): Boolean = readEntered.await(5, TimeUnit.SECONDS)

        fun releaseBlockedRead() {
            readAllowed.countDown()
        }

        fun awaitAnotherRead(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = anotherRead.await(timeout, unit)

        fun markAddDispatched() {
            addDispatched.countDown()
        }

        fun awaitAddDispatched(): Boolean = addDispatched.await(5, TimeUnit.SECONDS)

        fun removalRanBeforeTheNextRead(): Boolean = readsBeforeRemove.get() == 1

        override fun read(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? {
            readCalls.incrementAndGet()
            val snapshot = records[key]
            if (blockedReadActive.get()) anotherRead.countDown()
            if (blockNextRead.compareAndSet(true, false)) {
                blockedReadActive.set(true)
                readEntered.countDown()
                check(readAllowed.await(5, TimeUnit.SECONDS)) { "The test did not release the blocked read." }
                blockedReadActive.set(false)
            }
            return snapshot
        }

        override fun write(
            key: PendingRunSubmissionKey,
            snapshot: RunSubmissionUncertaintySnapshot,
        ) {
            records[key] = snapshot
        }

        override fun remove(key: PendingRunSubmissionKey) {
            readsBeforeRemove.set(readCalls.get())
            records.remove(key)
        }
    }

    private class InMemoryStorage : RunSubmissionUncertaintyStorage {
        private val records = mutableMapOf<PendingRunSubmissionKey, RunSubmissionUncertaintySnapshot>()
        var failWrites = false

        override fun read(key: PendingRunSubmissionKey): RunSubmissionUncertaintySnapshot? = records[key]

        override fun write(
            key: PendingRunSubmissionKey,
            snapshot: RunSubmissionUncertaintySnapshot,
        ) {
            check(!failWrites) { "Storage is unavailable." }
            records[key] = snapshot
        }

        override fun remove(key: PendingRunSubmissionKey) {
            records.remove(key)
        }
    }
}
