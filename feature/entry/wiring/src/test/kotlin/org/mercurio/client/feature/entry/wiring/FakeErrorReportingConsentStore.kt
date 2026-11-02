package org.mercurio.client.feature.entry.wiring

internal class FakeErrorReportingConsentStore(
    private var stored: ErrorReportingConsent = ErrorReportingConsent.Undecided,
) : ErrorReportingConsentStore {
    var reads: Int = 0
        private set

    override fun read(): ErrorReportingConsent {
        reads++
        return stored
    }

    override fun write(consent: ErrorReportingConsent) {
        stored = consent
    }
}
