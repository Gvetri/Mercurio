package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Holds a text field's edit buffer so a lagging state echo cannot reset the text the user typed.
 *
 * The holder applies UI events on its own consumer, so the value it echoes back can arrive after
 * later keystrokes. A field bound directly to that echo replaces its input buffer with the stale
 * value, which reorders or drops characters. The buffer adopts an external value only when that
 * value cannot be an echo of an earlier edit: an empty value, a value the field never forwarded,
 * or any value while the buffer is empty. An edit that only moves the selection forwards nothing.
 */
@Stable
internal class EchoSafeTextFieldBuffer(
    initialText: String,
) {
    private val forwarded = ArrayDeque<String>()

    internal var value by mutableStateOf(TextFieldValue(initialText))
        private set

    internal fun onUserChange(change: TextFieldValue): Boolean {
        val textChanged = change.text != value.text
        if (textChanged) {
            if (forwarded.size == MAX_RETAINED_ECHOES) {
                forwarded.removeFirst()
            }
            forwarded.addLast(change.text)
        }
        value = change
        return textChanged
    }

    internal fun adopt(echo: String) {
        if (isAdoptable(echo)) {
            forwarded.clear()
            value = TextFieldValue(echo, selection = TextRange(echo.length))
        }
    }

    private fun isAdoptable(echo: String): Boolean {
        val differs = echo != value.text
        val clearsOrRestores = echo.isEmpty() || value.text.isEmpty()
        return differs && (clearsOrRestores || echo !in forwarded)
    }

    private companion object {
        const val MAX_RETAINED_ECHOES = 256
    }
}

/**
 * Remembers an [EchoSafeTextFieldBuffer] for [externalText] and adopts every external value that
 * cannot be an echo. [key] restarts the buffer when the field switches to another draft owner,
 * such as another Session.
 */
@Composable
internal fun rememberEchoSafeTextFieldBuffer(
    externalText: String,
    key: Any? = Unit,
): EchoSafeTextFieldBuffer {
    val buffer = remember(key) { EchoSafeTextFieldBuffer(externalText) }
    LaunchedEffect(key, externalText) { buffer.adopt(externalText) }
    return buffer
}
