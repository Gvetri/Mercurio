import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.mercurio.client.buildlogic.BoundaryDoubleVerifier
import org.mercurio.client.buildlogic.CoverageEvidenceVerifier
import org.mercurio.client.buildlogic.FixtureDescriptorValidator
import org.mercurio.client.buildlogic.MutationEvidenceVerifier
import org.mercurio.client.buildlogic.QualityPolicy
import org.mercurio.client.buildlogic.gradleWrapperCommand
import org.mercurio.client.buildlogic.requireTasksInInvocation
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
}

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure(DetektExtension::class.java) {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        }
        tasks.matching { task -> task.name == "detekt" }.configureEach { enabled = false }
    }
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }
}

val architectureRuleTestSource =
    file("buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/ArchitectureCheckTest.kt")
val architectureRuleTestClass = "org.mercurio.client.buildlogic.ArchitectureCheckTest"
val architectureRuleTestBuildDir = file("build/architecture-rule-tests/buildSrc")
val architectureRuleTestResults = architectureRuleTestBuildDir.resolve("test-results/test")
var architectureRuleTestsExecuted = false
val fixtureDescriptorFile = file("fixtures/mercurio/pinned-fixture.properties")
val fixtureDescriptorTestSource =
    file("buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/FixtureDescriptorValidatorTest.kt")
val fixtureDescriptorTestClass = "org.mercurio.client.buildlogic.FixtureDescriptorValidatorTest"
val fixtureDescriptorTestBuildDir = file("build/fixture-descriptor-tests/buildSrc")
val fixtureDescriptorTestResults = fixtureDescriptorTestBuildDir.resolve("test-results/test")

fun requireArchitectureRuleTestSource() {
    check(architectureRuleTestSource.isFile && architectureRuleTestSource.readText().isNotBlank()) {
        "Architecture rule tests found no focused architecture test source."
    }
}

fun requireFocusedTestEvidence(
    testSource: File,
    testResults: File,
    testClass: String,
    label: String,
) {
    check(testSource.isFile && testSource.readText().isNotBlank()) {
        "$label found no focused test source."
    }
    check(testResults.isDirectory) {
        "$label found no test result directory."
    }
    val reports = testResults.walkTopDown()
        .filter { it.isFile && it.extension == "xml" }
        .toList()
    check(reports.isNotEmpty()) {
        "$label found no test result reports."
    }
    check(reports.all { it.length() > 0 }) {
        "$label found an empty test result report."
    }

    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
    val focusedReports = reports.mapNotNull { report ->
        val document = documentBuilderFactory.newDocumentBuilder().parse(report)
        document.takeIf { it.documentElement.getAttribute("name") == testClass }
    }
    check(focusedReports.isNotEmpty()) {
        "$label found no focused test result report."
    }
    check(focusedReports.all { document ->
        document.getElementsByTagName("failure").length == 0 &&
            document.getElementsByTagName("error").length == 0
    }) {
        "$label found failed focused test evidence."
    }
    val executedTests = focusedReports.sumOf { document ->
        val testCases = document.getElementsByTagName("testcase")
        (0 until testCases.length).count { index ->
            val testCase = testCases.item(index) as org.w3c.dom.Element
            testCase.getElementsByTagName("skipped").length == 0
        }
    }
    check(executedTests > 0) {
        "$label found no executed test evidence."
    }
}

fun requireArchitectureRuleTestEvidence() {
    requireArchitectureRuleTestSource()
    requireFocusedTestEvidence(
        testSource = architectureRuleTestSource,
        testResults = architectureRuleTestResults,
        testClass = architectureRuleTestClass,
        label = "Architecture rule tests",
    )
}

fun requireFixtureDescriptorTestEvidence() {
    requireFocusedTestEvidence(
        testSource = fixtureDescriptorTestSource,
        testResults = fixtureDescriptorTestResults,
        testClass = fixtureDescriptorTestClass,
        label = "Fixture descriptor tests",
    )
}

tasks.register("architectureRuleTests") {
    group = "verification"
    description = "Runs the Konsist architecture rule suite and verifies its executed evidence."
    doLast {
        requireArchitectureRuleTestSource()
        architectureRuleTestBuildDir.deleteRecursively()
        val result =
            ProcessBuilder(
                *(gradleWrapperCommand(projectDir) +
                    listOf(
                        "-p",
                        "buildSrc",
                        "-Parchitecture.testBuildDir=${architectureRuleTestBuildDir.absolutePath}",
                        "test",
                        "--tests",
                        architectureRuleTestClass,
                        "--rerun-tasks",
                        "--no-build-cache",
                        "--no-daemon",
                        "--console=plain",
                    )).toTypedArray(),
            ).directory(projectDir)
                .inheritIO()
                .start()
                .waitFor()
        check(result == 0) { "Architecture rule tests failed with exit code $result." }
        requireArchitectureRuleTestEvidence()
        architectureRuleTestsExecuted = true
    }
}

