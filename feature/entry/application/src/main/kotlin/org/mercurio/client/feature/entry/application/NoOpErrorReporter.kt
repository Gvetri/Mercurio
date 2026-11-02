package org.mercurio.client.feature.entry.application

/** Default sink: keeps the seam silent until a real backend is bound. */
object NoOpErrorReporter : ErrorReporter {
    override fun report(report: ErrorReport) = Unit
}
