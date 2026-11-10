package org.mercurio.client.fixture.journey

sealed class JourneyScenarioException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