var coverageVerified = false
var mutationVerified = false
var detektVerified = false
var mocksVerified = false
var boundaryDoublesVerified = false

tasks.register("verifyNoMocks") {
    group = "verification"
    description = "Fails when mock frameworks or mock-based test doubles are present."
    doLast {
        val mockFrameworks =
            listOf("mock" + "ito", "mock" + "k", "mock" + "webserver", "easy" + "mock", "mock" + "-server")
        val forbiddenSourcePatterns =
            mockFrameworks.map { framework -> Regex("(?i)$framework") } +
                Regex("(?i)\\b" + "mock" + "\\s*\\(")
        val sourceFiles = fileTree(projectDir) {
            include("**/src/main/**/*.kt")
            include("**/src/test/**/*.kt")
            include("**/src/androidTest/**/*.kt")
        }
        val violations = sourceFiles.flatMap { sourceFile ->
            sourceFile.readLines().mapIndexedNotNull { index, line ->
                if (forbiddenSourcePatterns.any { it.containsMatchIn(line) }) {
                    "${sourceFile.relativeTo(projectDir)}:${index + 1}: mock-based test code is not allowed"
                } else {
                    null
                }
            }
        }
        val buildScripts =
            fileTree(projectDir) {
                include("**/build.gradle.kts")
            }.files
        check(buildScripts.isNotEmpty()) { "Mock check found no module build scripts." }
        val dependencyViolations = buildScripts.flatMap { buildScript ->
            buildScript.readLines().mapIndexedNotNull { index, line ->
                if (mockFrameworks.any { framework -> line.contains(framework, ignoreCase = true) }) {
                    "${buildScript.relativeTo(projectDir)}:${index + 1}: mock framework dependency is not allowed"
                } else {
                    null
                }
            }
        }
        check((violations + dependencyViolations).isEmpty()) {
            (violations + dependencyViolations).joinToString("\n")
        }
        mocksVerified = true
    }
}

tasks.register("coverageVerify") {
    group = "verification"
    description =
        "Enforces the declared Kover line and branch thresholds for the declared tested production scope."
    dependsOn(
        QualityPolicy.coverageScope.flatMap { scope ->
            listOf("${scope.modulePath}:koverXmlReport", "${scope.modulePath}:koverVerify")
        },
    )
    doLast {
        requireTasksInInvocation(
            label = "Coverage verification",
            requiredTasks = QualityPolicy.coverageScope.map { scope -> "${scope.modulePath}:test" },
            invocationTasks = gradle.taskGraph.allTasks.map { task -> task.path }.toSet(),
        )
        val summaries = QualityPolicy.coverageScope.map { scope ->
            CoverageEvidenceVerifier.verify(scope, projectDir)
        }
        summaries.forEach { logger.lifecycle("Coverage verified. ${it.describe()}") }
        coverageVerified = true
    }
}

tasks.register("mutationVerify") {
    group = "verification"
    description =
        "Enforces the declared PIT mutation score and test strength for the declared mutation-testable scope."
    dependsOn(
        QualityPolicy.mutationScope.map { scope -> "${scope.modulePath}:pitest" },
    )
    dependsOn(QualityPolicy.coverageScope.map { scope -> "${scope.modulePath}:koverXmlReport" })
    doLast {
        requireTasksInInvocation(
            label = "Mutation verification",
            requiredTasks =
                QualityPolicy.coverageScope.map { scope -> "${scope.modulePath}:test" } +
                    QualityPolicy.mutationScope.map { scope -> "${scope.modulePath}:pitest" },
            invocationTasks = gradle.taskGraph.allTasks.map { task -> task.path }.toSet(),
        )
        val mutations = QualityPolicy.mutationScope.map { scope ->
            MutationEvidenceVerifier.verify(scope, projectDir)
        }
        val coverages = QualityPolicy.coverageScope.map { scope ->
            CoverageEvidenceVerifier.verify(scope, projectDir)
        }
        MutationEvidenceVerifier.requireCoveredByCoverage(mutations, coverages)
        mutations.forEach { logger.lifecycle("Mutation verified. ${it.describe()}") }
        mutationVerified = true
    }
}

