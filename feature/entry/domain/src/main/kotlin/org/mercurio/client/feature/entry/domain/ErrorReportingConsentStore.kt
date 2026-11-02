package org.mercurio.client.feature.entry.domain

interface ErrorReportingConsentStore {
    /**
     * Returns the stored decision. The composition root reads it once, before the reporting backend
     * is initialised; the SDK installs at most once per process, so a decision stored after that read
     * applies from the next process start and never to the running one.
     */
    fun read(): ErrorReportingConsent

    fun write(consent: ErrorReportingConsent)
}
