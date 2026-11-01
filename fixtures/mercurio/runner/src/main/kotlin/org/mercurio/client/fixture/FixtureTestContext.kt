package org.mercurio.client.fixture

import java.net.URI

data class FixtureTestContext(
    val endpoint: URI,
    val provenanceValue: String,
    val syntheticState: SyntheticTestState,
)
