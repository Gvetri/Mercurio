package org.mercurio.client.feature.entry.presentation

import org.mercurio.client.feature.entry.domain.RunRecoveryEntry

internal data class RecoveryWorkItem(
    val entry: RunRecoveryEntry,
    val claim: RecoveryClaim,
)
