package org.mercurio.client.fixture

fun interface FixtureReadinessChecker {
    fun verify(
        process: GatewayProcess,
        descriptor: PinnedFixtureDescriptor,
    )
}
