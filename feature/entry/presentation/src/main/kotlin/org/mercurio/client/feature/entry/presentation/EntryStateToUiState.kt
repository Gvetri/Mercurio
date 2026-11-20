package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.application.EntryState

internal fun EntryState.toUiState(): EntryUiState =
    if (isGatewayConnectionConfigured || configuredEndpoint != null) {
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_saved),
            supportingText = UiText.Resource(R.string.entry_supporting_verify_saved),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = configuredEndpoint.orEmpty(),
            bearerCredential = configuredCredential.orEmpty(),
            isGatewayConnectionConfigured = true,
            gatewayFacts = UiText.Resource(R.string.gateway_facts_saved),
            clientVersion = clientVersion,
            buildNumber = buildNumber,
            installSource = installSource,
        )
    } else {
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connect),
            supportingText = UiText.Resource(R.string.entry_supporting_connect),
            actionLabel = UiText.Resource(R.string.entry_action_add_connection),
            clientVersion = clientVersion,
            buildNumber = buildNumber,
            installSource = installSource,
        )
    }
