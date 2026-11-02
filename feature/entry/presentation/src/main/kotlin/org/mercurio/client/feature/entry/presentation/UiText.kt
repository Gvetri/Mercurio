package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * User-facing copy a UI state carries without selecting a locale.
 *
 * A state holder never builds copy: it names the resource, and the composition
 * resolves it against the locale the device is using. [Value] carries data the
 * Gateway supplied, which the client never translates.
 */
sealed interface UiText {
    /** Copy the client already holds as data, such as a Session title. */
    data class Value(
        val value: String,
    ) : UiText

    /** One string resource, with optional format arguments. */
    data class Resource(
        val id: Int,
        val arguments: List<Any> = emptyList(),
    ) : UiText

    /** One plural resource, selected by [count]. */
    data class Plural(
        val id: Int,
        val count: Int,
        val arguments: List<Any> = emptyList(),
    ) : UiText

    /** Several pieces of copy joined by a design separator. */
    data class Joined(
        val parts: List<UiText>,
        val separator: String,
    ) : UiText
}

/** One string resource with its format arguments, in the order the resource names them. */
internal fun uiText(
    id: Int,
    vararg arguments: Any,
): UiText = UiText.Resource(id, arguments.toList())

/** Resolves the copy against the composition's locale. */
@Suppress("SpreadOperator")
@Composable
internal fun UiText.resolve(): String =
    when (this) {
        is UiText.Value -> value
        is UiText.Resource -> {
            val resolved = arguments.map { argument -> argument.resolveArgument() }
            stringResource(id, *resolved.toTypedArray())
        }
        is UiText.Plural -> {
            val resolved = arguments.map { argument -> argument.resolveArgument() }
            pluralStringResource(id, count, *resolved.toTypedArray())
        }
        is UiText.Joined -> {
            val resolved = parts.map { part -> part.resolve() }
            resolved.joinToString(separator = separator)
        }
    }

@Composable
private fun Any.resolveArgument(): Any = if (this is UiText) resolve() else this
