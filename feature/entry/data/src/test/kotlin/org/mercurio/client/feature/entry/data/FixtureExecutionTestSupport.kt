package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.runBlocking
import org.mercurio.client.fixture.DeterministicGatewayFixture
import org.mercurio.client.fixture.FixtureTestContext

internal fun <T> DeterministicGatewayFixture.executeBlocking(block: suspend (FixtureTestContext) -> T): T =
    execute { context -> runBlocking { block(context) } }
