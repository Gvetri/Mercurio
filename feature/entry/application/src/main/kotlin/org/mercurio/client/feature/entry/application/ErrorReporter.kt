package org.mercurio.client.feature.entry.application

/** Reports one closed [ErrorReport] to the bound sink. */
interface ErrorReporter {
    fun report(report: ErrorReport)
}