val detektModules =
    listOf(
        ":app",
        ":feature:entry:domain",
        ":feature:entry:application",
        ":feature:entry:data",
        ":feature:entry:presentation",
        ":feature:entry:wiring",
        ":fixtures:mercurio:runner",
    )

val detektAnalysisTasks =
    detektModules.flatMap { module -> listOf("$module:detektMain", "$module:detektTest") }

tasks.register("detekt") {
    group = "verification"
    description = "Runs the type-resolution detekt analysis for every Kotlin module."
    dependsOn(detektAnalysisTasks)
}

tasks.register("detektVerify") {
    group = "verification"
    description = "Verifies that the detekt analysis ran fail-closed and that no baseline can absorb findings."
    dependsOn("detekt")
    doLast {
        requireTasksInInvocation(
            label = "Detekt verification",
            requiredTasks = detektAnalysisTasks,
            invocationTasks = gradle.taskGraph.allTasks.map { task -> task.path }.toSet(),
        )
        val disabled =
            gradle.taskGraph.allTasks.filter { task -> task.path in detektAnalysisTasks.toSet() && !task.enabled }
        check(disabled.isEmpty()) {
            "Detekt verification requires its analysis tasks to be enabled in this invocation: " +
                "${disabled.map { task -> task.path }}"
        }
        val baselines =
            file("config/detekt").listFiles().orEmpty()
                .filter { candidate ->
                    candidate.isFile && candidate.name.startsWith("baseline") && candidate.name.endsWith(".xml")
                }
                .map { candidate -> candidate.name }
                .sorted()
        check(baselines.isEmpty()) {
            "Detekt verification found baseline files that would absorb findings: $baselines. " +
                "A finding must be fixed, not baselined."
        }
        logger.lifecycle("Detekt verified: no baseline exists, so every reported finding fails the analysis.")
        detektVerified = true
    }
}

tasks.register("verifyDeterministicFakes") {
    group = "verification"
    description =
        "Requires a deterministic test double for every declared datasource and repository boundary."
    doLast {
        logger.lifecycle(
            BoundaryDoubleVerifier.verify(
                repositoryRoot = projectDir,
                requirements = QualityPolicy.boundaryDoubles,
                forbiddenReferences = QualityPolicy.forbiddenDoubleReferences,
                allowedReferences = QualityPolicy.allowedDoubleReferences,
                moduleRoots = QualityPolicy.scannedModuleRoots,
            ),
        )
        boundaryDoublesVerified = true
    }
}

tasks.register("formatCheck") {
    group = "verification"
    description = "Runs the repository formatter for every Kotlin module."
    dependsOn(subprojects.map { it.tasks.matching { task -> task.name == "ktlintCheck" } })
}

val requiredUnitTestTasks = listOf(
    ":feature:entry:application:test",
    ":feature:entry:data:test",
    ":feature:entry:presentation:testDebugUnitTest",
    ":feature:entry:wiring:testDebugUnitTest",
)

tasks.register("verifyRequiredUnitTests") {
    group = "verification"
    description = "Fails when a declared unit-test scope is empty, skipped, or did not produce results."
    dependsOn(requiredUnitTestTasks)
    doLast {
        val resultDirectories = listOf(
            file("feature/entry/application/build/test-results/test"),
            file("feature/entry/data/build/test-results/test"),
            file("feature/entry/presentation/build/test-results/testDebugUnitTest"),
            file("feature/entry/wiring/build/test-results/testDebugUnitTest"),
        )
        val failures = resultDirectories.flatMap { resultDirectory ->
            if (!resultDirectory.isDirectory) {
                listOf("missing test result directory: ${resultDirectory.relativeTo(projectDir)}")
            } else {
                val reports = resultDirectory.walkTopDown().filter { it.extension == "xml" }.toList()
                if (reports.isEmpty()) {
                    listOf("empty test result directory: ${resultDirectory.relativeTo(projectDir)}")
                } else {
                    var executedTests = 0
                    var skippedTests = 0
                    reports.forEach { report ->
                        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report)
                        executedTests += document.documentElement.getAttribute("tests").toIntOrNull() ?: 0
                        skippedTests += document.documentElement.getAttribute("skipped").toIntOrNull() ?: 0
                    }
                    val directory = resultDirectory.relativeTo(projectDir)
                    when {
                        executedTests == 0 -> listOf("zero executed tests: $directory")
                        skippedTests > 0 -> listOf("$skippedTests skipped test(s): $directory")
                        else -> emptyList()
                    }
                }
            }
        }
        check(failures.isEmpty()) { failures.joinToString("\n") }
    }
}

