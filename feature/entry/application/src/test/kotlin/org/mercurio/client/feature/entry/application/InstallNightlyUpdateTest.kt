package org.mercurio.client.feature.entry.application

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyInstallOutcome
import org.mercurio.client.feature.entry.domain.NightlyInstallRefusal
import org.mercurio.client.feature.entry.domain.NightlyInstaller

private val INSTALL_ARTIFACT =
    NightlyArtifact(reference = "nightly-update.apk", sizeBytes = 13_002_342L, sha256 = "1".repeat(64))

private const val PROBE_CALL = "probe"

private const val INSTALL_CALL = "install"

class InstallNightlyUpdateTest {
    @Test
    fun an_allowed_install_probes_first_and_hands_the_artifact_to_the_installer() =
        runTest {
            val installer = RecordingInstaller(allowed = true, outcome = NightlyInstallOutcome.Installed)

            val outcome = InstallNightlyUpdate(installer).execute(INSTALL_ARTIFACT)

            assertEquals(NightlyInstallOutcome.Installed, outcome)
            assertEquals(listOf(INSTALL_ARTIFACT), installer.installed)
            assertEquals(listOf(PROBE_CALL, INSTALL_CALL), installer.calls)
        }

    @Test
    fun a_missing_permission_stops_before_anything_is_staged() =
        runTest {
            val installer = RecordingInstaller(allowed = false, outcome = NightlyInstallOutcome.Installed)

            val outcome = InstallNightlyUpdate(installer).execute(INSTALL_ARTIFACT)

            assertEquals(NightlyInstallOutcome.PermissionRequired, outcome)
            assertTrue(installer.installed.isEmpty())
            assertEquals(listOf(PROBE_CALL), installer.calls)
        }

    @Test
    fun a_refusal_from_the_installer_is_reported_with_its_reason() =
        runTest {
            val installer =
                RecordingInstaller(
                    allowed = true,
                    outcome = NightlyInstallOutcome.Refused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM),
                )

            val outcome = InstallNightlyUpdate(installer).execute(INSTALL_ARTIFACT)

            assertEquals(NightlyInstallOutcome.Refused(NightlyInstallRefusal.BLOCKED_BY_SYSTEM), outcome)
        }
}

private class RecordingInstaller(
    private val allowed: Boolean,
    private val outcome: NightlyInstallOutcome,
) : NightlyInstaller {
    val calls: MutableList<String> = mutableListOf()
    val installed: MutableList<NightlyArtifact> = mutableListOf()

    override fun installAllowed(): Boolean {
        calls += PROBE_CALL
        return allowed
    }

    override suspend fun install(artifact: NightlyArtifact): NightlyInstallOutcome {
        calls += INSTALL_CALL
        installed += artifact
        return outcome
    }
}
