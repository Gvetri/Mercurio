package org.mercurio.client.feature.entry.domain

interface ErrorReportingConsentStore {
    /**
     * Returns the stored decision. The entry surface reads it to decide whether the question is
     * still due, and the Settings row reads it to state the current answer. The reporting gate reads
     * it for every report, so a decision that revokes consent stops the next report at once; a
     * decision that grants consent takes effect at the next process start, because the reporting
     * SDK installs at most once per process.
     */
    fun read(): ErrorReportingConsent

    fun write(consent: ErrorReportingConsent)
}
