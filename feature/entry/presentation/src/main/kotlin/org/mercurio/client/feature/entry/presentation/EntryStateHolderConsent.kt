package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.flow.update
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent

/**
 * The question is due only while the install holds no answer. An unbound store means this surface
 * is not part of the graph, like the other optional stores; the reporting gate keeps its own
 * fail-closed read, so an unbound store can never let a report leave without an answer.
 */
internal fun EntryStateHolder.restoredErrorReportingConsentRequired(): Boolean =
    errorReportingConsentStore?.read() == ErrorReportingConsent.Undecided

internal fun EntryStateHolder.recordErrorReportingConsent(consent: ErrorReportingConsent) {
    errorReportingConsentStore?.write(consent)
    mutableUiState.update { current -> current.copy(isErrorReportingConsentRequired = false) }
}
