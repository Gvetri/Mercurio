package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.NightlyArtifact
import org.mercurio.client.feature.entry.domain.NightlyInstallOutcome
import org.mercurio.client.feature.entry.domain.NightlyInstaller

/**
 * Hands a verified artifact to the platform installer, but only after the permission probe passes: a
 * missing permission stops here, so the install stage never stages data it cannot install.
 */
class InstallNightlyUpdate(
    private val installer: NightlyInstaller,
) {
    suspend fun execute(artifact: NightlyArtifact): NightlyInstallOutcome =
        if (installer.installAllowed()) {
            installer.install(artifact)
        } else {
            NightlyInstallOutcome.PermissionRequired
        }
}
