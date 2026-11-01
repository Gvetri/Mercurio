package org.mercurio.client.fixture

import java.net.URI

/** A process boundary for a local deterministic Gateway implementation. */
interface GatewayProcess {
    val endpoint: URI
    val provenanceValue: String
    val isRunning: Boolean

    fun stop()
}
