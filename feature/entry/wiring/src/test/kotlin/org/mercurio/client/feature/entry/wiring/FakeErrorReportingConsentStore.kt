package org.mercurio.client.feature.entry.wiring

import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore

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
