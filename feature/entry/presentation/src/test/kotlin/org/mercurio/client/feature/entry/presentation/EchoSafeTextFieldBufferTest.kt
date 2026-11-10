package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSafeTextFieldBufferTest {
    @Test
    fun an_echo_of_an_earlier_edit_is_ignored() {
        val buffer = EchoSafeTextFieldBuffer("")
        forward(buffer, "Recover")
        forward(buffer, "Recover this offline draft")
        buffer.adopt("Recover")
        assertEquals("Recover this offline draft", buffer.value.text)
    }

    @Test
    fun an_empty_echo_replaces_the_typed_text() {
        val buffer = EchoSafeTextFieldBuffer("")
        forward(buffer, "draft")
        buffer.adopt("")
        assertEquals("", buffer.value.text)
    }

    @Test
    fun a_value_the_field_never_forwarded_is_adopted() {
        val buffer = EchoSafeTextFieldBuffer("")
        forward(buffer, "draft")
        buffer.adopt("shared text")
        assertEquals("shared text", buffer.value.text)
        assertEquals(TextRange(11, 11), buffer.value.selection)
    }

    @Test
    fun a_restored_value_is_adopted_into_an_empty_buffer() {
        val buffer = EchoSafeTextFieldBuffer("")
        forward(buffer, "Recover")
        forward(buffer, "")
        buffer.adopt("Recover")
        assertEquals("Recover", buffer.value.text)
    }

    @Test
    fun an_empty_echo_is_adopted_after_the_field_forwarded_an_empty_edit() {
        val buffer = EchoSafeTextFieldBuffer("")
        forward(buffer, "draft")
        forward(buffer, "")
        forward(buffer, "draft again")
        buffer.adopt("")
        assertEquals("", buffer.value.text)
    }

    @Test
    fun an_echo_of_the_current_text_keeps_the_caret_where_the_user_left_it() {
        val buffer = EchoSafeTextFieldBuffer("abc")
        buffer.onUserChange(TextFieldValue("abc", selection = TextRange(1, 2)))
        buffer.adopt("abc")
        assertEquals(TextRange(1, 2), buffer.value.selection)
    }

    @Test
    fun a_selection_change_is_not_forwarded() {
        val buffer = EchoSafeTextFieldBuffer("abc")
        val changed = buffer.onUserChange(TextFieldValue("abc", selection = TextRange(1, 1)))
        assertFalse(changed)
        assertEquals(TextRange(1, 1), buffer.value.selection)
    }

    private fun forward(
        buffer: EchoSafeTextFieldBuffer,
        text: String,
    ) {
        assertTrue(buffer.onUserChange(TextFieldValue(text, selection = TextRange(text.length, text.length))))
    }
}
