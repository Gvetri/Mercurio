package org.mercurio.client.fixture

fun interface GatewayProcessFactory {
    fun start(descriptor: PinnedFixtureDescriptor): GatewayProcess
}
