package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.flow.update
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent

internal fun EntryStateHolder.recordErrorReportingConsent(consent: ErrorReportingConsent) {
    errorReportingConsentStore?.write(consent)
    mutableUiState.update { current ->
        current.copy(
            isErrorReportingConsentRequired = false,
            errorReportingConsent = consent,
        )
    }
}
