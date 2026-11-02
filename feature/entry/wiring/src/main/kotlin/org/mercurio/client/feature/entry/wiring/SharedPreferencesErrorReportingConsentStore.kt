package org.mercurio.client.feature.entry.wiring

import android.content.Context

/**
 * SharedPreferences-backed persistence for the error-reporting decision, in the same device-storage
 * tier as the other client preferences. An absent or unknown record reads as
 * [ErrorReportingConsent.Undecided], so a fresh install reports nothing, and a declined answer is
 * stored rather than removed.
 */
class SharedPreferencesErrorReportingConsentStore(
    context: Context,
) : ErrorReportingConsentStore {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(): ErrorReportingConsent =
        when (preferences.getString(DECISION_KEY, null)) {
            GRANTED_RECORD -> ErrorReportingConsent.Granted
            DECLINED_RECORD -> ErrorReportingConsent.Declined
            else -> ErrorReportingConsent.Undecided
        }

    override fun write(consent: ErrorReportingConsent) {
        val editor = preferences.edit()
        when (consent) {
            ErrorReportingConsent.Granted -> editor.putString(DECISION_KEY, GRANTED_RECORD)
            ErrorReportingConsent.Declined -> editor.putString(DECISION_KEY, DECLINED_RECORD)
            ErrorReportingConsent.Undecided -> editor.remove(DECISION_KEY)
        }
        check(editor.commit()) {
            "Could not persist the error-reporting decision."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "error_reporting_consent"
        const val DECISION_KEY = "decision"
        const val GRANTED_RECORD = "granted"
        const val DECLINED_RECORD = "declined"
    }
}
