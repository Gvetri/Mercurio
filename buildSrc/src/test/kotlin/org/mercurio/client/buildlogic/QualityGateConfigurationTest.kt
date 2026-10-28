package org.mercurio.client.buildlogic

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGateConfigurationTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("architecture.projectDir")) {
                "architecture.projectDir must identify the repository root"
            },
        )

    private fun assertPosixWrapperSupport() {
        assertTrue(
            "The evidence wrapper requires a POSIX filesystem.",
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        )
        listOf("bash", "timeout", "python3").forEach { tool ->
            assertTrue(
                "The evidence wrapper requires $tool.",
                System.getenv("PATH").orEmpty().split(File.pathSeparator).any { directory ->
                    File(directory, tool).canExecute()
                },
            )
        }
    }

    private fun runPythonSuite(relativePath: String) {
        assertPosixWrapperSupport()
        val process = ProcessBuilder("python3", repositoryRoot.resolve(relativePath).absolutePath)
            .directory(repositoryRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun test_job_failures_preserve_gating_reporting_and_redaction() {
        assertPosixWrapperSupport()
        val process = ProcessBuilder("python3", repositoryRoot.resolve(".github/scripts/tests/test_test_jobs.py").absolutePath)
            .directory(repositoryRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun nightly_watchdog_reports_starvation_without_changing_releases() {
        runPythonSuite(".github/scripts/tests/test_nightly_watchdog.py")
    }

    @Test
    fun nightly_release_validates_main_tip_and_publishes_verified_metadata() {
        assertPosixWrapperSupport()
        val process = ProcessBuilder("python3", repositoryRoot.resolve(".github/scripts/tests/test_nightly_release.py").absolutePath)
            .directory(repositoryRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun stable_beta_requires_closed_milestone_exact_candidate_and_fresh_approval() {
        assertPosixWrapperSupport()
        val process = ProcessBuilder("python3", repositoryRoot.resolve(".github/scripts/tests/test_stable_public_beta.py").absolutePath)
            .directory(repositoryRoot)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun release_signing_fails_closed_and_verifies_apk_identity() {
        assertPosixWrapperSupport()
        val process =
            ProcessBuilder(
                "python3", "-m", "unittest", "discover",
                "-s", ".github/scripts/tests", "-p", "test_*sign*.py",
            )
                .directory(repositoryRoot)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, 0, process.waitFor())
    }

    @Test
    fun nightly_draft_recovery_is_local_guarded_and_reads_back_state() {
        runPythonSuite(".github/scripts/tests/test_clear_nightly_draft.py")
    }

    @Test
    fun published_documentation_is_verified_for_links_safety_and_scope() {
        runPythonSuite(".github/scripts/tests/test_public_documentation.py")
    }

    @Test
    fun published_release_artifacts_are_verified_before_announcement() {
        runPythonSuite(".github/scripts/tests/test_release_artifacts.py")
    }

    @Test
    fun the_conformance_job_verifies_the_published_documentation() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val conformance = workflow.substringAfter("  conformance:\n").substringBefore("  api24_instrumentation:\n")

        assertTrue(
            "The conformance job must verify the published documentation.",
            conformance.contains("run: python3 .github/scripts/verify-public-documentation.py"),
        )
        assertTrue("The published user guide must exist.", repositoryRoot.resolve("docs/user-guide.md").isFile)
        assertTrue(
            "The published release artifact document must exist.",
            repositoryRoot.resolve("docs/release-artifacts.md").isFile,
        )
        assertTrue(
            "The pre-announcement artifact check must exist.",
            repositoryRoot.resolve(".github/scripts/verify-release-artifacts.py").isFile,
        )
    }

    @Test
    fun gradle_wrapper_declares_official_distribution_checksum() {
        val checksum =
            repositoryRoot.resolve("gradle/wrapper/gradle-wrapper.properties").readLines()
                .firstOrNull { it.startsWith("distributionSha256Sum=") }
                ?.substringAfter('=')

        assertEquals(
            "6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854",
            checksum,
        )
    }

    @Test
    fun emulator_job_enables_kvm_before_runner() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val kvmSetup =
            listOf(
                "      - name: Enable KVM group perms",
                "        run: |",
                "          echo 'KERNEL==\"kvm\", GROUP=\"kvm\", MODE=\"0666\", OPTIONS+=\"static_node=kvm\"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules",
                "          sudo udevadm control --reload-rules",
                "          sudo udevadm trigger --name-match=kvm",
            ).joinToString("\n")

        assertTrue("The workflow must use GitHub-hosted Ubuntu for the emulator job.", workflow.contains("runs-on: ubuntu-latest"))
        assertTrue("The emulator job must use the documented KVM setup.", workflow.contains(kvmSetup))
        assertTrue(
            "KVM must be enabled before the emulator action.",
            workflow.indexOf("Enable KVM group perms") <
                workflow.indexOf("reactivecircus/android-emulator-runner@v2"),
        )
    }

    @Test
    fun fixture_lifecycle_is_a_deterministic_required_check() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()

        assertTrue("The workflow must define a fixture lifecycle job.", workflow.contains("  fixture_lifecycle:"))
        assertTrue("The fixture lifecycle job must be named explicitly.", workflow.contains("    name: fixture-lifecycle"))
        assertTrue(
            "The required job must run the local fixture lifecycle and journey scenario tests.",
            workflow.contains("      - run: ./gradlew fixtureLifecycleTests journeyScenarioTests --no-daemon"),
        )
        assertTrue("The aggregate declaration must include fixture_lifecycle.", requiredChecks.contains("fixture_lifecycle"))
    }

    @Test
    fun fixture_lifecycle_requires_fresh_runner_test_execution() {
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()

        assertTrue(
            "The lifecycle task must reject cached or up-to-date runner tests.",
            buildScript.contains("lifecycleTests.state.didWork"),
        )
        assertTrue(
            "The lifecycle runner test task must execute on every invocation.",
            buildScript.contains("outputs.upToDateWhen { false }"),
        )
    }

    @Test
    fun fixture_lifecycle_setup_actions_use_immutable_references() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val lifecycleJob = workflow.substringAfter("  fixture_lifecycle:").substringBefore("  android_build:")

        assertTrue(
            "The lifecycle job must pin setup-java to the repository-approved commit.",
            lifecycleJob.contains("actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3"),
        )
        assertTrue(
            "The lifecycle job must pin setup-gradle to the repository-approved commit.",
            lifecycleJob.contains("gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a"),
        )
    }

    @Test
    fun android_test_failures_preserve_sanitized_failure_evidence() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val evidenceScript = repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").readText()
        val emulatorJob = workflow.substringAfter("  api24_instrumentation:").substringBefore("  quality-gate:")
        val failureStepMarker = "      - name: Fail when Android tests fail"
        assertTrue("The workflow must keep the Android failure step.", emulatorJob.contains(failureStepMarker))
        val failureStep =
            emulatorJob
                .substringAfter(failureStepMarker)
                .substringBefore("\n      - name:")

        assertTrue(
            "The emulator job must survive workflow cancellation long enough to finalize evidence.",
            emulatorJob.contains("      - name: Finalize Android test evidence") &&
                emulatorJob.contains("        if: \${{ always() }}"),
        )
        assertTrue(
            "The API 24 job must combine always() with its label- and UI-path-gated event filter for cancellation finalization.",
            emulatorJob.contains(
                "    if: \${{ always() && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') && (github.event_name == 'schedule' || github.event_name == 'workflow_dispatch' || (github.event_name == 'pull_request' && !github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && (contains(github.event.pull_request.labels.*.name, 'run-maestro') || needs.ui_paths.outputs.ui_paths_changed == 'true'))) }}",
            ),
        )
        assertTrue(
            "Cancellation must be evaluated in a permitted step condition before finalization.",
            emulatorJob.contains("        id: workflow_cancellation\n        if: \${{ cancelled() }}") &&
                emulatorJob.indexOf("        id: workflow_cancellation") < emulatorJob.indexOf("        id: finalize_android_evidence"),
        )
        assertEquals(
            "Finalization and reporting must each receive the workflow cancellation state.",
            2,
            emulatorJob.lines().count {
                it.trim() == "WORKFLOW_CANCELLED: \${{ steps.workflow_cancellation.outcome == 'success' || job.status == 'cancelled' }}"
            },
        )
        assertTrue("The emulator job must keep a bounded job timeout with finalization headroom.", emulatorJob.contains("    timeout-minutes: 20"))
        assertTrue("The workflow must preserve successful test classification.", emulatorJob.contains("category=success"))
        assertTrue("The Android test action must have a stable step id.", emulatorJob.contains("        id: android_tests"))
        assertTrue("The Android test action must have a bounded step timeout.", emulatorJob.contains("        timeout-minutes: 14"))
        assertTrue("The wrapper must have an explicit bounded deadline.", emulatorJob.contains("          ANDROID_TEST_TIMEOUT_SECONDS: '480'"))
        assertTrue("The Android test action must run the evidence wrapper.", emulatorJob.contains("            .github/scripts/android-test-evidence.sh"))
        assertTrue("The workflow must upload only sanitized evidence.", emulatorJob.contains("steps.finalize_android_evidence.outputs.redaction_ready == 'true'"))
        assertTrue("Failure evidence must have an explicit retention period.", emulatorJob.contains("          retention-days: 14"))
        assertTrue("Missing evidence must fail the upload step.", emulatorJob.contains("          if-no-files-found: error"))
        assertTrue("The failure step must run when Android tests or finalization fail.", failureStep.contains("steps.android_tests.outcome != 'success'"))
        assertTrue("The failure step must exit non-zero.", failureStep.contains("exit 1"))

        assertTrue("The wrapper must capture logcat.", evidenceScript.contains("logcat -d"))
        assertTrue("The wrapper must record incomplete diagnostic capture.", evidenceScript.contains("capture_incomplete"))
        assertTrue("The wrapper must preserve instrumentation output.", evidenceScript.contains("androidTest-results"))
        assertTrue("The wrapper must preserve test runner output.", evidenceScript.contains("runner-output.log"))
        assertTrue(
            "The Android wrapper must run instrumentation without duplicate JVM tasks.",
            evidenceScript.contains("./gradlew :app:verifyConnectedAndroidTests") &&
                !evidenceScript.contains("testDebugUnitTest") &&
                !evidenceScript.contains("verifyRequiredUnitTests"),
        )
        assertTrue("The wrapper must identify timeouts.", evidenceScript.contains("timeout"))
        assertTrue("The wrapper must identify cancellations.", evidenceScript.contains("cancellation"))
        assertTrue("The wrapper must identify test failures.", evidenceScript.contains("test_failure"))
        assertTrue("The wrapper must identify installation failures.", evidenceScript.contains("installation_failure"))
        assertTrue("The wrapper must identify emulator failures.", evidenceScript.contains("emulator_failure"))
        val redactionScript = repositoryRoot.resolve(".github/scripts/redact-test-reports.py").readText()
        assertTrue("The wrapper must use the shared redactor.", evidenceScript.contains("redact-test-reports.py"))
        assertTrue("The wrapper must redact sensitive values.", redactionScript.contains("<redacted>"))
        assertTrue("The wrapper must redact quoted JSON sensitive values.", redactionScript.contains("sensitive_key"))
        assertTrue("The wrapper must redact bare authentication schemes.", redactionScript.contains("bearer|basic"))
        assertTrue("The wrapper must not stream raw output to the Actions log.", !evidenceScript.contains("| tee -a"))
        assertTrue("The wrapper must bound cleanup commands.", evidenceScript.contains("run_cleanup_command"))
        assertTrue("The wrapper must bound total cleanup time.", evidenceScript.contains("cleanup_budget_seconds"))
        assertTrue("The wrapper must ignore cancellation signals during cleanup.", evidenceScript.contains("trap '' TERM INT"))
        assertTrue("The shared redactor must detect NUL-containing files as unsanitizable.", redactionScript.contains("\\x00"))
        assertTrue("The shared redactor must fail closed when a file cannot be read.", redactionScript.contains("raise SystemExit(1)"))
        assertTrue("The wrapper must delete unsanitizable files when possible.", evidenceScript.contains("rm -f --"))
        assertTrue("The wrapper must preserve a fail-closed sanitization marker.", evidenceScript.contains("redaction_pending_marker"))
        assertTrue(
            "The wrapper must redact Authorization values independent of authentication scheme.",
            redactionScript.contains("authorization\\s*[:=]"),
        )
    }

    @Test
    fun evidence_wrapper_redacts_quoted_json_credentials() {
        assertPosixWrapperSupport()
        val tempDir = Files.createTempDirectory("android-test-evidence-test")
        try {
            val evidenceDir = tempDir.resolve("artifacts/android-test-evidence")
            Files.createDirectories(evidenceDir)
            val pendingMarker = tempDir.resolve("artifacts/android-test-evidence-redaction-pending")
            Files.writeString(pendingMarker, "pending")
            Files.writeString(evidenceDir.resolve("android-test-start-epoch.txt"), "0")

            val fakeGradle = tempDir.resolve("gradlew")
            Files.writeString(
                fakeGradle,
                """
                    #!/usr/bin/env bash
                    printf '%s\n' 'Bearer bearer-secret Basic basic-secret'
                    printf '%s' '{"token":"escaped-prefix\"escaped-secret","authToken":"auth-secret","access_token":"access-secret"}'
                    exit 17
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeGradle, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakeAdb = tempDir.resolve("adb")
            Files.writeString(
                fakeAdb,
                """
                    #!/usr/bin/env bash
                    exit 0
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeAdb, PosixFilePermissions.fromString("rwxr-xr-x"))

            val process =
                ProcessBuilder("bash", repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").absolutePath)
                    .directory(tempDir.toFile())
            process.environment()["GITHUB_WORKSPACE"] = tempDir.toString()
            process.environment()["ANDROID_TEST_TIMEOUT_SECONDS"] = "30"
            process.environment()["PATH"] =
                "${tempDir}${File.pathSeparator}${System.getenv("PATH") ?: ""}"
            process.redirectErrorStream(true)
            val startedProcess = process.start()
            val wrapperOutput = startedProcess.inputStream.bufferedReader().use { it.readText() }
            val exitCode = startedProcess.waitFor()

            assertEquals(17, exitCode)
            val sanitizedOutput = Files.readString(evidenceDir.resolve("runner-output.log"))
            listOf("escaped-secret", "auth-secret", "access-secret", "bearer-secret", "basic-secret").forEach { secret ->
                assertTrue("The sanitized output must not contain $secret.", !sanitizedOutput.contains(secret))
                assertTrue("The Actions log must not contain $secret.", !wrapperOutput.contains(secret))
            }
            assertTrue("Bare Bearer credentials must be redacted.", sanitizedOutput.contains("Bearer <redacted>"))
            assertTrue("Bare Basic credentials must be redacted.", sanitizedOutput.contains("Basic <redacted>"))
            listOf("token", "authToken", "access_token").forEach { key ->
                assertTrue(
                    "The sanitized output must redact the $key value.",
                    sanitizedOutput.contains("\"$key\":\"<redacted>\""),
                )
            }
            assertTrue("The sanitization marker must be removed after success.", !Files.exists(pendingMarker))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun evidence_wrapper_preserves_capture_failures_as_sanitized_evidence() {
        assertPosixWrapperSupport()
        val tempDir = Files.createTempDirectory("android-test-evidence-capture-failure-test")
        try {
            val evidenceDir = tempDir.resolve("artifacts/android-test-evidence")
            Files.createDirectories(evidenceDir)
            val pendingMarker = tempDir.resolve("artifacts/android-test-evidence-redaction-pending")
            Files.writeString(pendingMarker, "pending")
            Files.writeString(evidenceDir.resolve("android-test-start-epoch.txt"), "0")

            val report =
                tempDir.resolve(
                    "feature/entry/presentation/build/outputs/androidTest-results/connected/debug/result.xml",
                )
            Files.createDirectories(report.parent)
            Files.writeString(report, "<testsuite />")

            val fakeGradle = tempDir.resolve("gradlew")
            Files.writeString(
                fakeGradle,
                """
                    #!/usr/bin/env bash
                    printf '%s\\n' 'Bearer test-secret'
                    exit 17
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeGradle, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakeAdb = tempDir.resolve("adb")
            Files.writeString(
                fakeAdb,
                """
                    #!/usr/bin/env bash
                    printf '%s\\n' 'adb-capture-failed'
                    exit 17
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeAdb, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakeCp = tempDir.resolve("cp")
            Files.writeString(
                fakeCp,
                """
                    #!/usr/bin/env bash
                    exit 17
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeCp, PosixFilePermissions.fromString("rwxr-xr-x"))

            val process =
                ProcessBuilder("bash", repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").absolutePath)
                    .directory(tempDir.toFile())
            process.environment()["GITHUB_WORKSPACE"] = tempDir.toString()
            process.environment()["ANDROID_TEST_TIMEOUT_SECONDS"] = "30"
            process.environment()["PATH"] =
                "${tempDir}${File.pathSeparator}${System.getenv("PATH") ?: ""}"
            process.redirectErrorStream(true)
            val startedProcess = process.start()
            startedProcess.inputStream.bufferedReader().use { it.readText() }
            val exitCode = startedProcess.waitFor()

            assertEquals(17, exitCode)
            assertTrue("Capture failures must not keep the redaction marker.", !Files.exists(pendingMarker))
            assertTrue(
                "The wrapper must record incomplete diagnostic capture.",
                Files.readString(evidenceDir.resolve("wrapper-started.txt")).contains("capture_incomplete=1"),
            )
            assertTrue("Failed logcat capture must remain as evidence.", Files.exists(evidenceDir.resolve("logcat.log")))
            assertTrue(
                "Failed report copying must remain represented.",
                Files.exists(evidenceDir.resolve("instrumentation-output/NOT_AVAILABLE.txt")),
            )
            assertTrue(
                "The runner output must be sanitized before upload.",
                !Files.readString(evidenceDir.resolve("runner-output.log")).contains("test-secret"),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun evidence_wrapper_redaction_timeout_keeps_marker_and_unsanitized_file() {
        assertPosixWrapperSupport()
        val tempDir = Files.createTempDirectory("android-test-evidence-redaction-timeout-test")
        try {
            val evidenceDir = tempDir.resolve("artifacts/android-test-evidence")
            Files.createDirectories(evidenceDir)
            val pendingMarker = tempDir.resolve("artifacts/android-test-evidence-redaction-pending")
            Files.writeString(pendingMarker, "pending")
            Files.writeString(evidenceDir.resolve("android-test-start-epoch.txt"), "0")

            val fakeGradle = tempDir.resolve("gradlew")
            Files.writeString(
                fakeGradle,
                """
                    #!/usr/bin/env bash
                    printf '%s\\n' 'Bearer timeout-secret'
                    exit 0
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeGradle, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakeAdb = tempDir.resolve("adb")
            Files.writeString(
                fakeAdb,
                """
                    #!/usr/bin/env bash
                    exit 0
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeAdb, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakePython = tempDir.resolve("python3")
            Files.writeString(
                fakePython,
                """
                    #!/usr/bin/env bash
                    if [[ "${'$'}{3:-}" == *"/runner-output.log" ]]; then
                        sleep 90
                    fi
                    exit 1
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakePython, PosixFilePermissions.fromString("rwxr-xr-x"))

            val process =
                ProcessBuilder("bash", repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").absolutePath)
                    .directory(tempDir.toFile())
            process.environment()["GITHUB_WORKSPACE"] = tempDir.toString()
            process.environment()["ANDROID_TEST_TIMEOUT_SECONDS"] = "30"
            process.environment()["PATH"] =
                "${tempDir}${File.pathSeparator}${System.getenv("PATH") ?: ""}"
            process.redirectErrorStream(true)
            val startedProcess = process.start()
            startedProcess.inputStream.bufferedReader().use { it.readText() }
            val exitCode = startedProcess.waitFor()

            assertEquals(0, exitCode)
            assertTrue("A redaction timeout must keep the pending marker.", Files.exists(pendingMarker))
            val runnerOutput = evidenceDir.resolve("runner-output.log")
            assertTrue("A redaction timeout must keep the affected evidence file.", Files.exists(runnerOutput))
            assertTrue(
                "The affected evidence must not be uploaded as sanitized content.",
                Files.readString(runnerOutput).contains("timeout-secret"),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun evidence_wrapper_timeout_stops_descendant_before_marker_removal() {
        assertPosixWrapperSupport()
        val tempDir = Files.createTempDirectory("android-test-evidence-timeout-test")
        try {
            val evidenceDir = tempDir.resolve("artifacts/android-test-evidence")
            Files.createDirectories(evidenceDir)
            val pendingMarker = tempDir.resolve("artifacts/android-test-evidence-redaction-pending")
            Files.writeString(pendingMarker, "pending")
            Files.writeString(evidenceDir.resolve("android-test-start-epoch.txt"), "0")

            val fakeGradle = tempDir.resolve("gradlew")
            Files.writeString(
                fakeGradle,
                """
                    #!/usr/bin/env bash
                    child_output="${'$'}GITHUB_WORKSPACE/artifacts/android-test-evidence/child-output.log"
                    printf '%s\\n' 'child-started' > "${'$'}child_output"
                    (
                        while :; do
                            printf '%s\\n' 'child-write' >> "${'$'}child_output"
                            sleep 0.05
                        done
                    ) &
                    while :; do
                        sleep 1
                    done
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeGradle, PosixFilePermissions.fromString("rwxr-xr-x"))

            val fakeAdb = tempDir.resolve("adb")
            Files.writeString(
                fakeAdb,
                """
                    #!/usr/bin/env bash
                    exit 0
                """.trimIndent(),
            )
            Files.setPosixFilePermissions(fakeAdb, PosixFilePermissions.fromString("rwxr-xr-x"))

            val process =
                ProcessBuilder("bash", repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").absolutePath)
                    .directory(tempDir.toFile())
            process.environment()["GITHUB_WORKSPACE"] = tempDir.toString()
            process.environment()["ANDROID_TEST_TIMEOUT_SECONDS"] = "3"
            process.environment()["PATH"] =
                "${tempDir}${File.pathSeparator}${System.getenv("PATH") ?: ""}"
            process.redirectErrorStream(true)
            val startedProcess = process.start()
            startedProcess.inputStream.bufferedReader().use { it.readText() }
            val exitCode = startedProcess.waitFor()

            assertEquals(124, exitCode)
            assertTrue("The timeout test must create descendant output.", Files.exists(evidenceDir.resolve("child-output.log")))
            val sizeAtExit = Files.size(evidenceDir.resolve("child-output.log"))
            Thread.sleep(300)
            assertEquals(
                "The timed-out descendant must stop before the marker is removed.",
                sizeAtExit,
                Files.size(evidenceDir.resolve("child-output.log")),
            )
            assertTrue("The redaction marker must be removed after safe timeout cleanup.", !Files.exists(pendingMarker))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private data class ShellExecution(
        val exitCode: Int,
        val output: String,
    )

    private data class Api24OutcomeScenario(
        val name: String,
        val workflowCancelled: Boolean,
        val androidTestOutcome: String,
        val jobStatus: String,
        val existingCategory: String?,
        val wrapperStarted: Boolean,
        val startEpoch: String,
        val expectedCategory: String,
        val expectedResult: String,
        val artifactUrl: String,
    )

    private fun extractWorkflowStepScript(workflow: String, stepName: String): String {
        val step = workflow
            .substringAfter("      - name: $stepName\n")
            .substringBefore("\n      - name:")
        return step
            .substringAfter("        run: |\n")
            .lineSequence()
            .map { line -> line.removePrefix("          ") }
            .joinToString("\n")
    }

    private fun runWorkflowShell(
        script: String,
        workingDirectory: File,
        environment: Map<String, String>,
    ): ShellExecution {
        val process =
            ProcessBuilder("bash", "-c", script)
                .directory(workingDirectory)
                .apply {
                    environment().putAll(environment)
                    redirectErrorStream(true)
                }
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return ShellExecution(process.waitFor(), output)
    }

    @Test
    fun api24_finalizer_and_report_classify_cancellation_and_outcomes_from_real_shells() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val finalizerScript = extractWorkflowStepScript(workflow, "Finalize Android test evidence")
        val reportScript = extractWorkflowStepScript(workflow, "Report API 24 instrumentation result")
        val scenarios =
            listOf(
                Api24OutcomeScenario(
                    name = "cancellation before emulator",
                    workflowCancelled = true,
                    androidTestOutcome = "skipped",
                    jobStatus = "success",
                    existingCategory = null,
                    wrapperStarted = false,
                    startEpoch = "0",
                    expectedCategory = "cancellation",
                    expectedResult = "cancellation",
                    artifactUrl = "https://github.com/Gvetri/hermes-native-client/actions/runs/47/artifacts/1",
                ),
                Api24OutcomeScenario(
                    name = "cancellation during emulator",
                    workflowCancelled = true,
                    androidTestOutcome = "failure",
                    jobStatus = "failure",
                    existingCategory = "timeout",
                    wrapperStarted = true,
                    startEpoch = "0",
                    expectedCategory = "cancellation",
                    expectedResult = "cancellation",
                    artifactUrl = "https://github.com/Gvetri/hermes-native-client/actions/runs/47/artifacts/2",
                ),
                Api24OutcomeScenario(
                    name = "success",
                    workflowCancelled = false,
                    androidTestOutcome = "success",
                    jobStatus = "success",
                    existingCategory = "success",
                    wrapperStarted = true,
                    startEpoch = "0",
                    expectedCategory = "success",
                    expectedResult = "pass",
                    artifactUrl = "https://github.com/Gvetri/hermes-native-client/actions/runs/47/artifacts/3",
                ),
                Api24OutcomeScenario(
                    name = "failure",
                    workflowCancelled = false,
                    androidTestOutcome = "failure",
                    jobStatus = "failure",
                    existingCategory = "test_failure",
                    wrapperStarted = true,
                    startEpoch = "0",
                    expectedCategory = "test_failure",
                    expectedResult = "failure",
                    artifactUrl = "https://github.com/Gvetri/hermes-native-client/actions/runs/47/artifacts/4",
                ),
                Api24OutcomeScenario(
                    name = "timeout",
                    workflowCancelled = false,
                    androidTestOutcome = "failure",
                    jobStatus = "failure",
                    existingCategory = null,
                    wrapperStarted = true,
                    startEpoch = "1",
                    expectedCategory = "timeout",
                    expectedResult = "timeout",
                    artifactUrl = "https://github.com/Gvetri/hermes-native-client/actions/runs/47/artifacts/5",
                ),
            )

        scenarios.forEach { scenario ->
            val tempDir = Files.createTempDirectory("api24-shell-${scenario.name.replace(' ', '-')}")
            try {
                val evidenceDir = tempDir.resolve("artifacts/android-test-evidence")
                Files.createDirectories(evidenceDir.resolve("instrumentation-output"))
                Files.writeString(evidenceDir.resolve("android-test-start-epoch.txt"), "${scenario.startEpoch}\n")
                scenario.existingCategory?.let { category ->
                    Files.writeString(evidenceDir.resolve("failure-category.txt"), "category=$category\n")
                }
                if (scenario.wrapperStarted) {
                    Files.writeString(evidenceDir.resolve("wrapper-started.txt"), "wrapper-started\n")
                } else {
                    Files.writeString(
                        tempDir.resolve("artifacts/android-test-evidence-redaction-pending"),
                        "pending\n",
                    )
                }

                val outputFile = tempDir.resolve("github-output.txt")
                val summaryFile = tempDir.resolve("github-summary.md")
                val environment =
                    mapOf(
                        "ANDROID_TEST_OUTCOME" to scenario.androidTestOutcome,
                        "WORKFLOW_CANCELLED" to scenario.workflowCancelled.toString(),
                        "JOB_STATUS" to scenario.jobStatus,
                        "GITHUB_OUTPUT" to outputFile.toString(),
                        "GITHUB_STEP_SUMMARY" to summaryFile.toString(),
                        "GITHUB_SERVER_URL" to "https://github.com",
                        "GITHUB_REPOSITORY" to "Gvetri/hermes-native-client",
                        "GITHUB_RUN_ID" to "47",
                        "GITHUB_RUN_ATTEMPT" to "1",
                        "GITHUB_SHA" to "test-sha",
                        "GITHUB_WORKFLOW" to "quality-gate",
                        "GITHUB_JOB" to "api24_instrumentation",
                        "GITHUB_REF_NAME" to "ci/47-nightly-api24",
                        "RUNNER_OS" to "Linux",
                    )
                val finalize = runWorkflowShell(finalizerScript, tempDir.toFile(), environment)
                assertEquals("${scenario.name} finalizer output: ${finalize.output}", 0, finalize.exitCode)
                assertEquals(
                    scenario.expectedCategory,
                    Files.readString(evidenceDir.resolve("failure-category.txt")).trim().substringAfter('='),
                )
                assertTrue(
                    "${scenario.name} must mark sanitized evidence ready.",
                    Files.readString(outputFile).contains("redaction_ready=true"),
                )

                val reportEnvironment =
                    environment +
                        mapOf(
                            "FINALIZE_OUTCOME" to "success",
                            "ARTIFACT_URL" to scenario.artifactUrl,
                        )
                val report = runWorkflowShell(reportScript, tempDir.toFile(), reportEnvironment)
                assertEquals("${scenario.name} report output: ${report.output}", 0, report.exitCode)
                val summary = Files.readString(summaryFile)
                assertTrue(summary.contains("## API 24 instrumentation: ${scenario.expectedResult}"))
                assertTrue(summary.contains("- Failure category: `${scenario.expectedCategory}`"))
                assertTrue(summary.contains("[Open run artifacts](${scenario.artifactUrl})"))

                Files.delete(summaryFile)
                val unavailableReport = runWorkflowShell(
                    reportScript,
                    tempDir.toFile(),
                    reportEnvironment + mapOf("ARTIFACT_URL" to "", "FINALIZE_OUTCOME" to "failure"),
                )
                assertEquals(unavailableReport.output, 0, unavailableReport.exitCode)
                val unavailableSummary = Files.readString(summaryFile)
                assertTrue(unavailableSummary.contains("- Diagnostic artifacts: Not uploaded."))
                assertTrue(!unavailableSummary.contains("[Open run artifacts]"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun api24_instrumentation_is_nightly_manual_and_label_or_ui_path_gated_on_pull_requests() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val composeJob = workflow.substringAfter("  compose_test:").substringBefore("  api24_instrumentation:")
        val api24Job = workflow.substringAfter("  api24_instrumentation:").substringBefore("  maestro_journeys:")

        assertTrue(
            "The workflow must run for pull requests.",
            workflow.contains("  pull_request:\n    types:\n"),
        )
        assertTrue("The workflow must run for main pushes.", workflow.contains("  push:\n    branches:\n      - main\n"))
        assertTrue("The workflow must schedule the API 24 suite nightly.", workflow.contains("  schedule:\n    - cron:"))
        assertTrue("The workflow must support manual API 24 execution.", workflow.contains("  workflow_dispatch:"))
        assertTrue(
            "The API 24 suite must run on scheduled and manual validation and on a ready, in-repository pull request that carries the run-maestro label or modifies a UI path, never on main pushes.",
            !api24Job.contains("github.event_name == 'push'") &&
                api24Job.contains("github.event_name == 'schedule'") &&
                api24Job.contains("github.event_name == 'workflow_dispatch'") &&
                api24Job.contains("github.event_name == 'pull_request'") &&
                api24Job.contains("!github.event.pull_request.draft") &&
                api24Job.contains("!github.event.pull_request.head.repo.fork") &&
                api24Job.contains("contains(github.event.pull_request.labels.*.name, 'run-maestro')") &&
                api24Job.contains("needs: [ui_paths]") &&
                api24Job.contains("needs.ui_paths.outputs.ui_paths_changed == 'true'"),
        )
        assertTrue(
            "The pull-request Compose job must use the JVM replacement.",
            composeJob.contains("./gradlew :feature:entry:presentation:verifyRoborazziDebug --no-daemon"),
        )
        assertTrue(
            "The pull-request Compose job must not start an emulator.",
            !composeJob.contains("reactivecircus/android-emulator-runner"),
        )
    }

    @Test
    fun pull_request_compose_job_verifies_roborazzi_baselines() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()
        val presentationBuild = repositoryRoot.resolve("feature/entry/presentation/build.gradle.kts").readText()
        val baselineManifest = repositoryRoot.resolve("feature/entry/presentation/src/test/roborazzi-baselines.txt")
        val composeJob = workflow.substringAfter("  compose_test:").substringBefore("  api24_instrumentation:")

        assertTrue(
            "The pull-request Compose job must verify the Roborazzi baseline manifest.",
            composeJob.contains("./gradlew :feature:entry:presentation:verifyRoborazziBaselineManifest --no-daemon --console=plain"),
        )
        assertTrue(
            "The pull-request Compose job must verify Roborazzi pixels.",
            composeJob.contains("./gradlew :feature:entry:presentation:verifyRoborazziDebug --no-daemon --console=plain"),
        )
        assertTrue("The Roborazzi verification must remain an aggregate required check.", requiredChecks.contains("compose_test"))
        assertTrue("The presentation module must register the baseline manifest task.", presentationBuild.contains("tasks.register(\"verifyRoborazziBaselineManifest\")"))
        assertTrue("Manifest failures must report missing baselines.", presentationBuild.contains("Missing baselines:"))
        assertTrue("Manifest failures must report unexpected baselines.", presentationBuild.contains("Unexpected baselines:"))
        assertTrue("The selected baseline manifest must be committed.", baselineManifest.isFile)
    }

    @Test
    fun nightly_api24_job_preserves_device_suite_timeout_reporting_and_diagnostic_link() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val evidenceScript = repositoryRoot.resolve(".github/scripts/android-test-evidence.sh").readText()
        val api24Job = workflow.substringAfter("  api24_instrumentation:").substringBefore("  quality-gate:")

        assertTrue("The nightly wrapper must run the complete app instrumentation verification task.", evidenceScript.contains("./gradlew :app:verifyConnectedAndroidTests"))
        assertTrue("The nightly job must retain a bounded job timeout.", api24Job.contains("    timeout-minutes: 20"))
        assertTrue("The emulator step must retain a bounded timeout.", api24Job.contains("        timeout-minutes: 14"))
        assertTrue("The instrumentation wrapper must retain its bounded deadline.", api24Job.contains("          ANDROID_TEST_TIMEOUT_SECONDS: '480'"))
        assertTrue("The workflow must report results in the step summary.", api24Job.contains("GITHUB_STEP_SUMMARY"))
        assertTrue("The workflow must link uploaded diagnostics from the result.", api24Job.contains("steps.upload_android_evidence.outputs.artifact-url"))
        listOf("category=success", "cancellation", "timeout", "test_failure").forEach { state ->
            assertTrue("The workflow must report the $state state.", api24Job.contains(state) || evidenceScript.contains(state))
        }
    }

    @Test
    fun coverage_and_mutation_checks_are_declared_required_and_enforced() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()
        val policy =
            repositoryRoot.resolve(
                "buildSrc/src/main/kotlin/org/mercurio/client/buildlogic/QualityPolicy.kt",
            ).readText()
        val documentation = repositoryRoot.resolve("docs/quality-gates.md")
        val coverageMutationJob =
            workflow.substringAfter("  coverage_mutation:").substringBefore("  compose_test:")

        assertTrue("The workflow must define a coverage and mutation job.", workflow.contains("  coverage_mutation:"))
        assertTrue(
            "The coverage and mutation job must be named explicitly.",
            workflow.contains("    name: coverage-mutation"),
        )
        assertTrue(
            "The declared job must verify coverage and mutation thresholds.",
            coverageMutationJob.contains("./gradlew coverageVerify mutationVerify --no-daemon --console=plain"),
        )
        assertTrue(
            "The coverage and mutation job must pin setup-java to the repository-approved commit.",
            coverageMutationJob.contains("actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3"),
        )
        assertTrue(
            "The coverage and mutation job must pin setup-gradle to the repository-approved commit.",
            coverageMutationJob.contains("gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a"),
        )
        assertTrue(
            "The coverage and mutation job must run on the main line only and never on a pull request.",
            coverageMutationJob.contains(
                "    if: \${{ always() && (github.event.action != 'labeled' || " +
                    "github.event.label.name == 'run-maestro') && github.event_name != 'pull_request' }}\n",
            ),
        )
        assertTrue(
            "The aggregate declaration must include coverage_mutation.",
            requiredChecks.contains("coverage_mutation"),
        )
        assertTrue("The aggregate gate must require the coverage and mutation job.", workflow.contains("      - coverage_mutation\n"))
        assertTrue(
            "The aggregate gate must receive the coverage and mutation result.",
            workflow.contains("COVERAGE_MUTATION_RESULT: \${{ needs.coverage_mutation.result }}"),
        )
        assertTrue(
            "The aggregate gate must fail when the coverage and mutation result misses its routed state.",
            workflow.contains("if [ \"\$COVERAGE_MUTATION_RESULT\" != \"\$coverage_expected_state\" ]; then"),
        )
        assertTrue(
            "The architecture job must run the architecture rule suite and the deterministic boundary check.",
            workflow.contains("./gradlew architectureRuleTests verifyNoMocks verifyDeterministicFakes --no-daemon"),
        )
        assertTrue(
            "The workflow must run the fail-closed quality verification tests.",
            workflow.contains("--tests org.mercurio.client.buildlogic.QualityVerificationFailClosedTest"),
        )
        listOf("verifyDeterministicFakes", "coverageVerify", "mutationVerify").forEach { task ->
            assertTrue("The local gate must depend on $task.", buildScript.contains("\"$task\","))
        }
        assertTrue(
            "The local gate must require coverage verification to execute.",
            buildScript.contains("qualityGate requires coverageVerify to execute in this invocation."),
        )
        assertTrue(
            "The local gate must require mutation verification to execute.",
            buildScript.contains("qualityGate requires mutationVerify to execute in this invocation."),
        )
        assertTrue(
            "The local gate must require the mock check to execute.",
            buildScript.contains("qualityGate requires verifyNoMocks to execute in this invocation."),
        )
        assertTrue(
            "The local gate must require the boundary-double check to execute.",
            buildScript.contains("qualityGate requires verifyDeterministicFakes to execute in this invocation."),
        )
        assertTrue(
            "The verifications must refuse an invocation that excluded the report-producing tasks.",
            buildScript.contains("requireTasksInInvocation("),
        )
        listOf("val coverageScope", "val mutationScope", "val boundaryDoubles", "val allowedDoubleReferences").forEach { declaration ->
            assertTrue("The gate must declare $declaration in the shared policy.", policy.contains(declaration))
        }
        assertTrue("Documentation must identify the required checks and thresholds.", documentation.isFile)
        val declaredValues =
            Regex(
                "(minReportedClasses|minLineCoveragePercent|minBranchCoveragePercent|minMutants|" +
                    "minMutatedClasses|minMutationScorePercent|minTestStrengthPercent) = ([0-9]+)",
            ).findAll(policy).map { match -> match.groupValues[2] }.toList()
        assertEquals(
            "The policy must declare the seven thresholds of every one of its three scoped modules.",
            21,
            declaredValues.size,
        )
        val documentationText = documentation.readText()
        declaredValues.forEach { value ->
            assertTrue(
                "Documentation must record the declared threshold value $value.",
                documentationText.contains(value),
            )
        }
        QualityPolicy.coverageScope.forEach { scope ->
            scope.documentationCells.forEach { cell ->
                assertTrue(
                    "Documentation must record the declared coverage cell '$cell'.",
                    documentationText.contains(cell),
                )
            }
        }
        QualityPolicy.mutationScope.forEach { scope ->
            scope.documentationCells.forEach { cell ->
                assertTrue(
                    "Documentation must record the declared mutation cell '$cell'.",
                    documentationText.contains(cell),
                )
            }
        }
        listOf(
            "CoverageEvidenceTest",
            "MutationEvidenceTest",
            "BoundaryDoubleVerifierTest",
            "ReportEvidenceTest",
            "QualityVerificationFailClosedTest",
        ).forEach { testClass ->
            assertTrue(
                "The workflow must run the buildSrc quality self-test $testClass.",
                workflow.contains("--tests org.mercurio.client.buildlogic.$testClass"),
            )
        }
        val architectureJobHeader =
            "  architecture_check:\n" +
                "    name: architecture-check\n" +
                "    needs: [ui_paths]\n" +
                "    if: \${{ always() && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') && (github.event_name != 'pull_request' || (!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && (contains(github.event.pull_request.labels.*.name, 'run-maestro') || needs.ui_paths.outputs.docs_only_changed != 'true'))) }}\n" +
                "    runs-on: ubuntu-latest\n" +
                "    timeout-minutes: 30\n"
        assertTrue(
            "The architecture job must run for a ready in-repository pull request under a bounded timeout.",
            workflow.contains(architectureJobHeader),
        )
        listOf("coverageVerify", "mutationVerify", "verifyDeterministicFakes").forEach { entryPoint ->
            assertTrue(
                "Documentation must name the $entryPoint entry point.",
                documentationText.contains(entryPoint),
            )
        }
    }

    @Test
    fun coverage_and_mutation_wiring_is_declared_once_in_a_convention_plugin() {
        val moduleScripts =
            listOf(
                "feature/entry/domain/build.gradle.kts",
                "feature/entry/application/build.gradle.kts",
                "feature/entry/data/build.gradle.kts",
            )
        val conventionPlugin =
            repositoryRoot.resolve("buildSrc/src/main/kotlin/mercurio-quality-gate.gradle.kts").readText()
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()

        moduleScripts.forEach { moduleScript ->
            val script = repositoryRoot.resolve(moduleScript).readText()
            assertTrue(
                "$moduleScript must apply the shared quality-gate plugin.",
                script.contains("id(\"mercurio-quality-gate\")"),
            )
            listOf("kover {", "PitestTask", "mutationThreshold", "CoverageUnit", "QualityPolicy").forEach { wiring ->
                assertTrue(
                    "$moduleScript must keep no $wiring wiring of its own.",
                    !script.contains(wiring),
                )
            }
        }

        listOf(
            "QualityPolicy.coverageFor(project.path)",
            "QualityPolicy.mutationFor(project.path)",
            "CoverageUnit.LINE",
            "CoverageUnit.BRANCH",
            "mutationThreshold.set",
            "testStrengthThreshold.set",
            "timestampedReports.set(false)",
            "outputFormats.set(setOf(\"XML\", \"HTML\"))",
            "childProcessJvmArgs.set",
            "fixture.repositoryRoot",
        ).forEach { declaration ->
            assertTrue(
                "The convention plugin must declare $declaration.",
                conventionPlugin.contains(declaration),
            )
        }

        listOf("libs.plugins.kover", "libs.plugins.pitest").forEach { alias ->
            assertTrue(
                "The root build script must not declare $alias: the build logic supplies that plugin, so a " +
                    "versioned request fails with 'already on the classpath with an unknown version'.",
                !buildScript.contains(alias),
            )
        }

        listOf("kover-gradle-plugin", "gradle-pitest-plugin").forEach { alias ->
            assertTrue(
                "The version catalog must declare the $alias artifact that the build logic depends on.",
                catalog.contains(alias),
            )
        }
    }

    @Test
    fun detekt_analyzes_every_module_with_type_resolution_and_fails_directly() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()
        val settings = repositoryRoot.resolve("settings.gradle.kts").readText()
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val configuration = repositoryRoot.resolve("config/detekt/detekt.yml").readText()
        val baseline = repositoryRoot.resolve("config/detekt/baseline.xml")
        val ledger = repositoryRoot.resolve("config/detekt/baseline-ledger.txt")
        val documentation = repositoryRoot.resolve("docs/quality-gates.md").readText()
        val detektJob = workflow.substringAfter("  detekt:\n").substringBefore("  unit_tests:\n")

        assertTrue("The workflow must define a detekt job.", workflow.contains("  detekt:\n    name: detekt\n"))
        assertTrue(
            "The detekt job must run for a ready in-repository pull request and every non-pull-request run.",
            detektJob.contains(
                "    if: \${{ always() && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') && (github.event_name != 'pull_request' || " +
                    "(!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && (contains(github.event.pull_request.labels.*.name, 'run-maestro') || needs.ui_paths.outputs.docs_only_changed != 'true'))) }}",
            ),
        )
        assertTrue(
            "The detekt job must run the aggregate analysis and its verification.",
            detektJob.contains("./gradlew detekt detektVerify --no-daemon --console=plain"),
        )
        assertTrue(
            "The detekt job must not fetch a base reference: the baseline ratchet is gone.",
            !detektJob.contains("git fetch"),
        )
        assertTrue(
            "The detekt job must pin setup-java to the repository-approved commit.",
            detektJob.contains("actions/setup-java@cf277c60eb25467037889841efdb72551f06f6c3"),
        )
        assertTrue(
            "The detekt job must pin setup-gradle to the repository-approved commit.",
            detektJob.contains("gradle/actions/setup-gradle@ed408507eac070d1f99cc633dbcf757c94c7933a"),
        )
        assertTrue("The aggregate declaration must include detekt.", requiredChecks.contains("detekt"))
        assertTrue("The aggregate gate must require the detekt job.", workflow.contains("      - detekt\n"))
        assertTrue(
            "The aggregate gate must receive the detekt result.",
            workflow.contains("DETEKT_RESULT: \${{ needs.detekt.result }}"),
        )
        assertTrue(
            "The aggregate gate must fail on a non-successful detekt result.",
            workflow.contains("\"\$DETEKT_RESULT\" \\"),
        )
        listOf("detekt", "detektVerify").forEach { task ->
            assertTrue("The local gate must depend on $task.", buildScript.contains("\"$task\","))
        }
        assertTrue(
            "The local gate must require detekt verification to execute.",
            buildScript.contains("qualityGate requires detektVerify to execute in this invocation."),
        )
        assertTrue(
            "The gate must analyze through the type-resolution tasks.",
            buildScript.contains(":detektMain") && buildScript.contains(":detektTest"),
        )
        assertTrue(
            "The catalog must pin the detekt plugin.",
            catalog.contains("detekt = \"1.23.8\"") && catalog.contains("io.gitlab.arturbosch.detekt"),
        )
        Regex("include\\(\"([^\"]+)\"\\)").findAll(settings).map { match -> match.groupValues[1] }.toList().forEach { module ->
            val moduleScript = repositoryRoot.resolve("${module.removePrefix(":").replace(':', '/')}/build.gradle.kts").readText()
            assertTrue("Every Kotlin module must apply the pinned detekt plugin: $module", moduleScript.contains("libs.plugins.detekt"))
        }
        listOf(
            "build:",
            "maxIssues: 0",
            "ReturnCount:",
            "excludeGuardClauses: false",
            "ElseCaseInsteadOfExhaustiveWhen:",
            "CommentOverPrivateFunction:",
            "CommentOverPrivateProperty:",
        ).forEach { declaration ->
            assertTrue("The detekt configuration must declare $declaration.", configuration.contains(declaration))
        }
        assertTrue("The burnt-down baseline must not be part of the repository.", !baseline.exists())
        assertTrue("The baseline ledger must not be part of the repository.", !ledger.exists())
        assertTrue(
            "The build logic must not wire a baseline into the analysis.",
            !buildScript.contains("baseline = rootProject.file"),
        )
        assertTrue(
            "The verification must reject a baseline that would absorb findings.",
            buildScript.contains("found baseline files that would absorb findings"),
        )
        listOf("detekt", "detektVerify").forEach { entry ->
            assertTrue("Documentation must name the $entry entry point.", documentation.contains(entry))
        }
    }

    @Test
    fun architecture_rules_run_as_konsist_checks_in_the_architecture_job() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val catalog = repositoryRoot.resolve("gradle/libs.versions.toml").readText()
        val buildScript = repositoryRoot.resolve("build.gradle.kts").readText()
        val buildSrcScript = repositoryRoot.resolve("buildSrc/build.gradle.kts").readText()
        val ruleSuite =
            repositoryRoot.resolve(
                "buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/ArchitectureCheckTest.kt",
            ).readText()

        assertTrue(
            "The catalog must pin the Konsist version that matches the build's embedded Kotlin.",
            catalog.contains("konsist = \"0.17.3\"") && catalog.contains("com.lemonappdev:konsist"),
        )
        assertTrue(
            "The build logic must put Konsist on the test classpath of the rule suite.",
            buildSrcScript.contains("testImplementation(libs.konsist)"),
        )
        assertTrue(
            "The architecture job must run the architecture rule suite.",
            workflow.contains("./gradlew architectureRuleTests verifyNoMocks verifyDeterministicFakes --no-daemon"),
        )
        assertTrue(
            "The architecture job must not run a duplicate hand-rolled architecture task.",
            !workflow.contains("architectureCheck "),
        )
        assertTrue(
            "The local gate must require the architecture rule suite to execute.",
            buildScript.contains("qualityGate requires architectureRuleTests to execute in this invocation."),
        )
        assertTrue(
            "The local gate must not depend on a duplicate hand-rolled architecture task.",
            !buildScript.contains("\"architectureCheck\""),
        )
        assertTrue(
            "The rule suite must express the layer graph through the Konsist architecture assertion.",
            ruleSuite.contains("assertArchitecture"),
        )
        listOf("Konsist.scopeFromProject", "Konsist.scopeFromDirectories", "Konsist.scopeFromExternalDirectory").forEach { scope ->
            assertTrue("The rule suite must resolve its scopes through $scope.", ruleSuite.contains(scope))
        }
        listOf(
            "architecture/layer-boundaries",
            "architecture/domain-application-references",
            "architecture/presentation-untrusted-content",
            "architecture/app-external-file-boundary",
            "architecture/module-dependency-declarations",
        ).forEach { rule ->
            assertTrue("The rule suite must name the $rule rule.", ruleSuite.contains(rule))
        }
        listOf(
            "layer_boundaries_hold_for_every_production_source",
            "domain_and_application_references_stay_inside_the_declared_packages",
            "presentation_references_avoid_executable_and_network_content",
            "every_production_source_keeps_the_app_external_file_boundary",
            "domain_and_application_module_scripts_declare_no_forbidden_dependency",
            "rejects_a_domain_reference_to_an_outer_layer",
            "rejects_an_application_reference_to_a_concrete_data_implementation",
            "rejects_a_scope_that_resolves_to_no_kotlin_source",
            "rejects_a_module_scope_that_resolves_to_no_build_script",
            "rejects_a_fully_qualified_android_reference_in_domain",
            "rejects_an_escaped_concrete_data_reference_in_application",
            "rejects_executable_web_content_in_presentation",
            "rejects_a_file_uri_handed_to_another_app",
            "rejects_a_forbidden_dependency_declaration_in_a_module_script",
        ).forEach { test ->
            assertTrue("The rule suite must declare the $test evidence.", ruleSuite.contains(test))
        }
    }

    @Test
    fun checkout_steps_are_immutable_and_disable_persisted_credentials() {
        val lines = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readLines()
        val checkoutStepIndices = lines.indices.filter { index ->
            lines[index].trim().startsWith("- uses: actions/checkout@")
        }
        val immutableReference = Regex("[0-9a-fA-F]{40}")

        assertEquals("The workflow must keep all twenty-one checkout steps explicit.", 21, checkoutStepIndices.size)
        checkoutStepIndices.forEach { index ->
            val reference = lines[index].trim().substringAfter("actions/checkout@")
            assertTrue(
                "Checkout action at line ${index + 1} must use an immutable commit SHA.",
                reference.matches(immutableReference),
            )
            val stepEnd =
                (index + 1 until lines.size).firstOrNull { nextIndex ->
                    lines[nextIndex].startsWith("      - ")
                } ?: lines.size
            assertTrue(
                "Checkout step at line ${index + 1} does not disable credential persistence.",
                lines.subList(index, stepEnd).any { it.trim() == "persist-credentials: false" },
            )
        }
    }

    @Test
    fun maestro_journeys_is_a_deterministic_api24_gated_check() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()
        val maestroJob = workflow.substringAfter("  maestro_journeys:").substringBefore("  create_nightly_failure_issue:")

        assertTrue("The workflow must define a Maestro journey job.", workflow.contains("  maestro_journeys:"))
        assertTrue("The Maestro journey job must be named explicitly.", workflow.contains("    name: maestro-journeys"))
        assertTrue(
            "The Maestro journey job must wait for a passing API 24 suite and run only on the nightly schedule, a manual run, or a ready pull request that carries the run-maestro label; a changed UI path must never start it, and a pull-request run must skip the journey when a required lane has already failed.",
            maestroJob.contains(
                "    needs: [api24_instrumentation, ui_paths, formatting, static_analysis, detekt, " +
                    "unit_tests, fixture_descriptor, fixture_lifecycle, fixture_contract, android_build, " +
                    "coverage_mutation, compose_test]",
            ) &&
                maestroJob.contains("needs.api24_instrumentation.result == 'success'") &&
                maestroJob.contains("github.event_name == 'schedule'") &&
                maestroJob.contains("github.event_name == 'workflow_dispatch'") &&
                maestroJob.contains("github.event_name == 'pull_request'") &&
                maestroJob.contains("contains(github.event.pull_request.labels.*.name, 'run-maestro')") &&
                !maestroJob.contains("needs.ui_paths.outputs.ui_paths_changed") &&
                maestroJob.contains("(github.event_name != 'pull_request' || !contains(needs.*.result, 'failure'))"),
        )
        assertTrue(
            "The Maestro journey job must run the pinned runner script.",
            workflow.contains(".github/scripts/journey-run.sh"),
        )
        assertTrue("The Maestro journey job must pin the CLI version.", workflow.contains("MAESTRO_CLI_VERSION: '2.10.0'"))
        assertTrue(
            "The Maestro journey job must cache the pinned CLI archive.",
            workflow.contains("key: maestro-cli-\${{ env.MAESTRO_CLI_VERSION }}"),
        )
        assertTrue(
            "Journey failure evidence must be uploaded only after redaction.",
            workflow.contains("steps.journeys.outcome != 'success' && steps.redact_journey_evidence.outcome == 'success'"),
        )
        assertTrue(
            "The aggregate gate must expect the journey lane to skip on unlabeled pull requests and to pass otherwise.",
            workflow.contains("test \"\$MAESTRO_JOURNEYS_RESULT\" = skipped") &&
                workflow.contains("test \"\$MAESTRO_JOURNEYS_RESULT\" = success"),
        )
        assertTrue("The aggregate declaration must include maestro_journeys.", requiredChecks.contains("maestro_journeys"))
    }

    @Test
    fun ui_path_detection_gates_the_emulator_lanes_on_the_declared_ui_surface() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val requiredChecks = repositoryRoot.resolve(".github/quality-gate/required-checks.txt").readLines()
        val documentation = repositoryRoot.resolve("docs/quality-gates.md").readText()
        val uiPathsJob = workflow.substringAfter("  ui_paths:").substringBefore("  api24_instrumentation:")

        assertTrue("The workflow must define a UI path detection job.", workflow.contains("  ui_paths:\n    name: ui-paths\n"))
        assertTrue(
            "The UI path job must run on the same pull-request guard as the fork guard.",
            uiPathsJob.contains(
                "    if: \${{ always() && github.event_name == 'pull_request' && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') }}",
            ),
        )
        assertTrue(
            "The UI path job must read the pull request files with a read-only token.",
            uiPathsJob.contains("    permissions:\n      contents: read\n      pull-requests: read\n"),
        )
        assertTrue(
            "The UI path job must pin the paths filter action to the repository-approved commit.",
            uiPathsJob.contains("uses: dorny/paths-filter@0e4a8c6effa4802afeda77dc8d303f8176d7dfad"),
        )
        assertTrue(
            "The UI path job must expose its decision to the emulator lanes and the aggregate.",
            workflow.contains("      ui_paths_changed: \${{ steps.ui_paths.outputs.ui_paths }}\n") &&
                workflow.contains("    needs: [ui_paths]\n") &&
                workflow.contains("needs.ui_paths.outputs.ui_paths_changed == 'true'"),
        )
        assertEquals(
            "The detected UI surface must be the documented one.",
            "            ui_paths:\n" +
                "              - 'app/src/main/**'\n" +
                "              - 'feature/entry/presentation/**'\n" +
                "              - 'feature/entry/wiring/src/main/res/**'\n" +
                "              - 'fixtures/mercurio/journey/**'",
            uiPathsJob.substringAfter("          filters: |\n").substringBefore("\n      - name:"),
        )
        listOf(
            "app/src/main/**",
            "feature/entry/presentation/**",
            "feature/entry/wiring/src/main/res/**",
            "fixtures/mercurio/journey/**",
        ).forEach { path ->
            assertTrue("Documentation must declare the UI path $path.", documentation.contains(path))
        }
        assertTrue("The aggregate declaration must include ui_paths.", requiredChecks.contains("ui_paths"))
        assertTrue("The aggregate gate must require the UI path job.", workflow.contains("      - ui_paths\n"))
        assertTrue(
            "The aggregate gate must receive the UI path result and decision.",
            workflow.contains("UI_PATHS_RESULT: \${{ needs.ui_paths.result }}") &&
                workflow.contains("UI_PATHS_CHANGED: \${{ needs.ui_paths.outputs.ui_paths_changed }}"),
        )
        assertTrue(
            "The aggregate gate must route the emulator lanes on the label or the UI path decision and fail closed on an unreadable decision.",
            workflow.contains(
                "[ \"\$EVENT_DRAFT\" != true ] && [[ \"\${EVENT_LABELS:-}\" == *'\"run-maestro\"'* || \"\$ui_paths_decision\" == true ]]; then",
            ) &&
                workflow.contains("UI path detection must report true or false for this run"),
        )
    }

    @Test
    fun docs_only_detection_skips_the_heavy_lanes_for_a_documentation_head() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val documentation = repositoryRoot.resolve("docs/quality-gates.md").readText()
        val routing = repositoryRoot.resolve("docs/protected-main-workflow.md").readText()
        val uiPathsJob = workflow.substringAfter("  ui_paths:").substringBefore("  api24_instrumentation:")

        assertTrue(
            "The decision job must expose the docs-only decision to the heavy lanes and the aggregate.",
            uiPathsJob.contains("      docs_only_changed: \${{ steps.docs_only.outputs.docs_only }}\n"),
        )
        assertTrue(
            "The docs-only classification must enumerate every changed file with the pinned paths-filter revision.",
            Regex("uses: dorny/paths-filter@0e4a8c6effa4802afeda77dc8d303f8176d7dfad")
                .findAll(uiPathsJob)
                .count() == 2 &&
                uiPathsJob.contains("          list-files: json\n") &&
                uiPathsJob.contains("            all:\n              - '**'\n"),
        )
        val classifier = uiPathsJob.substringAfter("      - name: Classify the docs-only decision\n").substringBefore("      - name: ")
        assertTrue(
            "The classifier must accept only Markdown outside the control plane or files under docs/ and fail closed otherwise.",
            classifier.contains("          CHANGED_FILES: \${{ steps.changed_files.outputs.all_files }}") &&
                classifier.contains("if \"/.github/\" in \"/\" + path:") &&
                classifier.contains("path.startswith(\"docs/\")") &&
                classifier.contains("path.endswith(\".md\")") &&
                classifier.contains("bool(changed_files) and all(") &&
                classifier.contains("failing closed to the full gate"),
        )

        val heavyGate =
            "    needs: [ui_paths]\n" +
                "    if: \${{ always() && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') && (github.event_name != 'pull_request' || (!github.event.pull_request.draft && !github.event.pull_request.head.repo.fork && (contains(github.event.pull_request.labels.*.name, 'run-maestro') || needs.ui_paths.outputs.docs_only_changed != 'true'))) }}\n"
        listOf(
            "formatting",
            "static_analysis",
            "detekt",
            "unit_tests",
            "fixture_descriptor",
            "fixture_lifecycle",
            "fixture_contract",
            "android_build",
            "architecture_check",
            "compose_test",
        ).forEach { job ->
            assertTrue(
                "The $job lane must skip an unlabeled docs-only pull request and keep the run-maestro precedence.",
                workflow.contains("  $job:\n") && workflow.substringAfter("  $job:\n").substringBefore("runs-on:").contains(heavyGate),
            )
        }
        assertTrue(
            "The coverage lane must never run on a pull request, whatever the head contains.",
            workflow
                .substringAfter("  coverage_mutation:")
                .substringBefore("runs-on:")
                .contains(
                    "    if: \${{ always() && (github.event.action != 'labeled' || github.event.label.name == 'run-maestro') && github.event_name != 'pull_request' }}",
                ),
        )
        val aggregate = workflow.substringAfter("  quality-gate:\n")
        assertTrue(
            "The aggregate must read the docs-only decision and fail closed on an unreadable one.",
            aggregate.contains("DOCS_ONLY_CHANGED: \${{ needs.ui_paths.outputs.docs_only_changed }}") &&
                aggregate.contains("Docs-only detection must report true or false for this run"),
        )
        assertTrue(
            "The aggregate must expect the heavy lanes to skip exactly on an unlabeled docs-only head.",
            aggregate.contains(
                "if [ \"\$docs_only_decision\" = true ] && [[ \"\${EVENT_LABELS:-}\" != *'\"run-maestro\"'* ]]; then\n" +
                    "                expected_state=skipped",
            ),
        )
        listOf(
            "`docs/**`",
            "outside `.github/`",
            "`run-maestro` label keeps precedence",
        ).forEach { declared ->
            assertTrue("quality-gates.md must declare the docs-only rule $declared.", documentation.contains(declared))
            assertTrue("protected-main-workflow.md must declare the docs-only rule $declared.", routing.contains(declared))
        }
    }

    @Test
    fun nightly_failures_create_one_labeled_issue_with_verified_sanitized_artifact() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val issueScript = repositoryRoot.resolve(".github/scripts/create-nightly-failure-issue.sh")
        val api24Job = workflow.substringAfter("  api24_instrumentation:").substringBefore("  create_nightly_failure_issue:")
        val issueJob = workflow.substringAfter("  create_nightly_failure_issue:").substringBefore("  quality-gate:")

        assertTrue("The API 24 job must not grant issue creation permission.", !api24Job.contains("      issues: write"))
        assertTrue("Issue creation must be isolated from the emulator job.", !api24Job.contains(".github/scripts/create-nightly-failure-issue.sh"))
        assertTrue("The issue job must grant issue creation permission.", issueJob.contains("      issues: write"))
        assertTrue("Issue creation must be limited to scheduled runs.", issueJob.contains("github.event_name == 'schedule'"))
        assertTrue(
            "Issue creation must require a non-successful API 24 result.",
            issueJob.contains("needs.api24_instrumentation.result != 'success'"),
        )
        assertTrue("Issue publication must wait for every required test job.", issueJob.contains("needs: [unit_tests, compose_test, api24_instrumentation, maestro_journeys]"))
        listOf("unit_tests", "compose_test", "api24_instrumentation", "maestro_journeys").forEach { job ->
            assertTrue("Issue creation must include failures in $job.", issueJob.contains("needs.$job.result != 'success'"))
        }
        assertTrue("Issue publication must serialize reruns for one workflow run.", issueJob.contains("nightly-failure-issue-${'$'}{{ github.repository }}-${'$'}{{ github.run_id }}"))
        assertTrue("Issue publication must not cancel an earlier rerun.", issueJob.contains("cancel-in-progress: false"))
        assertTrue("Missing reports must not suppress publication.", !issueJob.contains("gh run download"))
        assertTrue(
            "The workflow must call the nightly issue creation script.",
            issueJob.contains(".github/scripts/create-nightly-failure-issue.sh"),
        )
        assertTrue("The issue creation script must exist.", issueScript.isFile)
        val script = issueScript.readText()
        assertTrue("The issue must use the ready-for-agent label.", script.contains("ready-for-agent"))
        assertTrue("The issue must carry a run marker for deduplication.", script.contains("nightly-run:"))
        assertTrue("The script must update an existing issue before verification.", script.contains("--method PATCH"))
        assertTrue("The existing issue must be reopened before verification.", script.contains("payload[\"state\"] = \"open\""))
        assertTrue("The script must read the exact issue after publication.", script.contains("gh_api_or_refuse \"${'$'}temporary_directory/verified.json\" read-back \"${'$'}issue_number\" \"${'$'}{issues_endpoint}/${'$'}{issue_number}\""))
        assertTrue("Artifact links must come from the current run metadata.", script.contains("/actions/runs/${'$'}{GITHUB_RUN_ID}/artifacts?per_page=100"))
    }

    @Test
    fun local_quality_gate_script_runs_one_module_gate_in_the_ci_order() {
        val scriptPath = repositoryRoot.resolve(".github/scripts/local-quality-gate.sh")
        assertTrue("The local quality gate entry point must exist.", scriptPath.isFile)
        assertTrue("The local quality gate entry point must be executable.", Files.isExecutable(scriptPath.toPath()))
        val script = scriptPath.readText()

        assertEquals(
            "The local quality gate entry point must be a POSIX bash script.",
            "#!/usr/bin/env bash",
            script.lineSequence().firstOrNull(),
        )
        assertTrue("The local quality gate must fail closed.", script.contains("set -euo pipefail"))
        assertTrue("The local quality gate must require a module path.", script.contains("Usage:"))
        assertTrue(
            "The local quality gate must refuse a module outside the repository.",
            script.contains("build.gradle.kts"),
        )

        listOf(
            "./gradlew \"${'$'}module:ktlintFormat\" --no-daemon",
            "./gradlew \"${'$'}module:test\" --no-daemon",
            "./gradlew \"${'$'}module:ktlintMainSourceSetCheck\" \"${'$'}module:ktlintTestSourceSetCheck\" --no-daemon",
            "./gradlew \"${'$'}module:detektMain\" \"${'$'}module:detektTest\" detektVerify --no-daemon --console=plain",
        ).forEach { invocation ->
            assertTrue("The local quality gate must run: $invocation.", script.contains(invocation))
        }
        assertTrue(
            "The local quality gate must check the test source set with ktlint.",
            script.contains("ktlintTestSourceSetCheck"),
        )
        assertEquals(
            "Every local quality gate invocation must keep the CI --no-daemon shape.",
            4,
            Regex("--no-daemon").findAll(script).count(),
        )
        val formatter = script.indexOf("ktlintFormat")
        val compileAndTest = script.indexOf("\"${'$'}module:test\"")
        val ktlintCheck = script.indexOf("ktlintMainSourceSetCheck")
        val detektAnalysis = script.indexOf("detektMain")
        assertTrue(
            "The local quality gate must format before the ktlint checks.",
            formatter != -1 && formatter < ktlintCheck,
        )
        assertTrue(
            "The local quality gate must format before it compiles and tests.",
            formatter != -1 && formatter < compileAndTest,
        )
        assertTrue(
            "The local quality gate must compile and test before the checks judge the sources.",
            compileAndTest != -1 && compileAndTest < ktlintCheck,
        )
        assertTrue(
            "The local quality gate must run detekt after any reformat.",
            ktlintCheck != -1 && ktlintCheck < detektAnalysis,
        )

        assertPosixWrapperSupport()
        val syntaxCheck =
            ProcessBuilder("bash", "-n", scriptPath.absolutePath)
                .redirectErrorStream(true)
                .start()
        val syntaxOutput = syntaxCheck.inputStream.bufferedReader().use { it.readText() }
        assertEquals(syntaxOutput, 0, syntaxCheck.waitFor())
    }

    @Test
    fun local_quality_gate_documentation_states_the_ordering_and_staleness_recipe() {
        val documentationPath = repositoryRoot.resolve("docs/agents/local-quality-gate.md")
        assertTrue("The local quality gate recipe must live in the agents-facing docs area.", documentationPath.isFile)
        val documentation = documentationPath.readText()

        assertTrue(
            "The recipe must show the one-command entry point for a module.",
            documentation.contains(".github/scripts/local-quality-gate.sh feature/entry/domain"),
        )
        assertTrue(
            "The recipe must state that the formatter runs before the ktlint checks.",
            documentation.contains("before the ktlint checks"),
        )
        assertTrue(
            "The recipe must state that detekt runs after any reformat.",
            documentation.contains("after any reformat"),
        )
        assertTrue(
            "The recipe must state the detekt line limit for main and test sources.",
            documentation.contains("120") && documentation.contains("import statements exempt"),
        )
        assertTrue(
            "The recipe must state the staleness convergence recipe.",
            documentation.contains("./gradlew koverXmlReport pitest --rerun-tasks") &&
                documentation.contains("./gradlew coverageVerify mutationVerify"),
        )
        assertTrue(
            "The recipe must link the declared thresholds.",
            documentation.contains("../quality-gates.md"),
        )
        assertTrue(
            "The quality gates document must cross-link the local quality gate recipe.",
            repositoryRoot.resolve("docs/quality-gates.md").readText().contains("agents/local-quality-gate.md"),
        )
    }

    @Test
    fun gradle_caching_is_declared_and_the_architecture_self_tests_stay_consolidated_and_fresh() {
        val workflow = repositoryRoot.resolve(".github/workflows/quality-gate.yml").readText()
        val architectureJob = workflow.substringAfter("  architecture_check:").substringBefore("  buildsrc_contracts:")
        val buildSrcContractsJob = workflow.substringAfter("  buildsrc_contracts:").substringBefore("  coverage_mutation:")
        val rootProperties = repositoryRoot.resolve("gradle.properties").readText()
        val buildSrcProperties = repositoryRoot.resolve("buildSrc/gradle.properties").readText()
        val buildSrcScript = repositoryRoot.resolve("buildSrc/build.gradle.kts").readText()

        assertTrue(
            "The root build must NOT enable the Gradle build cache: cache-restored class files " +
                "are rewritten at restore time, which would trip the fail-closed staleness contract " +
                "in ReportEvidence.kt (a report older than the newest compiled class).",
            !rootProperties.contains("org.gradle.caching=true"),
        )
        assertTrue(
            "The standalone buildSrc build must enable the Gradle build cache.",
            buildSrcProperties.contains("org.gradle.caching=true"),
        )
        assertTrue(
            "The standalone buildSrc build must enable the configuration cache; the root build cannot, " +
                "because its fail-closed verifiers read the project and task graph at execution time.",
            buildSrcProperties.contains("org.gradle.configuration-cache=true"),
        )
        assertTrue(
            "The buildSrc self-tests must execute on every CI invocation instead of restoring cached evidence.",
            buildSrcScript.contains("outputs.upToDateWhen { false }") &&
                buildSrcScript.contains("outputs.cacheIf { false }"),
        )
        assertEquals(
            "The buildsrc-contracts job must consolidate the buildSrc self-tests into one Gradle invocation.",
            1,
            Regex("./gradlew -p buildSrc test").findAll(buildSrcContractsJob).count(),
        )
        assertEquals(
            "The architecture job must not run the buildSrc self-tests.",
            0,
            Regex("./gradlew -p buildSrc test").findAll(architectureJob).count(),
        )
        assertTrue(
            "The buildsrc-contracts job must run on schedule, dispatch, main pushes, and ready in-repository pull requests that change CI or build logic.",
            buildSrcContractsJob.contains("    name: buildsrc-contracts\n") &&
                buildSrcContractsJob.contains("    needs: [ui_paths]\n") &&
                buildSrcContractsJob.contains("github.event_name == 'schedule'") &&
                buildSrcContractsJob.contains("github.event_name == 'workflow_dispatch'") &&
                buildSrcContractsJob.contains("github.event_name == 'push'") &&
                buildSrcContractsJob.contains("!github.event.pull_request.draft") &&
                buildSrcContractsJob.contains("!github.event.pull_request.head.repo.fork") &&
                buildSrcContractsJob.contains("needs.ui_paths.outputs.ci_files_changed == 'true'") &&
                buildSrcContractsJob.contains("needs.ui_paths.outputs.build_logic_changed == 'true'"),
        )
        assertTrue(
            "The aggregate gate must receive the buildsrc-contracts result.",
            workflow.contains("      - buildsrc_contracts\n") &&
                workflow.contains("BUILDSRC_CONTRACTS_RESULT: \${{ needs.buildsrc_contracts.result }}"),
        )
        assertTrue(
            "The consolidated buildSrc invocation must keep the CI --no-daemon shape.",
            architectureJob.contains("--no-daemon") && buildSrcContractsJob.contains("--no-daemon"),
        )
        listOf(
            "QualityGateConfigurationTest",
            "QualityVerificationFailClosedTest",
            "CoverageEvidenceTest",
            "MutationEvidenceTest",
            "BoundaryDoubleVerifierTest",
            "ReportEvidenceTest",
        ).forEach { testClass ->
            assertTrue(
                "The consolidated invocation must select $testClass.",
                buildSrcContractsJob.contains("--tests org.mercurio.client.buildlogic.$testClass"),
            )
        }
    }
}