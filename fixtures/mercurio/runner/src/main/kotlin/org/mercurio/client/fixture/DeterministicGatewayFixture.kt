package org.mercurio.client.fixture

import java.io.File
import java.net.URI

/** Runs one repository-owned deterministic Gateway fixture lifecycle. */
class DeterministicGatewayFixture(
    private val descriptorFile: File,
    private val processFactory: GatewayProcessFactory =
        GatewayProcessFactory { descriptor -> PinnedHermesFixtureLauncher.start(descriptor) },
    private val readinessChecker: FixtureReadinessChecker = HttpFixtureReadinessChecker,
    private val syntheticState: SyntheticTestState = SyntheticTestState(),
) {
    private var descriptor: PinnedFixtureDescriptor? = null
    private var process: GatewayProcess? = null

    var lifecycleState: FixtureLifecycleState = FixtureLifecycleState.NEW
        private set

    val endpoint: URI
        get() = requireProcess().endpoint

    val lastSyntheticState: Map<String, String>
        get() = syntheticState.snapshot()

    fun setup() {
        check(lifecycleState == FixtureLifecycleState.NEW) {
            "Fixture setup is only valid for a new fixture."
        }
        val loadedDescriptor = PinnedFixtureDescriptor.load(descriptorFile)
        descriptor = loadedDescriptor
        try {
            val startedProcess = processFactory.start(loadedDescriptor)
            process = startedProcess
            check(startedProcess.isRunning) { "Deterministic Gateway process did not start." }
            check(startedProcess.provenanceValue == loadedDescriptor.provenanceValue) {
                "Deterministic Gateway process did not use the pinned fixture provenance."
            }
            requireLocalEndpoint(startedProcess.endpoint)
            lifecycleState = FixtureLifecycleState.STARTED
        } catch (error: FixtureStartupException) {
            failStartup(error)
        } catch (expectedError: Throwable) {
            failStartup(FixtureStartupException("Deterministic Gateway fixture startup failed.", expectedError))
        }
    }

    private fun failStartup(startupFailure: FixtureStartupException): Nothing {
        val startedProcess = process
        if (startedProcess != null) {
            var stopFailed = false
            try {
                startedProcess.stop()
            } catch (expectedStopError: Throwable) {
                stopFailed = true
                startupFailure.addSuppressed(expectedStopError)
            }
            if (!stopFailed && !startedProcess.isRunning) {
                process = null
            } else if (startedProcess.isRunning) {
                startupFailure.addSuppressed(
                    IllegalStateException("Deterministic Gateway process did not stop after startup failure."),
                )
            }
        }
        throw startupFailure
    }

    fun awaitReady() {
        check(lifecycleState == FixtureLifecycleState.STARTED) {
            "Fixture readiness requires a started fixture."
        }
        try {
            readinessChecker.verify(requireProcess(), requireNotNull(descriptor))
            lifecycleState = FixtureLifecycleState.READY
        } catch (error: FixtureReadinessException) {
            throw error
        } catch (expectedError: Throwable) {
            throw FixtureReadinessException(
                "Deterministic Gateway fixture readiness failed: ${expectedError.message}",
                expectedError,
            )
        }
    }

    fun <T> runTest(block: (FixtureTestContext) -> T): T {
        check(lifecycleState == FixtureLifecycleState.READY) {
            "Fixture test execution requires an explicitly ready fixture."
        }
        resetAndVerifySyntheticState()
        lifecycleState = FixtureLifecycleState.TESTING
        val executed =
            runCatching {
                val activeProcess = requireProcess()
                block(FixtureTestContext(activeProcess.endpoint, activeProcess.provenanceValue, syntheticState))
            }
        val cleanupFailure = resetSyntheticStateAfterTest()
        lifecycleState = FixtureLifecycleState.READY
        return executed
            .onSuccess { result ->
                if (cleanupFailure != null) throw cleanupFailure
                result
            }
            .getOrElse { error ->
                cleanupFailure?.let(error::addSuppressed)
                throw error
            }
    }

    private fun resetSyntheticStateAfterTest(): FixtureCleanupException? =
        try {
            resetAndVerifySyntheticState()
            null
        } catch (expectedError: Throwable) {
            FixtureCleanupException(
                "Synthetic state reset after the fixture test failed.",
                listOf(expectedError),
            )
        }

    fun <T> execute(block: (FixtureTestContext) -> T): T {
        val executed =
            runCatching {
                setup()
                awaitReady()
                runTest(block)
            }
        return executed.fold(
            onSuccess = { result ->
                teardown()
                result
            },
            onFailure = { error ->
                try {
                    teardown()
                } catch (cleanupFailure: FixtureCleanupException) {
                    error.addSuppressed(cleanupFailure)
                }
                throw error
            },
        )
    }

    fun teardown() {
        if (
            lifecycleState == FixtureLifecycleState.TORN_DOWN &&
            process == null &&
            syntheticState.snapshot().isEmpty()
        ) {
            return
        }

        val cleanupFailures = mutableListOf<Throwable>()
        try {
            resetAndVerifySyntheticState()
        } catch (expectedError: Throwable) {
            cleanupFailures += expectedError
        }

        val activeProcess = process
        if (activeProcess != null) {
            var stopFailed = false
            try {
                activeProcess.stop()
            } catch (expectedError: Throwable) {
                stopFailed = true
                cleanupFailures += expectedError
            }
            if (activeProcess.isRunning) {
                cleanupFailures += IllegalStateException("Deterministic Gateway process did not stop.")
            }
            if (!stopFailed && !activeProcess.isRunning) {
                process = null
            }
        }
        lifecycleState = FixtureLifecycleState.TORN_DOWN

        if (cleanupFailures.isNotEmpty()) {
            throw FixtureCleanupException("Deterministic Gateway fixture cleanup failed.", cleanupFailures)
        }
    }

    private fun requireProcess(): GatewayProcess {
        return requireNotNull(process) { "Deterministic Gateway fixture is not started." }
    }

    private fun resetAndVerifySyntheticState() {
        syntheticState.reset()
        check(syntheticState.snapshot().isEmpty()) {
            "Deterministic Gateway synthetic state did not reset."
        }
    }

    private fun requireLocalEndpoint(endpoint: URI) {
        require(endpoint.scheme == "http") { "Deterministic Gateway fixture must use HTTP on loopback." }
        require(endpoint.host in setOf("127.0.0.1", "localhost", "::1")) {
            "Deterministic Gateway fixture must bind to loopback, not ${endpoint.host}."
        }
    }

    companion object {
        fun fromDescriptor(descriptorFile: File): DeterministicGatewayFixture {
            return DeterministicGatewayFixture(descriptorFile)
        }
    }
}
