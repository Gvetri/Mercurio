package org.mercurio.client.feature.entry.presentation

/**
 * One run of literal message text. [link] is set only for a destination that
 * [allowedExternalLinkDestination] accepts; every other link target stays as its
 * inert label text. [element] marks an inline construct the parser recognised;
 * [text] still holds that construct's source verbatim, so an inert render is unchanged.
 */
internal data class MarkdownSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
    val element: InlineElement? = null,
)
