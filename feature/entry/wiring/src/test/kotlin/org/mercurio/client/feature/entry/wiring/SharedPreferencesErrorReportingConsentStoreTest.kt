package org.mercurio.client.feature.entry.wiring

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesErrorReportingConsentStoreTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun a_fresh_install_holds_no_decision() {
        assertEquals(ErrorReportingConsent.Undecided, newStore().read())
    }

    @Test
    fun a_granted_decision_survives_a_restart_of_the_process() {
        newStore().write(ErrorReportingConsent.Granted)

        assertEquals(ErrorReportingConsent.Granted, newStore().read())
    }

    @Test
    fun a_declined_decision_is_stored_rather_than_treated_as_missing() {
        newStore().write(ErrorReportingConsent.Declined)

        assertEquals(ErrorReportingConsent.Declined, newStore().read())
    }

    @Test
    fun clearing_the_decision_returns_the_store_to_the_fresh_install_state() {
        val store = newStore()
        store.write(ErrorReportingConsent.Granted)
        store.write(ErrorReportingConsent.Undecided)

        assertEquals(ErrorReportingConsent.Undecided, newStore().read())
    }

    private fun newStore(): ErrorReportingConsentStore = SharedPreferencesErrorReportingConsentStore(context)
}
