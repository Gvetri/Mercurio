package org.mercurio.client.feature.entry.domain

interface ErrorReportingConsentStore {
    /**
     * Returns the stored decision. The composition root reads it once for the reporting backend, and
     * the entry surface reads it to decide whether the question is still due; the SDK installs at
     * most once per process, so a decision stored after the backend read applies from the next
     * process start and never to the running one.
     */
    fun read(): ErrorReportingConsent

    fun write(consent: ErrorReportingConsent)
}
