package org.mercurio.client.buildlogic

data class MutationSummary(
    val modulePath: String,
    val total: Int,
    val detected: Int,
    val statuses: Map<String, Int>,
    val mutatedClasses: Set<String>,
) {
    val noCoverage: Int get() = statuses["NO_COVERAGE"] ?: 0

    /** PIT's mutation score: the mutants the tests detected, out of every analysed mutant. */
    val mutationScorePercent: Double get() = percentOf(detected, total)

    /** PIT's test strength: the detected mutants, out of the mutants the tests reached at all. */
    val testStrengthPercent: Double get() = percentOf(detected, total - noCoverage)

    fun describe(): String =
        "$modulePath: $total mutant(s), detected $detected" +
            statuses.entries.sortedBy { it.key }.joinToString("") { (status, count) ->
                ", $status $count"
            } +
            ", mutation score ${format(mutationScorePercent)}%, test strength ${format(testStrengthPercent)}%, " +
            "${mutatedClasses.size} mutated class(es)"
}
