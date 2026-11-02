package org.mercurio.client.feature.entry.wiring

/** Reads and writes the one stored error-reporting decision. */
interface ErrorReportingConsentStore {
    /**
     * Returns the stored decision. The composition root reads it once, before the reporting backend
     * is initialised; the SDK installs at most once per process, so a decision stored after that read
     * applies from the next process start and never to the running one.
     */
    fun read(): ErrorReportingConsent

    /**
     * Stores [consent] so every later process reads it, or clears the record when it is
     * [ErrorReportingConsent.Undecided].
     */
    fun write(consent: ErrorReportingConsent)
}
