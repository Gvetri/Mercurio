package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.domain.ErrorReportingConsentStore

class SharedPreferencesErrorReportingConsentStore(
    context: Context,
) : ErrorReportingConsentStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(): ErrorReportingConsent {
        val stored = preferences.getString(DECISION_KEY, null) ?: return ErrorReportingConsent.Undecided
        return ErrorReportingConsent.entries.firstOrNull { consent -> consent.record() == stored }
            ?: ErrorReportingConsent.Undecided
    }

    override fun write(consent: ErrorReportingConsent) {
        check(preferences.edit().putString(DECISION_KEY, consent.record()).commit()) {
            "Could not persist the error-reporting decision."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "error_reporting_consent"
        const val DECISION_KEY = "decision"
    }
}

private fun ErrorReportingConsent.record(): String =
    when (this) {
        ErrorReportingConsent.Undecided -> "undecided"
        ErrorReportingConsent.Declined -> "declined"
        ErrorReportingConsent.Granted -> "granted"
    }
