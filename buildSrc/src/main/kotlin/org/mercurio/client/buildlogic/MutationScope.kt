package org.mercurio.client.buildlogic

/**
 * The declared mutation scope: one entry per production module whose mutation result is enforced by
 * `mutationVerify` and by the mutation task's own thresholds.
 *
 * `minMutants` and `minMutatedClasses` keep a narrowed `targetClasses` from passing as a smaller,
 * easier run, and `minTestStrengthPercent` keeps a large block of uncovered production code from
 * hiding behind a mutation score that counts every unreached mutant as a failure.
 */
data class MutationScope(
    val modulePath: String,
    val targetPackage: String,
    val minMutants: Int,
    val minMutatedClasses: Int,
    val minMutationScorePercent: Int,
    val minTestStrengthPercent: Int,
) {
    /** The module directory, derived from the Gradle project path so the two cannot disagree. */
    val moduleDirectory: String get() = modulePath.removePrefix(":").replace(':', '/')

    val reportPath: String get() = "$moduleDirectory/build/reports/pitest/mutations.xml"

    /** The declared values as the documentation table records them, so the two cannot drift apart. */
    val documentationCells: List<String>
        get() =
            listOf(
                "| `$modulePath` | $minMutants (measured",
                "| $minMutatedClasses (measured",
                "| $minMutationScorePercent% (measured",
                "| $minTestStrengthPercent% (measured",
            )
}
