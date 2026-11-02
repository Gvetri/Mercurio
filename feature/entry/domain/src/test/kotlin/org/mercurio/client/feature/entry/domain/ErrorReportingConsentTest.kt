package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorReportingConsentTest {
    @Test
    fun only_the_granted_answer_allows_a_report() {
        assertTrue(ErrorReportingConsent.Granted.isGranted)
        assertFalse(ErrorReportingConsent.Declined.isGranted)
        assertFalse(ErrorReportingConsent.Undecided.isGranted)
    }
}
