package org.mercurio.client.buildlogic

/**
 * The declared coverage scope: one entry per production module whose measured coverage is enforced
 * by `coverageVerify` and by Kover's own `koverVerify`.
 *
 * The thresholds are deliberate baselines, not aspirations. Each value sits just below the coverage
 * that the module already measured when the scope was declared, so the gate fails on a regression
 * instead of on an unrelated change. Raising a value is a separate, reviewable decision that also
 * updates `docs/quality-gates.md`.
 */
data class CoverageScope(
    val modulePath: String,
    val minReportedClasses: Int,
    val minLineCoveragePercent: Int,
    val minBranchCoveragePercent: Int,
) {
    /** The module directory, derived from the Gradle project path so the two cannot disagree. */
    val moduleDirectory: String get() = modulePath.removePrefix(":").replace(':', '/')

    val reportPath: String get() = "$moduleDirectory/build/reports/kover/report.xml"

    /** The declared values as the documentation table records them, so the two cannot drift apart. */
    val documentationCells: List<String>
        get() =
            listOf(
                "| `$modulePath` | $minLineCoveragePercent% (measured",
                "| $minBranchCoveragePercent% (measured",
                "| $minReportedClasses (measured",
            )
}
