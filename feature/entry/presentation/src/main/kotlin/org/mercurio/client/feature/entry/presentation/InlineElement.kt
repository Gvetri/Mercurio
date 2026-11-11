package org.mercurio.client.feature.entry.presentation

/**
 * An inline construct the parser recognised. The span keeps its literal source in
 * [MarkdownSpan.text], so a renderer stays inert until it opts into handling the element.
 */
internal sealed interface InlineElement {
    /**
     * `![alt](destination)` whose destination is an absolute HTTPS URL accepted by
     * [allowedExternalLinkDestination]. Any other destination stays literal text and never
     * becomes an element, so a relative or non-HTTPS target is never fetched.
     */
    data class Image(
        val alt: String,
        val destination: String,
    ) : InlineElement

    /** `$expression$` with the dollar delimiters dropped from [expression]. */
    data class Math(
        val expression: String,
    ) : InlineElement
}
