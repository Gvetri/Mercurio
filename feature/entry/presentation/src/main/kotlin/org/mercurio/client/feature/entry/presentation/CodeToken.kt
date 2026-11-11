package org.mercurio.client.feature.entry.presentation

internal data class CodeToken(
    val start: Int,
    val end: Int,
    val kind: CodeTokenKind,
)
