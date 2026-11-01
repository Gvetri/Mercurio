package org.mercurio.client.fixture

/** Lifecycle stages exposed to deterministic integration tests. */
enum class FixtureLifecycleState {
    NEW,
    STARTED,
    READY,
    TESTING,
    TORN_DOWN,
}
