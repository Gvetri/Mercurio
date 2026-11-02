package org.mercurio.client.buildlogic

/**
 * The single declaration of what the quality gate enforces: the covered and mutated production
 * scope with its thresholds, the boundaries that must keep deterministic test doubles, and the
 * tokens that make a double depend on its environment instead of its inputs.
 */
object QualityPolicy {
    val coverageScope = listOf(
        CoverageScope(
            modulePath = ":feature:entry:domain",
            minReportedClasses = 42,
            minLineCoveragePercent = 62,
            minBranchCoveragePercent = 66,
        ),
        CoverageScope(
            modulePath = ":feature:entry:application",
            minReportedClasses = 18,
            minLineCoveragePercent = 86,
            minBranchCoveragePercent = 74,
        ),
        CoverageScope(
            modulePath = ":feature:entry:data",
            minReportedClasses = 37,
            minLineCoveragePercent = 90,
            minBranchCoveragePercent = 61,
        ),
    )

    val mutationScope = listOf(
        MutationScope(
            modulePath = ":feature:entry:domain",
            targetPackage = "org.mercurio.client.feature.entry.domain",
            minMutants = 185,
            minMutatedClasses = 32,
            minMutationScorePercent = 46,
            minTestStrengthPercent = 82,
        ),
        MutationScope(
            modulePath = ":feature:entry:application",
            targetPackage = "org.mercurio.client.feature.entry.application",
            minMutants = 115,
            minMutatedClasses = 10,
            minMutationScorePercent = 78,
            minTestStrengthPercent = 82,
        ),
        MutationScope(
            modulePath = ":feature:entry:data",
            targetPackage = "org.mercurio.client.feature.entry.data",
            minMutants = 480,
            minMutatedClasses = 31,
            minMutationScorePercent = 70,
            minTestStrengthPercent = 82,
        ),
    )

    val boundaryDoubles = listOf(
        BoundaryDoubleRequirement("MessageImagePort", "repository"),
        BoundaryDoubleRequirement("GatewayConnectionRepository", "repository"),
        BoundaryDoubleRequirement("SessionGatewayPort", "repository"),
        BoundaryDoubleRequirement("RunGatewayPort", "repository"),
        BoundaryDoubleRequirement("RunRecoveryRegistry", "repository"),
        BoundaryDoubleRequirement("RunRecoveryStorage", "datasource"),
        BoundaryDoubleRequirement("RunSubmissionUncertaintyStore", "repository"),
        BoundaryDoubleRequirement("RunSubmissionUncertaintyStorage", "datasource"),
        BoundaryDoubleRequirement("SessionDraftStore", "repository"),
        BoundaryDoubleRequirement("InstalledBuildProvider", "datasource"),
    )

    /**
     * A boundary double that reads the wall clock or randomness stops being a function of its
     * inputs, so the verification rejects these references inside a declared double unless an
     * explicit allowance, with its reason, declares the deviation.
     */
    val forbiddenDoubleReferences = listOf(
        "Random",
        "Math.random",
        "ThreadLocalRandom",
        "System.currentTimeMillis",
        "System.nanoTime",
        "Instant.now",
        "LocalDate.now",
        "LocalTime.now",
        "LocalDateTime.now",
        "ZonedDateTime.now",
    )

    val allowedDoubleReferences = listOf(
        DoubleReferenceAllowance(
            doubleType = "SlowSessionGateway",
            reference = "System.nanoTime",
            reason = "bounds the wait for the next request; the returned page stays a function of its inputs",
        ),
    )

    /** Module roots whose main and test Kotlin sources are scanned by the source-level checks. */
    val scannedModuleRoots = listOf("app", "feature", "fixtures")

    fun coverageFor(modulePath: String): CoverageScope =
        coverageScope.firstOrNull { it.modulePath == modulePath }
            ?: error("Coverage verification has no declared scope for $modulePath.")

    fun mutationFor(modulePath: String): MutationScope =
        mutationScope.firstOrNull { it.modulePath == modulePath }
            ?: error("Mutation verification has no declared scope for $modulePath.")
}