tasks.register("fixtureDescriptorTests") {
    group = "verification"
    description = "Runs focused tests for the deterministic Hermes fixture descriptor."
    doLast {
        check(fixtureDescriptorTestSource.isFile) {
            "Fixture descriptor test source is missing."
        }
        fixtureDescriptorTestBuildDir.deleteRecursively()
        val result =
            ProcessBuilder(
                *(gradleWrapperCommand(projectDir) +
                    listOf(
                        "-p",
                        "buildSrc",
                        "-PfixtureDescriptor.testBuildDir=${fixtureDescriptorTestBuildDir.absolutePath}",
                        "test",
                        "--tests",
                        fixtureDescriptorTestClass,
                        "--rerun-tasks",
                        "--no-build-cache",
                        "--no-daemon",
                        "--console=plain",
                    )).toTypedArray(),
            ).directory(projectDir)
                .inheritIO()
                .start()
                .waitFor()
        check(result == 0) { "Fixture descriptor tests failed with exit code $result." }
        requireFixtureDescriptorTestEvidence()
    }
}

gradle.projectsEvaluated {
    project(":fixtures:mercurio:runner").tasks.named("test").configure {
        outputs.upToDateWhen { false }
    }
}

tasks.register("fixtureLifecycleTests") {
    group = "verification"
    description = "Runs deterministic local Gateway fixture lifecycle tests without provider access."
    dependsOn(":fixtures:mercurio:runner:fixtureLifecycleTest")
    doLast {
        val lifecycleTests = project(":fixtures:mercurio:runner").tasks.named("fixtureLifecycleTest").get()
        check(lifecycleTests.state.didWork || lifecycleTests.state.upToDate) {
            "fixtureLifecycleTests requires the fixture lifecycle tests to execute in this invocation."
        }
    }
}

tasks.register("fixtureContractTests") {
    group = "verification"
    description = "Runs client-owned Gateway JSON and SSE contract fixture tests."
    dependsOn(":fixtures:mercurio:runner:fixtureContractTest")
    doLast {
        val contractTests = project(":fixtures:mercurio:runner").tasks.named("fixtureContractTest").get()
        check(contractTests.state.didWork || contractTests.state.upToDate) {
            "fixtureContractTests requires the contract fixture tests to execute in this invocation."
        }
    }
}

tasks.register("journeyScenarioTests") {
    group = "verification"
    description = "Runs deterministic journey scenario, Gateway, and verifier tests without a device."
    dependsOn(":fixtures:mercurio:runner:journeyScenarioTest")
    doLast {
        val scenarioTests = project(":fixtures:mercurio:runner").tasks.named("journeyScenarioTest").get()
        check(scenarioTests.state.didWork || scenarioTests.state.upToDate) {
            "journeyScenarioTests requires the journey scenario tests to execute in this invocation."
        }
    }
}

tasks.register("verifyFixtureDescriptor") {
    group = "verification"
    description = "Validates the immutable Hermes fixture provenance and lifecycle contract."
    inputs.file(fixtureDescriptorFile)
    doLast {
        FixtureDescriptorValidator.validate(fixtureDescriptorFile)
    }
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all deterministic local quality checks for the initial public project."
    dependsOn(
        "formatCheck",
        "architectureRuleTests",
        "verifyNoMocks",
        "verifyDeterministicFakes",
        "coverageVerify",
        "mutationVerify",
        "detekt",
        "detektVerify",
        "fixtureDescriptorTests",
        "fixtureLifecycleTests",
        "fixtureContractTests",
        "journeyScenarioTests",
        "verifyFixtureDescriptor",
        "verifyRequiredUnitTests",
        ":app:lintDebug",
        ":app:assembleDebug",
        ":app:assembleRelease",
    )
    doLast {
        check(coverageVerified) {
            "qualityGate requires coverageVerify to execute in this invocation."
        }
        check(mutationVerified) {
            "qualityGate requires mutationVerify to execute in this invocation."
        }
        check(detektVerified) {
            "qualityGate requires detektVerify to execute in this invocation."
        }
        check(mocksVerified) {
            "qualityGate requires verifyNoMocks to execute in this invocation."
        }
        check(boundaryDoublesVerified) {
            "qualityGate requires verifyDeterministicFakes to execute in this invocation."
        }
        check(architectureRuleTestsExecuted) {
            "qualityGate requires architectureRuleTests to execute in this invocation."
        }
        requireArchitectureRuleTestEvidence()
    }
}
