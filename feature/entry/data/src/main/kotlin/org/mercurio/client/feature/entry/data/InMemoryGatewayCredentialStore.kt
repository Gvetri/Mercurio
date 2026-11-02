package org.mercurio.client.feature.entry.data

import org.mercurio.client.feature.entry.domain.GatewayCredentialStore

class InMemoryGatewayCredentialStore(
    private var credential: String? = null,
) : GatewayCredentialStore {
    override fun load(): String? = credential

    override fun save(credential: String) {
        this.credential = credential
    }

    override fun clear() {
        credential = null
    }
}
