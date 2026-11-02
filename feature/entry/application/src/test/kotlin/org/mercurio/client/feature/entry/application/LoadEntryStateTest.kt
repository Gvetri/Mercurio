package org.mercurio.client.feature.entry.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayConnection
import org.mercurio.client.feature.entry.domain.GatewayConnectionRepository
import org.mercurio.client.feature.entry.domain.InstallSource
import org.mercurio.client.feature.entry.domain.InstalledBuild
import org.mercurio.client.feature.entry.domain.InstalledBuildProvider

class LoadEntryStateTest {
    @Test
    fun reports_an_entry_state_when_no_gateway_connection_is_configured() {
        val repository = FakeGatewayConnectionRepository(endpoint = null)

        val state = LoadEntryState(repository, installedBuild()).execute()

        assertFalse(state.isGatewayConnectionConfigured)
        assertEquals(null, state.configuredEndpoint)
    }

    @Test
    fun loads_the_persisted_endpoint_without_a_credential() {
        val repository = FakeGatewayConnectionRepository(endpoint = "https://gateway.example/profile")

        val state = LoadEntryState(repository, installedBuild()).execute()

        assertEquals(true, state.isGatewayConnectionConfigured)
        assertEquals("https://gateway.example/profile", state.configuredEndpoint)
        assertEquals(null, state.configuredCredential)
    }

    @Test
    fun loads_an_opt_in_credential_without_changing_the_endpoint_contract() {
        val repository =
            FakeGatewayConnectionRepository(
                endpoint = "https://gateway.example/profile",
                credential = "secure-token",
            )

        val state = LoadEntryState(repository, installedBuild()).execute()

        assertEquals("https://gateway.example/profile", state.configuredEndpoint)
        assertEquals("secure-token", state.configuredCredential)
    }

    @Test
    fun reports_the_installed_version_name_the_provider_reads() {
        val provider =
            FakeInstalledBuildProvider(
                InstalledBuild(
                    versionCode = 57L,
                    versionName = "nightly-57-ab12cd",
                    installSource = InstallSource.GOOGLE_PLAY,
                ),
            )

        val state = LoadEntryState(FakeGatewayConnectionRepository(endpoint = null), provider).execute()

        assertEquals("nightly-57-ab12cd", state.clientVersion)
    }

    @Test
    fun reports_an_unknown_client_version_when_the_installed_build_carries_no_name() {
        val provider =
            FakeInstalledBuildProvider(
                InstalledBuild(
                    versionCode = 57L,
                    versionName = "",
                    installSource = InstallSource.OTHER_SOURCE,
                ),
            )

        val state = LoadEntryState(FakeGatewayConnectionRepository(endpoint = null), provider).execute()

        assertEquals(UNKNOWN_CLIENT_VERSION, state.clientVersion)
    }

    private fun installedBuild(): InstalledBuildProvider =
        FakeInstalledBuildProvider(
            InstalledBuild(
                versionCode = 57L,
                versionName = "nightly-57-ab12cd",
                installSource = InstallSource.OTHER_SOURCE,
            ),
        )

    private class FakeGatewayConnectionRepository(
        private val endpoint: String?,
        private val credential: String? = null,
    ) : GatewayConnectionRepository {
        override fun load(): GatewayConnection? = endpoint?.let { GatewayConnection(it, credential) }

        override fun save(connection: GatewayConnection) = Unit
    }

    private class FakeInstalledBuildProvider(
        private val build: InstalledBuild,
    ) : InstalledBuildProvider {
        override fun installedBuild(): InstalledBuild = build
    }
}
