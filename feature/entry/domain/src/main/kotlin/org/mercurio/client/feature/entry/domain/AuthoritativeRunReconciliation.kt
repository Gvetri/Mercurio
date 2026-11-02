package org.mercurio.client.feature.entry.domain

data class AuthoritativeRunReconciliation(
    val run: Run,
    val history: SessionHistory,
    val decision: RunReconciliationDecision,
)
