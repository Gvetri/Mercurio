package org.mercurio.client.buildlogic

data class CoverageSummary(
    val modulePath: String,
    val lineCovered: Int,
    val lineTotal: Int,
    val branchCovered: Int,
    val branchTotal: Int,
    val classNames: Set<String>,
) {
    val lineCoveragePercent: Double get() = percentOf(lineCovered, lineTotal)

    val branchCoveragePercent: Double get() = percentOf(branchCovered, branchTotal)

    fun describe(): String =
        "$modulePath: line ${format(lineCoveragePercent)}%, branch ${format(branchCoveragePercent)}%, " +
            "${classNames.size} reported class(es)"
}
