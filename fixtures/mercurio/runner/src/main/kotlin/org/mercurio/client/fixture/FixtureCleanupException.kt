package org.mercurio.client.fixture

class FixtureCleanupException(
    message: String,
    causes: List<Throwable>,
) : IllegalStateException(message, causes.firstOrNull()) {
    init {
        causes.drop(1).forEach(::addSuppressed)
    }
}
