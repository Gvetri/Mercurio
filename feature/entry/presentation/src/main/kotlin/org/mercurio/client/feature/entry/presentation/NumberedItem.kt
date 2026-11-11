package org.mercurio.client.feature.entry.presentation

/**
 * One ordered item with the ordinal its source authored. The renderer shows that marker as
 * written, so numbering the content never had is never invented.
 */
internal data class NumberedItem(
    val marker: String,
    val spans: List<MarkdownSpan>,
)
