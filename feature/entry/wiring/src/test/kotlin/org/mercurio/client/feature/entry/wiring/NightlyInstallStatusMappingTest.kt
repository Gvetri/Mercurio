package org.mercurio.client.feature.entry.wiring

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mercurio.client.feature.entry.domain.NightlyInstallOutcome
import org.mercurio.client.feature.entry.domain.NightlyInstallRefusal

class NightlyInstallStatusMappingTest {
    @Test
    fun a_success_status_reports_an_install() {
        assertEquals(NightlyInstallOutcome.Installed, nightlyInstallOutcomeFor(PackageInstaller.STATUS_SUCCESS))
    }

    @Test
    fun a_storage_failure_reports_the_storage_refusal() {
        assertEquals(
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.INSUFFICIENT_STORAGE),
            nightlyInstallOutcomeFor(PackageInstaller.STATUS_FAILURE_STORAGE),
        )
    }

    @Test
    fun an_aborted_status_reports_a_cancelled_install() {
        assertEquals(
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.CANCELLED),
            nightlyInstallOutcomeFor(PackageInstaller.STATUS_FAILURE_ABORTED),
        )
    }

    @Test
    fun a_blocked_status_reports_the_system_block() {
        assertEquals(
            NightlyInstallOutcome.Refused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM),
            nightlyInstallOutcomeFor(PackageInstaller.STATUS_FAILURE_BLOCKED),
        )
    }

    @Test
    fun every_other_terminal_status_reports_a_system_refusal() {
        listOf(
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
        ).forEach { status ->
            assertEquals(
                NightlyInstallOutcome.Refused(NightlyInstallRefusal.SYSTEM_REFUSED),
                nightlyInstallOutcomeFor(status),
            )
        }
    }
}
