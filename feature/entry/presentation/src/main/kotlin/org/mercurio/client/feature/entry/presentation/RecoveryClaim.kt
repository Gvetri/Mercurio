package org.mercurio.client.feature.entry.presentation

internal data class RecoveryClaim(
    val key: RecoveryEntryKey,
    val connectionGeneration: Long,
    val claimId: Long,
)
